package com.datemanager.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import com.datemanager.PruebaIntegracion;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Disparador {@code tr_cita_sin_solape} (V8): dos citas no canceladas del mismo servicio y día no pueden solaparse.
 * SQL directo (la aplicación valida antes, así que aquí se ejerce la última defensa), más una prueba de carrera con
 * dos conexiones propias que insertan a la vez citas solapadas: nunca pueden ganar las dos; y otra de dos conexiones
 * que comprueba que atender o cancelar no bloquea las demás citas del servicio (V10).
 * También {@code tr_ventana_sin_cruce} (V9): dos ventanas de disponibilidad del mismo servicio y día no pueden
 * cruzarse.
 */
class EsquemaDisparadoresTest extends PruebaIntegracion {

    private static final int ERROR_SOLAPE = 50010;
    private static final int ERROR_INTERBLOQUEO = 1205;
    private static final String FECHA = "2030-01-07";
    private static final String INSERTAR_CITA = "INSERT INTO cita (id, servicio_id, cliente_id, fecha, hora_inicio, "
            + "hora_fin, estado, creada_en, cancelada_en) VALUES (?, ?, ?, ?, ?, ?, ?, '2030-01-01 08:00:00', "
            + "CASE WHEN ? = 'Cancelada' THEN '2030-01-01 09:00:00' END)";

    @Autowired
    private DataSource fuente;

    private UUID cliente;

    private UUID crearServicio() {
        UUID servicio = UUID.randomUUID();
        jdbc.update("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, 'Corte', 60, 1, 0)",
                servicio);
        return servicio;
    }

    private UUID preparar() {
        cliente = UUID.randomUUID();
        jdbc.update("INSERT INTO cliente (id, nombre, apellido, telefono, correo) "
                + "VALUES (?, 'Ana', 'Pérez', '300', 'ana@x.co')", cliente);
        return crearServicio();
    }

    private UUID cita(UUID servicio, String fecha, String inicio, String fin, String estado) {
        UUID id = UUID.randomUUID();
        jdbc.update(INSERTAR_CITA, id, servicio, cliente, fecha, inicio, fin, estado, estado);
        return id;
    }

    private UUID cita(UUID servicio, String inicio, String fin) {
        return cita(servicio, FECHA, inicio, fin, "Confirmada");
    }

    private int citasActivas() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM cita WHERE estado <> 'Cancelada'", Integer.class);
    }

    /** Código de error de SQL Server de la primera {@link SQLException} en la cadena de causas. */
    private static Integer codigoSql(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return sql.getErrorCode();
            }
        }
        return null;
    }

    /** La sentencia la rechaza el disparador (error 50010, mensaje sin valores de la base) y nada cambia. */
    private void afirmarSolape(ThrowingCallable sql) {
        int antes = citasActivas();
        Throwable e = catchThrowable(sql);
        assertThat(e).as("se esperaba el rechazo del disparador").isNotNull();
        assertThat(codigoSql(e)).as("código de error de SQL Server").isEqualTo(ERROR_SOLAPE);
        assertThat(e).hasMessageContaining("La cita se solapa con otra del mismo servicio.");
        assertThat(citasActivas()).isEqualTo(antes);
    }

    // ---- INSERT ----

    @Test
    void unaCitaQueSeSolapaParcialmenteConUnaConfirmadaSeRechaza() {
        UUID servicio = preparar();
        cita(servicio, "10:00", "11:00");

        afirmarSolape(() -> cita(servicio, "10:30", "11:30"));
        afirmarSolape(() -> cita(servicio, "09:30", "10:30"));
    }

    @Test
    void unaCitaQueContieneAOtraOEstaDentroDeOtraSeRechaza() {
        UUID servicio = preparar();
        cita(servicio, "10:00", "11:00");

        afirmarSolape(() -> cita(servicio, "09:00", "12:00"));
        afirmarSolape(() -> cita(servicio, "10:15", "10:45"));
    }

    @Test
    void unaCitaConElMismoHorarioSeRechaza() {
        UUID servicio = preparar();
        cita(servicio, "10:00", "11:00");

        // El mismo inicio lo detiene antes el índice único filtrado uq_cita_servicio_inicio (se conserva).
        assertThatThrownBy(() -> cita(servicio, "10:00", "11:00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_cita_servicio_inicio");
        assertThat(citasActivas()).isEqualTo(1);
    }

    @Test
    void unaAtendidaTambienOcupaElHorario() {
        UUID servicio = preparar();
        cita(servicio, FECHA, "10:00", "11:00", "Atendida");

        afirmarSolape(() -> cita(servicio, "10:30", "11:30"));
    }

    @Test
    void citasPegadasExtremoConExtremoSeAceptan() {
        UUID servicio = preparar();
        cita(servicio, "10:00", "11:00");

        assertThatCode(() -> {
            cita(servicio, "09:00", "10:00");
            cita(servicio, "11:00", "12:00");
        }).doesNotThrowAnyException();
        assertThat(citasActivas()).isEqualTo(3);
    }

    @Test
    void elMismoHorarioEnOtroServicioUOtroDiaSeAcepta() {
        UUID servicio = preparar();
        UUID otro = crearServicio();
        cita(servicio, "10:00", "11:00");

        assertThatCode(() -> {
            cita(otro, "10:30", "11:30");
            cita(servicio, "2030-01-08", "10:30", "11:30", "Confirmada");
        }).doesNotThrowAnyException();
        assertThat(citasActivas()).isEqualTo(3);
    }

    @Test
    void solaparseConUnaCanceladaSeAcepta() {
        UUID servicio = preparar();
        cita(servicio, FECHA, "10:00", "11:00", "Cancelada");

        assertThatCode(() -> cita(servicio, "10:30", "11:30")).doesNotThrowAnyException();
        // Y una cancelada nueva encima de una activa tampoco ocupa nada.
        assertThatCode(() -> cita(servicio, FECHA, "10:45", "11:15", "Cancelada")).doesNotThrowAnyException();
    }

    @Test
    void unInsertDeVariasFilasQueSeSolapanEntreSiSeRechaza() {
        UUID servicio = preparar();

        afirmarSolape(() -> jdbc.update("INSERT INTO cita (id, servicio_id, cliente_id, fecha, hora_inicio, hora_fin, "
                + "estado, creada_en) VALUES (?, ?, ?, ?, '10:00', '11:00', 'Confirmada', '2030-01-01 08:00:00'), "
                + "(?, ?, ?, ?, '10:30', '11:30', 'Confirmada', '2030-01-01 08:00:00')",
                UUID.randomUUID(), servicio, cliente, FECHA, UUID.randomUUID(), servicio, cliente, FECHA));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cita", Integer.class)).isZero();
    }

    @Test
    void unInsertDeVariasFilasPegadasSeAcepta() {
        UUID servicio = preparar();

        assertThatCode(() -> jdbc.update("INSERT INTO cita (id, servicio_id, cliente_id, fecha, hora_inicio, "
                + "hora_fin, estado, creada_en) VALUES (?, ?, ?, ?, '10:00', '11:00', 'Confirmada', "
                + "'2030-01-01 08:00:00'), (?, ?, ?, ?, '11:00', '12:00', 'Confirmada', '2030-01-01 08:00:00')",
                UUID.randomUUID(), servicio, cliente, FECHA, UUID.randomUUID(), servicio, cliente, FECHA))
                .doesNotThrowAnyException();
        assertThat(citasActivas()).isEqualTo(2);
    }

    // ---- UPDATE ----

    @Test
    void moverUnaCitaEncimaDeOtraSeRechaza() {
        UUID servicio = preparar();
        cita(servicio, "10:00", "11:00");
        UUID otra = cita(servicio, "12:00", "13:00");

        afirmarSolape(() -> jdbc.update("UPDATE cita SET hora_inicio = '10:30', hora_fin = '11:30' WHERE id = ?",
                otra));
    }

    @Test
    void cambiarDeServicioODiaEncimaDeOtraSeRechaza() {
        UUID servicio = preparar();
        UUID otro = crearServicio();
        cita(servicio, "10:00", "11:00");
        UUID enOtroServicio = cita(otro, "10:30", "11:30");
        UUID otroDia = cita(servicio, "2030-01-08", "10:30", "11:30", "Confirmada");

        afirmarSolape(() -> jdbc.update("UPDATE cita SET servicio_id = ? WHERE id = ?", servicio, enOtroServicio));
        afirmarSolape(() -> jdbc.update("UPDATE cita SET fecha = ? WHERE id = ?", FECHA, otroDia));
    }

    @Test
    void reactivarUnaCanceladaEncimaDeOtraSeRechaza() {
        UUID servicio = preparar();
        cita(servicio, "10:00", "11:00");
        UUID cancelada = cita(servicio, FECHA, "10:30", "11:30", "Cancelada");

        afirmarSolape(() -> jdbc.update("UPDATE cita SET estado = 'Confirmada', cancelada_en = NULL WHERE id = ?",
                cancelada));
    }

    @Test
    void unaCitaNoSeComparaConsigoMisma() {
        UUID servicio = preparar();
        UUID id = cita(servicio, "10:00", "11:00");
        cita(servicio, "12:00", "13:00");

        assertThatCode(() -> {
            // Moverla a un horario que se cruza con el suyo anterior, atenderla y cancelarla.
            jdbc.update("UPDATE cita SET hora_inicio = '10:30', hora_fin = '11:30' WHERE id = ?", id);
            jdbc.update("UPDATE cita SET estado = 'Atendida' WHERE id = ?", id);
            jdbc.update("UPDATE cita SET estado = 'Cancelada', cancelada_en = '2030-01-01 09:00:00' WHERE id = ?",
                    id);
        }).doesNotThrowAnyException();
    }

    @Test
    void unUpdateDeVariasFilasSeVerificaConElResultadoFinal() {
        UUID servicio = preparar();
        UUID a = cita(servicio, "10:00", "11:00");
        UUID b = cita(servicio, "11:00", "12:00");

        // Intercambiar las dos en una sola sentencia deja un estado final válido.
        assertThatCode(() -> jdbc.update("UPDATE cita SET hora_inicio = CASE WHEN id = ? THEN '11:00' ELSE '10:00' "
                + "END, hora_fin = CASE WHEN id = ? THEN '12:00' ELSE '11:00' END WHERE id IN (?, ?)", a, a, a, b))
                .doesNotThrowAnyException();
        // Llevar las dos al mismo bloque de 10:00 a 12:00 con inicios distintos no.
        afirmarSolape(() -> jdbc.update("UPDATE cita SET hora_inicio = CASE WHEN id = ? THEN '10:00' ELSE '10:30' "
                + "END, hora_fin = '12:00' WHERE id IN (?, ?)", a, a, b));
    }

    // ---- Ventanas de disponibilidad (V9) ----

    private static final int ERROR_CRUCE = 50020;

    private UUID ventana(UUID servicio, int dia, String inicio, String fin) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO ventana_disponibilidad (servicio_id, id, dia, hora_inicio, hora_fin) "
                + "VALUES (?, ?, ?, ?, ?)", servicio, id, dia, inicio, fin);
        return id;
    }

    private int ventanas() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ventana_disponibilidad", Integer.class);
    }

    /** La sentencia la rechaza el disparador (error 50020, mensaje sin valores de la base) y nada cambia. */
    private void afirmarCruce(ThrowingCallable sql) {
        int antes = ventanas();
        Throwable e = catchThrowable(sql);
        assertThat(e).as("se esperaba el rechazo del disparador").isNotNull();
        assertThat(codigoSql(e)).as("código de error de SQL Server").isEqualTo(ERROR_CRUCE);
        assertThat(e).hasMessageContaining("Las ventanas de disponibilidad de un servicio se cruzan.");
        assertThat(ventanas()).isEqualTo(antes);
    }

    @Test
    void unaVentanaQueSeCruzaConOtraDelMismoServicioYDiaSeRechaza() {
        UUID servicio = crearServicio();
        ventana(servicio, 1, "09:00", "10:00");

        afirmarCruce(() -> ventana(servicio, 1, "09:30", "10:30"));
        afirmarCruce(() -> ventana(servicio, 1, "08:30", "09:30"));
        afirmarCruce(() -> ventana(servicio, 1, "08:00", "12:00"));
        afirmarCruce(() -> ventana(servicio, 1, "09:15", "09:45"));
        afirmarCruce(() -> ventana(servicio, 1, "09:00", "10:00"));
    }

    @Test
    void ventanasPegadasExtremoConExtremoSeAceptan() {
        UUID servicio = crearServicio();
        ventana(servicio, 1, "09:00", "10:00");

        assertThatCode(() -> {
            ventana(servicio, 1, "08:00", "09:00");
            ventana(servicio, 1, "10:00", "11:00");
        }).doesNotThrowAnyException();
        assertThat(ventanas()).isEqualTo(3);
    }

    @Test
    void elMismoHorarioEnOtroDiaUOtroServicioSeAcepta() {
        UUID servicio = crearServicio();
        UUID otro = crearServicio();
        ventana(servicio, 1, "09:00", "10:00");

        assertThatCode(() -> {
            ventana(servicio, 2, "09:00", "10:00");
            ventana(otro, 1, "09:30", "10:30");
        }).doesNotThrowAnyException();
        assertThat(ventanas()).isEqualTo(3);
    }

    @Test
    void unInsertDeVariasVentanasQueSeCruzanEntreSiSeRechazaYPegadasSeAcepta() {
        UUID servicio = crearServicio();
        String insertar = "INSERT INTO ventana_disponibilidad (servicio_id, id, dia, hora_inicio, hora_fin) "
                + "VALUES (?, ?, 3, ?, ?), (?, ?, 3, ?, ?)";

        afirmarCruce(() -> jdbc.update(insertar, servicio, UUID.randomUUID(), "09:00", "10:00", servicio,
                UUID.randomUUID(), "09:30", "10:30"));
        assertThatCode(() -> jdbc.update(insertar, servicio, UUID.randomUUID(), "09:00", "10:00", servicio,
                UUID.randomUUID(), "10:00", "11:00")).doesNotThrowAnyException();
        assertThat(ventanas()).isEqualTo(2);
    }

    @Test
    void moverUnaVentanaEncimaDeOtraOCambiarleElDiaSeRechaza() {
        UUID servicio = crearServicio();
        ventana(servicio, 1, "09:00", "10:00");
        UUID otra = ventana(servicio, 1, "12:00", "13:00");
        UUID otroDia = ventana(servicio, 2, "09:30", "10:30");

        afirmarCruce(() -> jdbc.update("UPDATE ventana_disponibilidad SET hora_inicio = '09:30', hora_fin = '10:30' "
                + "WHERE servicio_id = ? AND id = ?", servicio, otra));
        afirmarCruce(() -> jdbc.update("UPDATE ventana_disponibilidad SET dia = 1 WHERE servicio_id = ? AND id = ?",
                servicio, otroDia));
    }

    @Test
    void unaVentanaNoSeComparaConsigoMisma() {
        UUID servicio = crearServicio();
        UUID id = ventana(servicio, 1, "09:00", "10:00");
        ventana(servicio, 1, "12:00", "13:00");

        // Moverla a un horario que se cruza con el suyo anterior, o dejarla igual, no es un cruce.
        assertThatCode(() -> {
            jdbc.update("UPDATE ventana_disponibilidad SET hora_inicio = '09:30', hora_fin = '10:30' "
                    + "WHERE servicio_id = ? AND id = ?", servicio, id);
            jdbc.update("UPDATE ventana_disponibilidad SET dia = 1 WHERE servicio_id = ? AND id = ?", servicio, id);
        }).doesNotThrowAnyException();
    }

    @Test
    void unUpdateDeVariasVentanasSeVerificaConElResultadoFinal() {
        UUID servicio = crearServicio();
        UUID a = ventana(servicio, 1, "09:00", "10:00");
        UUID b = ventana(servicio, 1, "10:00", "11:00");

        // Desplazar las dos una hora en una sola sentencia deja un estado final válido.
        assertThatCode(() -> jdbc.update("UPDATE ventana_disponibilidad SET hora_inicio = DATEADD(HOUR, 1, "
                + "hora_inicio), hora_fin = DATEADD(HOUR, 1, hora_fin) WHERE servicio_id = ?", servicio))
                .doesNotThrowAnyException();
        // Llevar las dos al mismo bloque no.
        afirmarCruce(() -> jdbc.update("UPDATE ventana_disponibilidad SET hora_inicio = '10:00', hora_fin = '12:00' "
                + "WHERE servicio_id = ? AND id IN (?, ?)", servicio, a, b));
    }

    // ---- Carreras ----

    private static final int REPETICIONES = 20;

    /**
     * Dos conexiones propias abren transacción e insertan a la vez dos citas solapadas (inicios distintos, así que el
     * índice único no las distingue); cada una espera a que la otra haya insertado (o a que se agote un plazo corto,
     * si la base la tiene en espera) antes de confirmar. Exactamente una gana; la otra recibe el error del disparador
     * o es la víctima de un interbloqueo. Nunca dos ganadoras.
     */
    @Test
    void dosTransaccionesConCitasSolapadasALaVezNoGananLasDos() throws Exception {
        UUID servicio = preparar();
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            for (int repeticion = 0; repeticion < REPETICIONES; repeticion++) {
                int i = repeticion;
                String fecha = LocalDate.of(2030, 2, 1).plusDays(i).toString();
                CountDownLatch salida = new CountDownLatch(2);
                CountDownLatch insertaron = new CountDownLatch(2);
                List<Future<Object>> futuros = new ArrayList<>();
                futuros.add(hilos.submit(() -> insertarYConfirmar(servicio, fecha, "10:00", "11:00", salida,
                        insertaron)));
                futuros.add(hilos.submit(() -> insertarYConfirmar(servicio, fecha, "10:30", "11:30", salida,
                        insertaron)));
                List<Object> resultados = new ArrayList<>();
                for (Future<Object> f : futuros) {
                    resultados.add(f.get(60, TimeUnit.SECONDS));
                }

                assertThat(resultados).as("repetición %d: exactamente una gana", i)
                        .filteredOn(r -> r instanceof Boolean).hasSize(1);
                assertThat(resultados).filteredOn(r -> r instanceof SQLException).singleElement()
                        .satisfies(e -> assertThat(((SQLException) e).getErrorCode()).as("repetición %d", i)
                                .isIn(ERROR_SOLAPE, ERROR_INTERBLOQUEO));
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cita WHERE fecha = ?", Integer.class, fecha))
                        .isEqualTo(1);
            }
        } finally {
            hilos.shutdownNow();
        }
    }

    /**
     * Atender o cancelar una cita (un UPDATE que no cambia su horario) no toma bloqueos de rango sobre las demás citas
     * del servicio y día: la conexión 1 deja sin confirmar ese UPDATE sobre la cita de las 10:00 y la conexión 2 lee
     * la de las 12:00 con UPDLOCK por el índice filtrado (como la lectura del disparador en una reserva o un traslado)
     * con un plazo de bloqueo corto; no debe esperar (error 1222). Y la regla sigue rigiendo para mover e insertar.
     */
    @Test
    void atenderOCancelarUnaCitaNoBloqueaLasDemasDelServicio() throws Exception {
        UUID servicio = preparar();
        UUID a = cita(servicio, "10:00", "11:00");
        UUID b = cita(servicio, "12:00", "13:00");

        assertThat(leerLaDeLas12Mientras("UPDATE cita SET estado = 'Atendida' WHERE id = ?", a, servicio))
                .as("atender la de las 10:00 no bloquea la de las 12:00 (código de error)").isNull();
        assertThat(leerLaDeLas12Mientras("UPDATE cita SET estado = 'Cancelada', cancelada_en = "
                + "'2030-01-01 09:00:00' WHERE id = ?", a, servicio))
                .as("cancelar la de las 10:00 no bloquea la de las 12:00 (código de error)").isNull();

        // Las dos actualizaciones se revirtieron: la de las 10:00 sigue ocupando su horario.
        afirmarSolape(() -> jdbc.update("UPDATE cita SET hora_inicio = '10:30', hora_fin = '11:30' WHERE id = ?", b));
        afirmarSolape(() -> cita(servicio, "12:30", "13:30"));
    }

    /**
     * Con la conexión 1 dejando sin confirmar {@code actualizacion} sobre {@code cita}, la conexión 2 lee con UPDLOCK
     * la cita de las 12:00 del servicio. Devuelve {@code null} si la leyó sin esperar, o el código del error de SQL
     * Server (1222 si un bloqueo de la conexión 1 la detuvo). Ambas transacciones se revierten.
     */
    private Integer leerLaDeLas12Mientras(String actualizacion, UUID cita, UUID servicio) throws SQLException {
        try (Connection escritora = fuente.getConnection(); Connection lectora = fuente.getConnection()) {
            escritora.setAutoCommit(false);
            try {
                try (PreparedStatement actualizar = escritora.prepareStatement(actualizacion)) {
                    actualizar.setObject(1, cita);
                    assertThat(actualizar.executeUpdate()).isEqualTo(1);
                }
                lectora.setAutoCommit(false);
                try (Statement plazo = lectora.createStatement()) {
                    plazo.execute("SET LOCK_TIMEOUT 2000");
                }
                try (PreparedStatement leer = lectora.prepareStatement("SELECT id FROM cita WITH (UPDLOCK, "
                        + "INDEX (ix_cita_servicio_ocupada), FORCESEEK) WHERE servicio_id = ? AND fecha = ? "
                        + "AND hora_inicio = '12:00' AND estado <> 'Cancelada'")) {
                    leer.setObject(1, servicio);
                    leer.setString(2, FECHA);
                    try (ResultSet filas = leer.executeQuery()) {
                        assertThat(filas.next()).isTrue();
                    }
                    return null;
                } catch (SQLException e) {
                    return e.getErrorCode();
                } finally {
                    lectora.rollback();
                    lectora.setAutoCommit(true);
                    // La conexión vuelve al grupo: sin plazo, como las demás.
                    try (Statement plazo = lectora.createStatement()) {
                        plazo.execute("SET LOCK_TIMEOUT -1");
                    }
                }
            } finally {
                escritora.rollback();
                escritora.setAutoCommit(true);
            }
        }
    }

    /** Devuelve {@code true} si confirmó, o la {@link SQLException} con que la base la rechazó. */
    private Object insertarYConfirmar(UUID servicio, String fecha, String inicio, String fin, CountDownLatch salida,
            CountDownLatch insertaron) throws Exception {
        try (Connection conexion = fuente.getConnection()) {
            conexion.setAutoCommit(false);
            try {
                salida.countDown();
                salida.await(10, TimeUnit.SECONDS);
                try (PreparedStatement insertar = conexion.prepareStatement(INSERTAR_CITA)) {
                    insertar.setObject(1, UUID.randomUUID());
                    insertar.setObject(2, servicio);
                    insertar.setObject(3, cliente);
                    insertar.setString(4, fecha);
                    insertar.setString(5, inicio);
                    insertar.setString(6, fin);
                    insertar.setString(7, "Confirmada");
                    insertar.setString(8, "Confirmada");
                    insertar.executeUpdate();
                }
                insertaron.countDown();
                // Si la otra está detenida por un bloqueo, no llegará: el plazo evita esperarla para siempre.
                insertaron.await(1, TimeUnit.SECONDS);
                conexion.commit();
                return Boolean.TRUE;
            } catch (SQLException e) {
                insertaron.countDown();
                conexion.rollback();
                return e;
            } finally {
                conexion.setAutoCommit(true);
            }
        }
    }
}

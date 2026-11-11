package com.datemanager.infraestructura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.datemanager.CapturaRegistro;
import com.datemanager.PruebaIntegracion;
import com.datemanager.dominio.Cita;
import com.datemanager.dominio.EstadoCita;
import com.datemanager.dominio.Permiso;
import com.datemanager.dominio.Servicio;
import com.datemanager.infraestructura.persistencia.CitaRepositorio;
import com.datemanager.infraestructura.persistencia.ClienteRepositorio;
import com.datemanager.infraestructura.persistencia.RolRepositorio;
import com.datemanager.infraestructura.persistencia.ServicioRepositorio;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio;
import com.datemanager.infraestructura.persistencia.VentanaRepositorio;
import com.datemanager.infraestructura.seguridad.Bcrypt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Datos de ejemplo del perfil dev: se prueban aquí sin el perfil dev, construyendo la semilla a mano. */
class SemillaDatosTest extends PruebaIntegracion {

    /** Lunes 9 de noviembre de 2026, 10:00 en Bogotá. */
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 9, 10, 0);

    private static final Bcrypt CODIFICADOR = new Bcrypt();

    private RolRepositorio roles;
    private UsuarioRepositorio usuarios;
    private ServicioRepositorio servicios;
    private ClienteRepositorio clientes;
    private CitaRepositorio citas;
    /** Los avisos de la semilla y del administrador no ensucian la salida de pruebas. */
    private CapturaRegistro silencioSemilla;
    private CapturaRegistro silencioAdministrador;

    @BeforeEach
    void preparar() {
        fijarAhora(AHORA);
        roles = new RolRepositorio(tx);
        usuarios = new UsuarioRepositorio(tx);
        servicios = new ServicioRepositorio(tx, new VentanaRepositorio(tx));
        clientes = new ClienteRepositorio(tx);
        citas = new CitaRepositorio(tx);
        silencioSemilla = new CapturaRegistro(SemillaDatos.class, true);
        silencioAdministrador = new CapturaRegistro(AdministradorInicial.class, true);
    }

    @AfterEach
    void restaurarRegistro() {
        silencioSemilla.close();
        silencioAdministrador.close();
    }

    private SemillaDatos semilla() {
        return new SemillaDatos(tx, roles, usuarios, servicios, clientes, citas, CODIFICADOR, reloj);
    }

    private AdministradorInicial administrador(String usuario, String clave) {
        return new AdministradorInicial(tx, roles, usuarios, CODIFICADOR, usuario, clave);
    }

    private static int contar(String tabla) {
        return consultarInt("SELECT COUNT(*) FROM " + tabla);
    }

    private static Resp intentarEntrar(String usuario, String clave) {
        return post("/api/auth/login").cuerpo(Map.of("usuario", usuario, "clave", clave)).enviar();
    }

    private static String entrar(String usuario, String clave) {
        Resp r = intentarEntrar(usuario, clave);
        assertEquals(200, r.estado(), r.texto());
        return r.texto("token");
    }

    private static <T extends Comparable<T>> List<T> ordenada(List<T> lista) {
        return lista.stream().sorted().toList();
    }

    // ---------- Qué se crea ----------

    @Test
    void enUnaBaseVaciaCreaRolesUsuariosServiciosClientesYCitas() {
        assertTrue(semilla().sembrarSiVacia());

        assertEquals(2, contar("rol"));
        assertEquals(ordenada(List.of("Administrador", "Recepción")), ordenada(columna("SELECT nombre FROM rol")));
        assertEquals(ordenada(List.of("marta", "andres")), ordenada(columna("SELECT usuario FROM usuario")));
        assertEquals(3, contar("servicio"));
        assertEquals(List.of("30", "60", "90"),
                columna("SELECT CAST(duracion_min AS NVARCHAR(10)) FROM servicio ORDER BY duracion_min"));
        assertEquals(25, contar("ventana_disponibilidad"));
        assertEquals(8, contar("cliente"));
        assertEquals(23, contar("cita"));
        assertEquals(0, contar("enlace_invitacion"));
    }

    @Test
    void elRolAdministradorTieneTodosLosPermisosYRecepcionSoloLosDeLaAgenda() {
        semilla().sembrarSiVacia();

        assertEquals(ordenada(Arrays.stream(Permiso.values()).map(Permiso::id).toList()),
                ordenada(permisosDe("Administrador")));
        assertEquals(ordenada(List.of("ver_citas", "gestionar_citas", "atender_citas", "gestionar_clientes")),
                ordenada(permisosDe("Recepción")));
    }

    private static List<String> permisosDe(String rol) {
        return columna("""
                SELECT p.permiso FROM rol_permiso p JOIN rol r ON r.id = p.rol_id WHERE r.nombre = ?
                """, rol);
    }

    @Test
    void laClaveDeEjemploSoloSeGuardaConHashYPermiteEntrar() {
        semilla().sembrarSiVacia();

        for (String hash : columna("SELECT clave_hash FROM usuario")) {
            assertTrue(hash.startsWith("$2"), hash);
            assertFalse(hash.contains("demo123"));
            assertTrue(CODIFICADOR.coincide("demo123", hash));
        }
        assertFalse(entrar("marta", "demo123").isBlank());
        assertFalse(entrar("andres", "demo123").isBlank());
        assertEquals(401, post("/api/auth/login").cuerpoCrudo("{\"usuario\":\"marta\",\"clave\":\"otra\"}").enviar()
                .estado());
    }

    @Test
    void martaEsAdministradoraYAndresRecepcion() {
        semilla().sembrarSiVacia();

        assertEquals("Administrador", consultarTexto("""
                SELECT r.nombre FROM usuario u JOIN rol r ON r.id = u.rol_id WHERE u.usuario = 'marta'
                """));
        assertEquals("Recepción", consultarTexto("""
                SELECT r.nombre FROM usuario u JOIN rol r ON r.id = u.rol_id WHERE u.usuario = 'andres'
                """));
    }

    // ---------- Horarios libres por la API real ----------

    @Test
    void cadaServicioSembradoTieneHorariosLibresPorElEndpointDelPanel() {
        semilla().sembrarSiVacia();
        String token = entrar("marta", "demo123");

        for (String servicio : columna("SELECT CAST(id AS NVARCHAR(36)) FROM servicio")) {
            boolean algunoLibre = false;
            for (int dias = 0; dias <= 14 && !algunoLibre; dias++) {
                String fecha = AHORA.toLocalDate().plusDays(dias).toString();
                Resp r = get("/api/panel/servicios/" + servicio.toLowerCase() + "/horarios?fecha=" + fecha)
                        .conToken(token).enviar();
                assertEquals(200, r.estado(), r.texto());
                algunoLibre = !r.lista("$").isEmpty();
            }
            assertTrue(algunoLibre, "servicio " + servicio + " con horarios libres en 15 días");
        }
    }

    // ---------- Idempotencia y «solo si la base está vacía» ----------

    @Test
    void unaSegundaEjecucionNoDuplicaNada() {
        assertTrue(semilla().sembrarSiVacia());
        Map<String, Integer> antes = conteos();

        assertFalse(semilla().sembrarSiVacia());

        assertEquals(antes, conteos());
    }

    @Test
    void conUnaBaseConDatosPropiosNoSiembraNada() {
        UUID rolId = UUID.randomUUID();
        ejecutar("INSERT INTO rol (id, nombre, descripcion) VALUES (?, N'Mío', '')", rolId);

        assertFalse(semilla().sembrarSiVacia());

        assertEquals(1, conteos().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void conUnClienteSueltoTampocoSiembra() {
        ejecutar("INSERT INTO cliente (id, nombre, apellido, telefono, correo) VALUES (?, 'A', 'B', '1', 'a@b.co')",
                UUID.randomUUID());

        assertFalse(semilla().sembrarSiVacia());

        assertEquals(0, contar("servicio"));
        assertEquals(0, contar("usuario"));
    }

    @Test
    void convivePorOrdenConElAdministradorInicial() {
        // La semilla corre primero: crea marta y andres, y el administrador de DATEMANAGER_ADMIN_* ya no hace falta.
        semilla().sembrarSiVacia();
        administrador("admin", "clave-de-prueba-123").crearSiHaceFalta();

        assertEquals(2, contar("usuario"));
        assertEquals(2, contar("rol"));
    }

    @Test
    void siElAdministradorInicialYaExistiaLaSemillaNoLoToca() {
        administrador("marta", "clave-de-prueba-123").crearSiHaceFalta();

        assertFalse(semilla().sembrarSiVacia());

        assertEquals(1, contar("usuario"));
        assertEquals(0, contar("servicio"));
    }

    private Map<String, Integer> conteos() {
        return List.of("rol", "rol_permiso", "usuario", "servicio", "ventana_disponibilidad", "cliente", "cita",
                        "enlace_invitacion").stream()
                .collect(Collectors.toMap(t -> t, SemillaDatosTest::contar));
    }

    // ---------- Solo en el perfil dev ----------

    @Test
    void soloSeUsaEnElPerfilDevYLaAplicacionDePruebaNoSembro() {
        // Aplicacion solo crea la semilla con DATEMANAGER_PERFIL=dev (lo prueba ArranqueTest); la de pruebas no lo es.
        assertFalse(configuracion().dev());
        // Al arrancar la aplicación de prueba no sembró nada (la base la vacía PruebaBase antes de cada prueba).
        assertEquals(0, contar("usuario"));
    }

    // ---------- Reglas de negocio de las citas sembradas, en cualquier día y hora ----------

    @Test
    void lasCitasSembradasRespetanLasReglasEnCualquierDiaYHora() {
        for (int dia = 9; dia <= 15; dia++) {
            for (LocalTime hora : List.of(LocalTime.of(0, 0), LocalTime.of(7, 0), LocalTime.of(10, 0),
                    LocalTime.of(13, 30), LocalTime.of(17, 30), LocalTime.of(23, 30))) {
                LocalDateTime ahora = LocalDateTime.of(2026, 11, dia, hora.getHour(), hora.getMinute());
                limpiarDatos();
                fijarAhora(ahora);

                assertTrue(semilla().sembrarSiVacia(), "sembró a las " + ahora);

                verificarReglas(ahora);
            }
        }
    }

    private void verificarReglas(LocalDateTime ahora) {
        List<Servicio> catalogo = tx.leer(servicios::todos);
        List<Cita> todas = tx.leer(() -> citas.entreFechas(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1)));
        assertEquals(23, todas.size(), "citas a las " + ahora);

        for (Cita c : todas) {
            String quien = "cita %s %s %s %s a las %s".formatted(c.estado(), c.servicioId(), c.fecha(), c.inicio(), ahora);
            Servicio s = catalogo.stream().filter(x -> x.id().equals(c.servicioId())).findFirst().orElseThrow();
            assertEquals(c.inicio().plusMinutes(s.duracionMin()), c.fin(), quien);
            // dentro de una ventana de ese día y alineada al paso de 30 minutos desde su inicio
            assertTrue(s.ventanas().stream().anyMatch(v -> v.dia() == c.fecha().getDayOfWeek().getValue()
                    && !c.inicio().isBefore(v.inicio()) && !c.fin().isAfter(v.fin())
                    && Duration.between(v.inicio(), c.inicio()).toMinutes() % 30 == 0), quien);

            LocalDateTime empieza = c.fecha().atTime(c.inicio());
            LocalDateTime termina = c.fecha().atTime(c.fin());
            if (c.estado() == EstadoCita.Atendida) {
                assertFalse(termina.isAfter(ahora), quien);
            }
            if (c.estado() == EstadoCita.Confirmada) {
                assertFalse(empieza.isBefore(ahora), quien);
                assertFalse(c.fecha().isAfter(ahora.toLocalDate().plusDays(30)), quien);
            }
            assertFalse(c.fecha().isBefore(ahora.toLocalDate().minusDays(30)), quien);
            assertFalse(c.creadaEn().isAfter(reloj.instant()), quien);
            if (c.estado() == EstadoCita.Cancelada) {
                assertNotNull(c.canceladaEn(), quien);
                assertFalse(c.canceladaEn().isBefore(c.creadaEn()), quien);
                assertFalse(c.canceladaEn().isAfter(reloj.instant()), quien);
            } else {
                assertNull(c.canceladaEn(), quien);
            }
        }

        // sin solapes por servicio y día (las canceladas también se esquivan: así ninguna se cruza con otra)
        for (int i = 0; i < todas.size(); i++) {
            for (int j = i + 1; j < todas.size(); j++) {
                Cita a = todas.get(i);
                Cita b = todas.get(j);
                if (a.servicioId().equals(b.servicioId()) && a.fecha().equals(b.fecha())) {
                    assertFalse(a.inicio().isBefore(b.fin()) && b.inicio().isBefore(a.fin()),
                            "solape %s/%s %s %s-%s con %s-%s".formatted(a.servicioId(), a.fecha(), a.estado(),
                                    a.inicio(), a.fin(), b.inicio(), b.fin()));
                }
            }
        }
        List<EstadoCita> estados = todas.stream().map(Cita::estado).distinct().toList();
        assertEquals(3, estados.size(), "estados " + estados);
        assertEquals(Set.of(EstadoCita.Atendida, EstadoCita.Confirmada, EstadoCita.Cancelada), Set.copyOf(estados));
        assertTrue(todas.stream().map(Cita::fecha).min(LocalDate::compareTo).orElseThrow()
                .isBefore(ahora.toLocalDate()));
    }
}

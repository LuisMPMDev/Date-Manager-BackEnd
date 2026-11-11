package com.datemanager;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.datemanager.aplicacion.CitasServicio;
import com.datemanager.aplicacion.ClientesServicio;
import com.datemanager.aplicacion.ServiciosServicio;
import com.datemanager.dominio.Cita;
import com.datemanager.dominio.DatosCliente;
import com.datemanager.infraestructura.persistencia.CitaRepositorio;
import com.datemanager.infraestructura.persistencia.ClienteRepositorio;
import com.datemanager.infraestructura.persistencia.ErrorSql;
import com.datemanager.infraestructura.persistencia.ErroresBase;
import com.datemanager.infraestructura.persistencia.ServicioRepositorio;
import com.datemanager.infraestructura.persistencia.VentanaRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.function.Executable;

/**
 * Base de las pruebas de servicios, clientes y citas: repositorios y servicios con un reloj mutable en la zona del
 * negocio, y atajos para sembrar datos por SQL.
 */
public abstract class PruebaAgenda extends PruebaAdministracion {

    protected static final ZoneId ZONA = ZoneId.of("America/Bogota");
    /** Lunes 9 de noviembre de 2026, 08:00 en Bogotá. */
    protected static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 9, 8, 0);
    protected static final LocalDate MANANA = LocalDate.of(2026, 11, 10);

    protected RelojMutable reloj;
    protected VentanaRepositorio ventanas;
    protected ServicioRepositorio serviciosRepo;
    protected ClienteRepositorio clientesRepo;
    protected CitaRepositorio citasRepo;
    protected ServiciosServicio serviciosServicio;
    protected ClientesServicio clientesServicio;
    protected CitasServicio citasServicio;

    @BeforeEach
    void armarAgenda() {
        reloj = new RelojMutable(ZONA);
        ventanas = new VentanaRepositorio(tx);
        serviciosRepo = new ServicioRepositorio(tx, ventanas);
        clientesRepo = new ClienteRepositorio(tx);
        citasRepo = new CitaRepositorio(tx);
        serviciosServicio = new ServiciosServicio(tx, serviciosRepo);
        clientesServicio = new ClientesServicio(tx, clientesRepo);
        citasServicio = new CitasServicio(tx, citasRepo, serviciosServicio, clientesServicio, reloj);
    }

    /** Fija "ahora" a esa hora de pared en la zona del negocio (no en la de la JVM). */
    protected void fijarAhora(LocalDateTime ahora) {
        reloj.fijarAhora(ahora);
    }

    /** Servicio con la misma ventana los siete días. */
    protected UUID crearServicio(String nombre, int duracion, boolean activo, String inicio, String fin) {
        UUID id = nuevoId();
        ejecutar("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, ?, ?, ?, 0)", id, nombre,
                duracion, activo);
        for (int dia = 1; dia <= 7; dia++) {
            ejecutar("INSERT INTO ventana_disponibilidad (servicio_id, id, dia, hora_inicio, hora_fin) "
                    + "VALUES (?, ?, ?, ?, ?)", id, nuevoId(), dia, LocalTime.parse(inicio), LocalTime.parse(fin));
        }
        return id;
    }

    protected UUID insertarServicio(String duracion, String valor) {
        UUID id = nuevoId();
        ejecutar("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, 'S', " + duracion
                + ", 1, " + valor + ")", id);
        return id;
    }

    protected void insertarVentana(UUID servicioId, int dia, String inicio, String fin) {
        ejecutar("INSERT INTO ventana_disponibilidad (id, servicio_id, dia, hora_inicio, hora_fin) VALUES (?, ?, ?, '"
                + inicio + "', '" + fin + "')", nuevoId(), servicioId, dia);
    }

    protected UUID insertarCliente(String nombre, String apellido, String correo) {
        UUID id = nuevoId();
        ejecutar("INSERT INTO cliente (id, nombre, apellido, telefono, correo) VALUES (?, ?, ?, '300', ?)", id, nombre,
                apellido, correo);
        return id;
    }

    /** Un enlace de invitación (cita.enlace_id es una llave foránea) con su rol y usuario creador. */
    protected UUID crearEnlace() {
        UUID usuario = crearUsuario("creador", crearRol("Creador"));
        UUID enlace = nuevoId();
        ejecutar("INSERT INTO enlace_invitacion (id, codigo, creado_por, creado_en, vence_en, usos) "
                + "VALUES (?, 'AAAAAAAAAAAAAAAAAAAAAA', ?, '2026-11-09T13:00:00', '2026-11-10T13:00:00', 0)",
                enlace, usuario);
        return enlace;
    }

    protected static DatosCliente ana() {
        return new DatosCliente("Ana", "Pérez", "300 1", "ana@x.co");
    }

    protected static DatosCliente cliente(String nombre, String correo, String telefono) {
        return new DatosCliente(nombre, "Pérez", telefono, correo);
    }

    protected Cita reservar(UUID servicio, LocalDate fecha, String inicio) {
        return citasServicio.reservar(servicio, fecha, LocalTime.parse(inicio), ana(), null);
    }

    protected static int contar(String tabla) {
        return consultarInt("SELECT COUNT(*) FROM " + tabla);
    }

    /** La acción debe fallar con un error de la base que {@link ErroresBase} reconoce como violación de restricción. */
    protected static void exigirRestriccion(Executable accion) {
        ErrorSql error = assertThrows(ErrorSql.class, accion);
        assertTrue(ErroresBase.esViolacionDeRestriccion(error), "no es una violación de restricción: " + error);
    }

    protected static List<String> horas(List<LocalTime> libres) {
        return libres.stream().map(LocalTime::toString).toList();
    }
}

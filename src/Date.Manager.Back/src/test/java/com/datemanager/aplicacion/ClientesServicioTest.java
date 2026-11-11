package com.datemanager.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.datemanager.PruebaAgenda;
import com.datemanager.dominio.Cita;
import com.datemanager.dominio.Cliente;
import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.DatosCliente;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.infraestructura.persistencia.ClienteRepositorio;
import org.junit.jupiter.api.Test;

/** Casos de uso de clientes (portados de las pruebas del controlador de la aplicación original). */
class ClientesServicioTest extends PruebaAgenda {

    private static DatosCliente datos(String nombre, String apellido, String telefono, String correo) {
        return new DatosCliente(nombre, apellido, telefono, correo);
    }

    private static String nombreEnBase(UUID id) {
        return consultarTexto("SELECT nombre FROM cliente WHERE id = ?", id);
    }

    private static String correoEnBase(UUID id) {
        return consultarTexto("SELECT correo FROM cliente WHERE id = ?", id);
    }

    @Test
    void listarDevuelveLosClientesOrdenadosPorNombreYApellido() {
        UUID zoe = insertarCliente("Zoe", "Arias", "zoe@x.co");
        UUID anaR = insertarCliente("Ana", "Ruiz", "ana.r@x.co");
        UUID anaG = insertarCliente("Ana", "Gómez", "ana.g@x.co");

        List<Cliente> clientes = clientesServicio.listar();

        assertEquals(List.of(anaG, anaR, zoe), clientes.stream().map(Cliente::id).toList());
        assertEquals(new Cliente(anaG, "Ana", "Gómez", "300", "ana.g@x.co"), clientes.get(0));
    }

    @Test
    void editarCambiaLosDatosYNormalizaElCorreo() {
        UUID id = insertarCliente("Ana", "Ruiz", "ana@x.co");

        Cliente editado = clientesServicio.guardar(id, datos(" Ana María ", "Ruiz Gil", "311 2", "  AnaMaria@X.co "));

        assertEquals(new Cliente(id, "Ana María", "Ruiz Gil", "311 2", "anamaria@x.co"), editado);
        assertEquals("anamaria@x.co", correoEnBase(id));
    }

    @Test
    void elNuevoCorreoSeUsaParaReconocerAlClienteAlReservar() {
        UUID id = insertarCliente("Ana", "Ruiz", "ana@x.co");
        clientesServicio.guardar(id, datos("Ana", "Ruiz", "300", "nueva@x.co"));
        fijarAhora(AHORA);
        UUID servicio = nuevoId();
        ejecutar("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, 'S', 30, 1, 0)", servicio);
        ejecutar("INSERT INTO ventana_disponibilidad (servicio_id, id, dia, hora_inicio, hora_fin) "
                + "VALUES (?, ?, 2, '09:00', '12:00')", servicio, nuevoId());

        Cita cita = citasServicio.reservar(servicio, MANANA, LocalTime.of(9, 0),
                datos("Ana", "Ruiz", "300", "NUEVA@x.co"), null);

        assertEquals(id, cita.clienteId());
        assertEquals(1, contar("cliente"));
    }

    @Test
    void cambiarSoloMayusculasDelPropioCorreoNoEsConflicto() {
        UUID id = insertarCliente("Ana", "Ruiz", "ana@x.co");

        assertEquals("ana@x.co", clientesServicio.guardar(id, datos("Ana", "Ruiz", "300", "ANA@x.co ")).correo());
    }

    @Test
    void usarElCorreoDeOtroClienteEsConflicto() {
        insertarCliente("Beto", "Díaz", "beto@x.co");
        UUID ana = insertarCliente("Ana", "Ruiz", "ana@x.co");

        ErrorNegocio error = exigirError(CodigoError.CONFLICTO,
                () -> clientesServicio.guardar(ana, datos("Ana", "Ruiz", "300", " BETO@x.co")));

        assertEquals("Ya existe otro cliente con ese correo.", error.getMessage());
        assertEquals("ana@x.co", correoEnBase(ana));
    }

    @Test
    void siLaBaseRechazaElCorreoRepetidoTambienEsConflicto() {
        // El único de la base es la última defensa: otra transacción creó ese correo entre la lectura y la escritura.
        UUID ana = insertarCliente("Ana", "Ruiz", "ana@x.co");
        insertarCliente("Beto", "Díaz", "beto@x.co");
        ClientesServicio conCarrera = new ClientesServicio(tx, new ClienteRepositorio(tx) {
            @Override
            public Optional<Cliente> porCorreo(String correo) {
                return Optional.empty(); // la lectura previa no ve al otro cliente
            }
        });

        ErrorNegocio error = exigirError(CodigoError.CONFLICTO,
                () -> conCarrera.guardar(ana, datos("Ana", "Ruiz", "300", "beto@x.co")));

        assertEquals("Ya existe otro cliente con ese correo.", error.getMessage());
        assertEquals("ana@x.co", correoEnBase(ana));
    }

    @Test
    void unClienteDesconocidoEsNoEncontradoPorqueElPanelNoCreaClientes() {
        exigirError(CodigoError.NO_ENCONTRADO,
                () -> clientesServicio.guardar(nuevoId(), datos("Ana", "Ruiz", "300", "ana@x.co")));
        assertEquals(0, contar("cliente"));
    }

    @Test
    void datosInvalidosSonValidacionConCampos() {
        UUID id = insertarCliente("Ana", "Ruiz", "ana@x.co");

        assertEquals(Map.of("correo", "Escribe un correo válido, por ejemplo nombre@correo.com."),
                exigirError(CodigoError.VALIDACION,
                        () -> clientesServicio.guardar(id, datos("Ana", "Ruiz", "300", "ana@x"))).campos());
        assertEquals(Map.of("nombre", "Escribe el nombre."),
                exigirError(CodigoError.VALIDACION,
                        () -> clientesServicio.guardar(id, datos(" ", "Ruiz", "300", "ana@x.co"))).campos());
        assertEquals(Map.of("telefono", "Escribe el teléfono."),
                exigirError(CodigoError.VALIDACION,
                        () -> clientesServicio.guardar(id, datos("Ana", "Ruiz", "", "ana@x.co"))).campos());
        assertEquals(Map.of("nombre", "El nombre es demasiado largo."),
                exigirError(CodigoError.VALIDACION,
                        () -> clientesServicio.guardar(id, datos("A".repeat(101), "Ruiz", "300", "ana@x.co")))
                        .campos());

        assertEquals("Ana", nombreEnBase(id));
    }

    @Test
    void validarListaTodosLosCamposInvalidosEnElOrdenDelFormularioYElMensajeEsElDelPrimero() {
        ErrorNegocio error = exigirError(CodigoError.VALIDACION,
                () -> ClientesServicio.validar(datos(null, "", "T".repeat(41), "no es correo")));

        assertEquals(List.of("nombre", "apellido", "telefono", "correo"), new ArrayList<>(error.campos().keySet()));
        assertEquals("Escribe el nombre.", error.getMessage());
        assertEquals("El teléfono es demasiado largo.", error.campos().get("telefono"));
    }

    @Test
    void sinDatosEsUnaValidacionDelCliente() {
        ErrorNegocio error = exigirError(CodigoError.VALIDACION, () -> ClientesServicio.validar(null));

        assertEquals("Faltan los datos del cliente.", error.getMessage());
        assertEquals(Map.of("cliente", "Escribe los datos del cliente."), error.campos());
    }

    @Test
    void elLargoDelNombreSeMideSinLosEspaciosDeLosExtremos() {
        ClientesServicio.validar(datos(" " + "A".repeat(100) + " ", "Ruiz", "300", "ana@x.co"));
    }

    // ---------- obtenerOCrear ----------

    @Test
    void obtenerOCrearExigeUnaTransaccionAbierta() {
        assertThrows(IllegalStateException.class, () -> clientesServicio.obtenerOCrear(ana()));
        assertEquals(0, contar("cliente"));
    }

    @Test
    void obtenerOCrearCreaAlClienteConElCorreoNormalizado() {
        Cliente creado = tx.escribir(() -> clientesServicio.obtenerOCrear(
                datos(" Ana ", " Pérez ", " 300 ", "  ANA@X.co ")));

        assertEquals(new Cliente(creado.id(), "Ana", "Pérez", "300", "ana@x.co"), creado);
        assertEquals(1, contar("cliente"));
        assertEquals("ana@x.co", correoEnBase(creado.id()));
    }

    @Test
    void obtenerOCrearReutilizaAlClienteDelMismoCorreoYActualizaSusDatos() {
        Cliente primero = tx.escribir(() -> clientesServicio.obtenerOCrear(datos("Ana", "Pérez", "300 1", "ANA@x.co")));
        Cliente segundo = tx.escribir(
                () -> clientesServicio.obtenerOCrear(datos("Ana María", "Pérez", "311 2", "ana@x.co ")));

        assertEquals(primero.id(), segundo.id());
        assertEquals(1, contar("cliente"));
        assertEquals(new Cliente(primero.id(), "Ana María", "Pérez", "311 2", "ana@x.co"),
                tx.leer(() -> clientesRepo.buscar(primero.id())).orElseThrow());
    }

    @Test
    void obtenerOCrearValidaLosDatos() {
        exigirError(CodigoError.VALIDACION,
                () -> tx.escribir(() -> clientesServicio.obtenerOCrear(datos("Ana", "Pérez", "300", "ana@"))));
        assertEquals(0, contar("cliente"));
    }

    // ---------- Textos con tildes, ñ y emoji ----------

    @Test
    void elNombreConTildesEnieYEmojiVuelveIntacto() {
        String nombre = "Peña 😀";
        String apellido = "Gómez Núñez 🎉";
        Cliente creado = tx.escribir(
                () -> clientesServicio.obtenerOCrear(datos(nombre, apellido, "300", "emoji@x.co")));

        assertEquals(nombre, creado.nombre());
        Cliente leido = clientesServicio.listar().get(0);
        assertEquals(new Cliente(creado.id(), nombre, apellido, "300", "emoji@x.co"), leido);
        assertEquals(nombre, nombreEnBase(creado.id()));
        assertEquals(2 * nombre.length(), consultarInt("SELECT DATALENGTH(nombre) FROM cliente WHERE id = ?",
                creado.id()), "NVARCHAR guarda las unidades UTF-16 completas (el emoji, su par sustituto)");

        Cliente editado = clientesServicio.guardar(creado.id(), datos("Ñandú 😀😀", "Ruiz", "300", "emoji@x.co"));
        assertEquals("Ñandú 😀😀", editado.nombre());
        assertEquals("Ñandú 😀😀", nombreEnBase(creado.id()));
    }

    @Test
    void elMismoCorreoConAcentosEnElCorreoSeGuardaYSeBuscaIgual() {
        Cliente creado = tx.escribir(() -> clientesServicio.obtenerOCrear(datos("José", "Núñez", "300", "JOSÉ@x.co")));

        assertEquals("josé@x.co", creado.correo());
        assertEquals(creado.id(), tx.escribir(
                () -> clientesServicio.obtenerOCrear(datos("José", "Núñez", "300", " josé@X.co"))).id());
    }
}

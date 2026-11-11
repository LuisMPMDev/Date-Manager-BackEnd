package com.datemanager.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /api/panel/clientes}. La prueba de que el correo editado sirve para reconocer al cliente al reservar (que en
 * la versión Spring llamaba al servicio directamente) está en {@code ClientesServicioTest}.
 */
class ClientesControladorTest extends PruebaPanel {

    private static final String CLIENTES = "/api/panel/clientes";

    private UUID gestor;

    @BeforeEach
    void preparar() {
        gestor = crearUsuario("gestor", crearRol("Gestor", "gestionar_clientes"));
    }

    private UUID insertarCliente(String nombre, String apellido, String correo) {
        UUID id = UUID.randomUUID();
        ejecutar("INSERT INTO cliente (id, nombre, apellido, telefono, correo) VALUES (?, ?, ?, '300', ?)",
                id, nombre, apellido, correo);
        return id;
    }

    private static Map<String, Object> cuerpo(String nombre, String apellido, String telefono, String correo) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("nombre", nombre);
        c.put("apellido", apellido);
        c.put("telefono", telefono);
        c.put("correo", correo);
        return c;
    }

    private static String columnaDe(String columna, UUID id) {
        return consultarTexto("SELECT " + columna + " FROM cliente WHERE id = ?", id);
    }

    @Test
    void listarDevuelveLosClientesOrdenadosPorNombreYApellido() {
        UUID zoe = insertarCliente("Zoe", "Arias", "zoe@x.co");
        UUID anaR = insertarCliente("Ana", "Ruiz", "ana.r@x.co");
        UUID anaG = insertarCliente("Ana", "Gómez", "ana.g@x.co");

        Resp r = exigir(200, obtener(CLIENTES, gestor));
        assertEquals(List.of(anaG.toString(), anaR.toString(), zoe.toString()), r.en("$[*].id"));
        assertEquals("Ana", r.en("$[0].nombre"));
        assertEquals("Gómez", r.en("$[0].apellido"));
        assertEquals("300", r.en("$[0].telefono"));
        assertEquals("ana.g@x.co", r.en("$[0].correo"));
    }

    @Test
    void editarCambiaLosDatosYNormalizaElCorreo() {
        UUID id = insertarCliente("Ana", "Ruiz", "ana@x.co");

        Resp r = exigir(200, guardar(CLIENTES + "/" + id, gestor,
                cuerpo(" Ana María ", "Ruiz Gil", "311 2", "  AnaMaria@X.co ")));
        assertEquals(id.toString(), r.en("id"));
        assertEquals("Ana María", r.en("nombre"));
        assertEquals("Ruiz Gil", r.en("apellido"));
        assertEquals("311 2", r.en("telefono"));
        assertEquals("anamaria@x.co", r.en("correo"));

        assertEquals("anamaria@x.co", columnaDe("correo", id));
    }

    @Test
    void cambiarSoloMayusculasDelPropioCorreoNoEsConflicto() {
        UUID id = insertarCliente("Ana", "Ruiz", "ana@x.co");

        Resp r = exigir(200, guardar(CLIENTES + "/" + id, gestor, cuerpo("Ana", "Ruiz", "300", "ANA@x.co ")));
        assertEquals("ana@x.co", r.en("correo"));
    }

    @Test
    void usarElCorreoDeOtroClienteEs409() {
        insertarCliente("Beto", "Díaz", "beto@x.co");
        UUID ana = insertarCliente("Ana", "Ruiz", "ana@x.co");

        Resp r = exigir(409, "CONFLICTO", guardar(CLIENTES + "/" + ana, gestor, cuerpo("Ana", "Ruiz", "300", " BETO@x.co")));
        assertEquals("Ya existe otro cliente con ese correo.", r.en("mensaje"));

        assertEquals("ana@x.co", columnaDe("correo", ana));
    }

    @Test
    void unClienteDesconocidoEs404PorqueElPanelNoCreaClientes() {
        exigir(404, "NO_ENCONTRADO",
                guardar(CLIENTES + "/" + UUID.randomUUID(), gestor, cuerpo("Ana", "Ruiz", "300", "ana@x.co")));
        assertEquals(0, contar("cliente"));
    }

    @Test
    void datosInvalidosSon400ConCampos() {
        UUID id = insertarCliente("Ana", "Ruiz", "ana@x.co");

        Resp correo = exigir(400, "VALIDACION", guardar(CLIENTES + "/" + id, gestor, cuerpo("Ana", "Ruiz", "300", "ana@x")));
        assertFalse(correo.texto("campos.correo").isEmpty());
        Resp nombre = exigir(400, guardar(CLIENTES + "/" + id, gestor, cuerpo(" ", "Ruiz", "300", "ana@x.co")));
        assertEquals("Escribe el nombre.", nombre.en("campos.nombre"));
        assertEquals("Hay datos inválidos en la petición.", nombre.en("mensaje"));
        Resp telefono = exigir(400, guardar(CLIENTES + "/" + id, gestor, cuerpo("Ana", "Ruiz", "", "ana@x.co")));
        assertEquals("Escribe el teléfono.", telefono.en("campos.telefono"));
        Resp largo = exigir(400, guardar(CLIENTES + "/" + id, gestor, cuerpo("A".repeat(101), "Ruiz", "300", "ana@x.co")));
        assertEquals("El nombre es demasiado largo.", largo.en("campos.nombre"));

        assertEquals("Ana", columnaDe("nombre", id));
    }

    @Test
    void unNombreConEnieYEmojiVaYVuelveIntacto() {
        UUID id = insertarCliente("Ana", "Ruiz", "ana@x.co");

        Resp r = exigir(200, guardar(CLIENTES + "/" + id, gestor, cuerpo("Peña 😀", "Ñúñez", "300", "ana@x.co")));
        assertEquals("Peña 😀", r.en("nombre"));
        assertEquals("Ñúñez", r.en("apellido"));

        assertEquals("Peña 😀", columnaDe("nombre", id));
        assertEquals("Peña 😀", obtener(CLIENTES, gestor).en("$[0].nombre"));
    }

    @Test
    void verCitasLeeClientesPeroNoLosEdita() {
        UUID id = insertarCliente("Ana", "Ruiz", "ana@x.co");
        UUID lector = crearUsuario("lector", crearRol("Lector", "ver_citas"));

        assertEquals(1, exigir(200, obtener(CLIENTES, lector)).lista("$").size());
        exigir(403, "SIN_PERMISO", guardar(CLIENTES + "/" + id, lector, cuerpo("Otro", "Ruiz", "300", "ana@x.co")));
        assertEquals("Ana", columnaDe("nombre", id));
    }

    @Test
    void sinPermisosDeClientesEs403() {
        UUID id = insertarCliente("Ana", "Ruiz", "ana@x.co");
        UUID otro = crearUsuario("otro", crearRol("Otro", "ver_panel", "gestionar_citas", "atender_citas"));

        exigir(403, obtener(CLIENTES, otro));
        exigir(403, guardar(CLIENTES + "/" + id, otro, cuerpo("Otro", "Ruiz", "300", "ana@x.co")));
    }

    @Test
    void sinTokenEs401() {
        UUID id = insertarCliente("Ana", "Ruiz", "ana@x.co");

        exigir(401, "NO_AUTENTICADO", get(CLIENTES).enviar());
        exigir(401, put(CLIENTES + "/" + id).cuerpo(cuerpo("Otro", "Ruiz", "300", "ana@x.co")).enviar());
    }
}

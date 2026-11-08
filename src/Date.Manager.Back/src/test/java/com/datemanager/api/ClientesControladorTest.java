package com.datemanager.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.datemanager.aplicacion.CitasServicio;
import com.datemanager.dominio.Cita;
import com.datemanager.dominio.DatosCliente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class ClientesControladorTest extends PruebaPanel {

    private static final String CLIENTES = "/api/panel/clientes";

    @Autowired
    private CitasServicio citas;

    private UUID gestor;

    @BeforeEach
    void preparar() {
        gestor = crearUsuario("gestor", crearRol("Gestor", "gestionar_clientes"));
    }

    private UUID insertarCliente(String nombre, String apellido, String correo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cliente (id, nombre, apellido, telefono, correo) VALUES (?, ?, ?, '300', ?)",
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

    @Test
    void listarDevuelveLosClientesOrdenadosPorNombreYApellido() throws Exception {
        UUID zoe = insertarCliente("Zoe", "Arias", "zoe@x.co");
        UUID anaR = insertarCliente("Ana", "Ruiz", "ana.r@x.co");
        UUID anaG = insertarCliente("Ana", "Gómez", "ana.g@x.co");

        obtener(CLIENTES, gestor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(anaG.toString(), anaR.toString(), zoe.toString())))
                .andExpect(jsonPath("$[0].nombre").value("Ana"))
                .andExpect(jsonPath("$[0].apellido").value("Gómez"))
                .andExpect(jsonPath("$[0].telefono").value("300"))
                .andExpect(jsonPath("$[0].correo").value("ana.g@x.co"));
    }

    @Test
    void editarCambiaLosDatosYNormalizaElCorreo() throws Exception {
        UUID id = insertarCliente("Ana", "Ruiz", "ana@x.co");

        guardar(CLIENTES + "/" + id, gestor, cuerpo(" Ana María ", "Ruiz Gil", "311 2", "  AnaMaria@X.co "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.nombre").value("Ana María"))
                .andExpect(jsonPath("$.apellido").value("Ruiz Gil"))
                .andExpect(jsonPath("$.telefono").value("311 2"))
                .andExpect(jsonPath("$.correo").value("anamaria@x.co"));

        assertThat(jdbc.queryForObject("SELECT correo FROM cliente WHERE id = ?", String.class, id))
                .isEqualTo("anamaria@x.co");
    }

    @Test
    void elNuevoCorreoSeUsaParaReconocerAlClienteAlReservar() throws Exception {
        UUID id = insertarCliente("Ana", "Ruiz", "ana@x.co");
        guardar(CLIENTES + "/" + id, gestor, cuerpo("Ana", "Ruiz", "300", "nueva@x.co")).andExpect(status().isOk());
        fijarAhora(LocalDateTime.of(2026, 11, 9, 8, 0));
        UUID servicio = UUID.randomUUID();
        jdbc.update("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, 'S', 30, 1, 0)", servicio);
        jdbc.update("INSERT INTO ventana_disponibilidad (servicio_id, id, dia, hora_inicio, hora_fin) "
                + "VALUES (?, ?, 2, '09:00', '12:00')", servicio, UUID.randomUUID());

        Cita cita = citas.reservar(servicio, LocalDate.of(2026, 11, 10), LocalTime.of(9, 0),
                new DatosCliente("Ana", "Ruiz", "300", "NUEVA@x.co"), null);

        assertThat(cita.clienteId()).isEqualTo(id);
        assertThat(contar("cliente")).isEqualTo(1);
    }

    @Test
    void cambiarSoloMayusculasDelPropioCorreoNoEsConflicto() throws Exception {
        UUID id = insertarCliente("Ana", "Ruiz", "ana@x.co");

        guardar(CLIENTES + "/" + id, gestor, cuerpo("Ana", "Ruiz", "300", "ANA@x.co "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("ana@x.co"));
    }

    @Test
    void usarElCorreoDeOtroClienteEs409() throws Exception {
        insertarCliente("Beto", "Díaz", "beto@x.co");
        UUID ana = insertarCliente("Ana", "Ruiz", "ana@x.co");

        guardar(CLIENTES + "/" + ana, gestor, cuerpo("Ana", "Ruiz", "300", " BETO@x.co"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"))
                .andExpect(jsonPath("$.mensaje").value("Ya existe otro cliente con ese correo."));

        assertThat(jdbc.queryForObject("SELECT correo FROM cliente WHERE id = ?", String.class, ana))
                .isEqualTo("ana@x.co");
    }

    @Test
    void unClienteDesconocidoEs404PorqueElPanelNoCreaClientes() throws Exception {
        guardar(CLIENTES + "/" + UUID.randomUUID(), gestor, cuerpo("Ana", "Ruiz", "300", "ana@x.co"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        assertThat(contar("cliente")).isZero();
    }

    @Test
    void datosInvalidosSon400ConCampos() throws Exception {
        UUID id = insertarCliente("Ana", "Ruiz", "ana@x.co");

        guardar(CLIENTES + "/" + id, gestor, cuerpo("Ana", "Ruiz", "300", "ana@x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos.correo").isNotEmpty());
        guardar(CLIENTES + "/" + id, gestor, cuerpo(" ", "Ruiz", "300", "ana@x.co"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombre").isNotEmpty());
        guardar(CLIENTES + "/" + id, gestor, cuerpo("Ana", "Ruiz", "", "ana@x.co"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.telefono").isNotEmpty());
        guardar(CLIENTES + "/" + id, gestor, cuerpo("A".repeat(101), "Ruiz", "300", "ana@x.co"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombre").isNotEmpty());

        assertThat(jdbc.queryForObject("SELECT nombre FROM cliente WHERE id = ?", String.class, id)).isEqualTo("Ana");
    }

    @Test
    void verCitasLeeClientesPeroNoLosEdita() throws Exception {
        UUID id = insertarCliente("Ana", "Ruiz", "ana@x.co");
        UUID lector = crearUsuario("lector", crearRol("Lector", "ver_citas"));

        obtener(CLIENTES, lector).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        guardar(CLIENTES + "/" + id, lector, cuerpo("Otro", "Ruiz", "300", "ana@x.co"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        assertThat(jdbc.queryForObject("SELECT nombre FROM cliente WHERE id = ?", String.class, id)).isEqualTo("Ana");
    }

    @Test
    void sinPermisosDeClientesEs403() throws Exception {
        UUID id = insertarCliente("Ana", "Ruiz", "ana@x.co");
        UUID otro = crearUsuario("otro", crearRol("Otro", "ver_panel", "gestionar_citas", "atender_citas"));

        obtener(CLIENTES, otro).andExpect(status().isForbidden());
        guardar(CLIENTES + "/" + id, otro, cuerpo("Otro", "Ruiz", "300", "ana@x.co"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinTokenEs401() throws Exception {
        UUID id = insertarCliente("Ana", "Ruiz", "ana@x.co");

        mvc.perform(get(CLIENTES)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(put(CLIENTES + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(cuerpo("Otro", "Ruiz", "300", "ana@x.co"))))
                .andExpect(status().isUnauthorized());
    }
}

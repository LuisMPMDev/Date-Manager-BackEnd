package com.datemanager.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.datemanager.aplicacion.ServiciosServicio;
import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.Servicio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

class ServiciosControladorTest extends PruebaPanel {

    private static final String SERVICIOS = "/api/panel/servicios";

    @Autowired
    private ServiciosServicio servicios;

    private UUID admin;

    @BeforeEach
    void crearGestor() {
        admin = crearUsuario("gestor", crearRol("Gestor", "gestionar_servicios"));
    }

    private static Map<String, Object> ventana(Object id, int dia, String inicio, String fin) {
        Map<String, Object> v = new java.util.LinkedHashMap<>();
        if (id != null) {
            v.put("id", id.toString());
        }
        v.put("dia", dia);
        v.put("inicio", inicio);
        v.put("fin", fin);
        return v;
    }

    private static Map<String, Object> cuerpo(String nombre, int duracionMin, boolean activo, Object valor,
            List<Map<String, Object>> ventanas) {
        Map<String, Object> c = new java.util.LinkedHashMap<>();
        c.put("nombre", nombre);
        c.put("duracionMin", duracionMin);
        c.put("activo", activo);
        c.put("valor", valor);
        c.put("ventanas", ventanas);
        return c;
    }

    @SafeVarargs
    private static Map<String, Object> masaje(Map<String, Object>... ventanas) {
        return cuerpo("Masaje", 45, true, 50000, List.of(ventanas));
    }

    private List<String> ventanasEnBase(UUID servicioId) {
        return jdbc.queryForList("""
                SELECT CONCAT(dia, ' ', CONVERT(VARCHAR(5), hora_inicio, 108), '-', CONVERT(VARCHAR(5), hora_fin, 108))
                FROM ventana_disponibilidad WHERE servicio_id = ? ORDER BY dia, hora_inicio
                """, String.class, servicioId);
    }

    // ---------- Crear y leer ----------

    @Test
    void creaUnServicioConVentanasYLoLee() throws Exception {
        UUID id = UUID.randomUUID();
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();

        guardar(SERVICIOS + "/" + id, admin,
                masaje(ventana(v1, 1, "09:00", "12:00"), ventana(v2, 3, "14:00", "17:30")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.nombre").value("Masaje"))
                .andExpect(jsonPath("$.duracionMin").value(45))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.valor").isNumber())
                .andExpect(jsonPath("$.valor").value(50000))
                .andExpect(jsonPath("$.ventanas", hasSize(2)))
                .andExpect(jsonPath("$.ventanas[0].id").value(v1.toString()))
                .andExpect(jsonPath("$.ventanas[0].dia").value(1))
                .andExpect(jsonPath("$.ventanas[0].inicio").value("09:00"))
                .andExpect(jsonPath("$.ventanas[0].fin").value("12:00"))
                .andExpect(jsonPath("$.ventanas[1].id").value(v2.toString()))
                .andExpect(jsonPath("$.ventanas[1].inicio").value("14:00"))
                .andExpect(jsonPath("$.ventanas[1].fin").value("17:30"));

        obtener(SERVICIOS, admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].valor").value(50000))
                .andExpect(jsonPath("$[0].ventanas", hasSize(2)))
                .andExpect(jsonPath("$[0].ventanas[1].fin").value("17:30"));
        assertThat(ventanasEnBase(id)).containsExactly("1 09:00-12:00", "3 14:00-17:30");
    }

    @Test
    void unIdDesconocidoEnPutCreaElServicioConEseId() throws Exception {
        UUID id = UUID.randomUUID();

        guardar(SERVICIOS + "/" + id, admin, masaje()).andExpect(status().isOk());

        assertThat(contar("servicio")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT id FROM servicio", UUID.class)).isEqualTo(id);
    }

    @Test
    void elValorSeGuardaConDosDecimales() throws Exception {
        UUID id = UUID.randomUUID();

        guardar(SERVICIOS + "/" + id, admin, cuerpo("Corte", 30, true, 12500.5, List.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(12500.5));

        assertThat(jdbc.queryForObject("SELECT valor FROM servicio WHERE id = ?", java.math.BigDecimal.class, id))
                .isEqualByComparingTo("12500.50");
        obtener(SERVICIOS, admin).andExpect(jsonPath("$[0].valor").value(12500.5));
    }

    @Test
    void elIdDelCuerpoSeIgnoraYManda_ElDeLaRuta() throws Exception {
        UUID ruta = UUID.randomUUID();
        UUID otro = UUID.randomUUID();
        Map<String, Object> c = masaje();
        c.put("id", otro.toString());

        guardar(SERVICIOS + "/" + ruta, admin, c)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ruta.toString()));

        assertThat(contar("servicio")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT id FROM servicio", UUID.class)).isEqualTo(ruta);
    }

    @Test
    void elIdDeLaRutaQueNoEsUuidEsUn400() throws Exception {
        guardar(SERVICIOS + "/no-es-uuid", admin, masaje())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void siUnaVentanaNoTraeIdElServidorLeGeneraUno() throws Exception {
        UUID id = UUID.randomUUID();

        guardar(SERVICIOS + "/" + id, admin, masaje(ventana(null, 2, "08:00", "10:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ventanas[0].id").value(not(nullValue())));

        assertThat(contar("ventana_disponibilidad")).isEqualTo(1);
    }

    // ---------- Editar reemplaza ventanas ----------

    @Test
    void editarReemplazaLasVentanasPorLasRecibidas() throws Exception {
        UUID id = UUID.randomUUID();
        UUID conservada = UUID.randomUUID();
        UUID quitada = UUID.randomUUID();
        guardar(SERVICIOS + "/" + id, admin,
                masaje(ventana(conservada, 1, "09:00", "12:00"), ventana(quitada, 2, "09:00", "12:00")))
                .andExpect(status().isOk());
        UUID nueva = UUID.randomUUID();

        guardar(SERVICIOS + "/" + id, admin, cuerpo("Masaje relajante", 60, true, 70000, List.of(
                ventana(conservada, 1, "10:00", "13:00"), ventana(nueva, 5, "15:00", "18:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Masaje relajante"))
                .andExpect(jsonPath("$.duracionMin").value(60))
                .andExpect(jsonPath("$.ventanas", hasSize(2)))
                .andExpect(jsonPath("$.ventanas[0].id").value(conservada.toString()))
                .andExpect(jsonPath("$.ventanas[0].inicio").value("10:00"))
                .andExpect(jsonPath("$.ventanas[1].id").value(nueva.toString()));

        assertThat(contar("servicio")).isEqualTo(1);
        assertThat(ventanasEnBase(id)).containsExactly("1 10:00-13:00", "5 15:00-18:00");
        assertThat(contar("ventana_disponibilidad")).isEqualTo(2);
    }

    @Test
    void reenviarLasMismasVentanasConLosMismosIdsNoChocaConLaLlavePrimaria() throws Exception {
        UUID id = UUID.randomUUID();
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();
        Map<String, Object> c = masaje(ventana(v1, 1, "09:00", "12:00"), ventana(v2, 1, "14:00", "16:00"));

        guardar(SERVICIOS + "/" + id, admin, c).andExpect(status().isOk());
        guardar(SERVICIOS + "/" + id, admin, c).andExpect(status().isOk());
        guardar(SERVICIOS + "/" + id, admin, c).andExpect(status().isOk());

        assertThat(contar("ventana_disponibilidad")).isEqualTo(2);
        assertThat(ventanasEnBase(id)).containsExactly("1 09:00-12:00", "1 14:00-16:00");
    }

    @Test
    void cambiarElDiaDeUnaVentanaConElMismoIdLaMueve() throws Exception {
        UUID id = UUID.randomUUID();
        UUID v = UUID.randomUUID();
        guardar(SERVICIOS + "/" + id, admin, masaje(ventana(v, 1, "09:00", "12:00"))).andExpect(status().isOk());

        guardar(SERVICIOS + "/" + id, admin, masaje(ventana(v, 4, "09:00", "12:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ventanas[0].dia").value(4));

        assertThat(ventanasEnBase(id)).containsExactly("4 09:00-12:00");
    }

    @Test
    void editarConUnaListaVaciaQuitaTodasLasVentanas() throws Exception {
        UUID id = UUID.randomUUID();
        guardar(SERVICIOS + "/" + id, admin, masaje(ventana(UUID.randomUUID(), 1, "09:00", "12:00")))
                .andExpect(status().isOk());

        guardar(SERVICIOS + "/" + id, admin, masaje())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ventanas", hasSize(0)));

        assertThat(contar("ventana_disponibilidad")).isZero();
    }

    @Test
    void losMismosIdsDeVentanaEnDosServiciosNoChocanYCadaUnaPerteneceAlSuyo() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID compartida = UUID.randomUUID();

        guardar(SERVICIOS + "/" + a, admin, cuerpo("A", 30, true, 1, List.of(ventana(compartida, 1, "09:00", "10:00"))))
                .andExpect(status().isOk());
        guardar(SERVICIOS + "/" + b, admin, cuerpo("B", 30, true, 1, List.of(ventana(compartida, 2, "11:00", "12:00"))))
                .andExpect(status().isOk());

        assertThat(contar("ventana_disponibilidad")).isEqualTo(2);
        assertThat(ventanasEnBase(a)).containsExactly("1 09:00-10:00");
        assertThat(ventanasEnBase(b)).containsExactly("2 11:00-12:00");

        // Editar B no toca la ventana de A aunque compartan id.
        guardar(SERVICIOS + "/" + b, admin, cuerpo("B", 30, true, 1, List.of()))
                .andExpect(status().isOk());
        assertThat(ventanasEnBase(a)).containsExactly("1 09:00-10:00");
        assertThat(ventanasEnBase(b)).isEmpty();
        obtener(SERVICIOS, admin)
                .andExpect(jsonPath("$[0].nombre").value("A"))
                .andExpect(jsonPath("$[0].ventanas", hasSize(1)))
                .andExpect(jsonPath("$[1].nombre").value("B"))
                .andExpect(jsonPath("$[1].ventanas", hasSize(0)));
    }

    @Test
    void dosVentanasConElMismoIdEnLaMismaPeticionSonUn400() throws Exception {
        UUID id = UUID.randomUUID();
        UUID repetida = UUID.randomUUID();

        guardar(SERVICIOS + "/" + id, admin,
                masaje(ventana(repetida, 1, "09:00", "10:00"), ventana(repetida, 2, "09:00", "10:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        assertThat(contar("servicio")).isZero();
    }

    // ---------- Validación ----------

    @Test
    void unNombreEnBlancoEsUn400ConElMensajeDeValidaciones() throws Exception {
        guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, cuerpo("   ", 45, true, 100, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje").value("Escribe el nombre del servicio."));
        assertThat(contar("servicio")).isZero();
    }

    @Test
    void unaDuracionCeroONegativaEsUn400() throws Exception {
        for (int duracion : new int[] {0, -30}) {
            guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, cuerpo("Masaje", duracion, true, 100, List.of()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.mensaje")
                            .value("La duración debe ser un número entero de minutos mayor que cero."));
        }
    }

    @Test
    void unValorNegativoEsUn400() throws Exception {
        guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, cuerpo("Masaje", 45, true, -1, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje").value("El valor no puede ser negativo."));
    }

    @Test
    void unValorConMasDeDosDecimalesOQueNoCabeEnLaColumnaEsUn400() throws Exception {
        guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, cuerpo("Masaje", 45, true, 10.555, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        guardar(SERVICIOS + "/" + UUID.randomUUID(), admin,
                cuerpo("Masaje", 45, true, new java.math.BigDecimal("12345678901.00"), List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        assertThat(contar("servicio")).isZero();
    }

    @Test
    void faltanDatosObligatoriosEsUn400() throws Exception {
        guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, Map.of("nombre", "Masaje"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        assertThat(contar("servicio")).isZero();
    }

    @Test
    void unNombreMasLargoQueLaColumnaEsUn400() throws Exception {
        guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, cuerpo("x".repeat(151), 45, true, 100, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void lasVentanasCruzadasSeRechazanConElMensajeEspanol() throws Exception {
        guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, masaje(
                ventana(UUID.randomUUID(), 1, "09:00", "12:00"), ventana(UUID.randomUUID(), 1, "11:00", "14:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje").value(
                        "Lunes: las ventanas 09:00–12:00 y 11:00–14:00 se cruzan. "
                                + "Ajusta las horas para que una termine donde empieza la otra o antes."));
        assertThat(contar("servicio")).isZero();
        assertThat(contar("ventana_disponibilidad")).isZero();
    }

    @Test
    void ventanasQueSoloSeTocanSonValidas() throws Exception {
        guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, masaje(
                ventana(UUID.randomUUID(), 1, "09:00", "12:00"), ventana(UUID.randomUUID(), 1, "12:00", "14:00")))
                .andExpect(status().isOk());
    }

    @Test
    void unaVentanaConFinNoPosteriorAlInicioEsUn400() throws Exception {
        guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, masaje(ventana(UUID.randomUUID(), 2, "12:00", "12:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje").value("Martes: la hora de fin debe ser posterior a la de inicio."));
        guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, masaje(ventana(UUID.randomUUID(), 2, "13:00", "12:00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unDiaFueraDeUnoASieteEsUn400AntesDeLlegarALaBase() throws Exception {
        for (int dia : new int[] {0, 8, -1, 99}) {
            guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, masaje(ventana(UUID.randomUUID(), dia, "09:00", "10:00")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        }
        assertThat(contar("servicio")).isZero();
        assertThat(contar("ventana_disponibilidad")).isZero();
    }

    @Test
    void losDiasUnoYSieteSonValidos() throws Exception {
        guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, masaje(
                ventana(UUID.randomUUID(), 1, "09:00", "10:00"), ventana(UUID.randomUUID(), 7, "09:00", "10:00")))
                .andExpect(status().isOk());
    }

    @Test
    void unaHoraConFormatoIncorrectoOFaltanteEsUn400() throws Exception {
        for (String mala : new String[] {"9:00", "25:00", "09:00:30", "mediodía", ""}) {
            guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, masaje(ventana(UUID.randomUUID(), 1, mala, "12:00")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        }
        Map<String, Object> sinFin = ventana(UUID.randomUUID(), 1, "09:00", "10:00");
        sinFin.remove("fin");
        guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, masaje(sinFin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        assertThat(contar("servicio")).isZero();
    }

    @Test
    void unaEdicionInvalidaNoModificaNada() throws Exception {
        UUID id = UUID.randomUUID();
        guardar(SERVICIOS + "/" + id, admin, masaje(ventana(UUID.randomUUID(), 1, "09:00", "12:00")))
                .andExpect(status().isOk());

        guardar(SERVICIOS + "/" + id, admin, cuerpo("Otro", 45, true, 1, List.of(
                ventana(UUID.randomUUID(), 1, "09:00", "12:00"), ventana(UUID.randomUUID(), 1, "10:00", "11:00"))))
                .andExpect(status().isBadRequest());

        obtener(SERVICIOS, admin)
                .andExpect(jsonPath("$[0].nombre").value("Masaje"))
                .andExpect(jsonPath("$[0].ventanas", hasSize(1)));
    }

    // ---------- Desactivar ----------

    @Test
    void desactivarConservaElServicioYSusVentanasYSigueEnElListado() throws Exception {
        UUID id = UUID.randomUUID();
        UUID v = UUID.randomUUID();
        guardar(SERVICIOS + "/" + id, admin, masaje(ventana(v, 1, "09:00", "12:00"))).andExpect(status().isOk());

        guardar(SERVICIOS + "/" + id, admin,
                cuerpo("Masaje", 45, false, 50000, List.of(ventana(v, 1, "09:00", "12:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        obtener(SERVICIOS, admin)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].activo").value(false))
                .andExpect(jsonPath("$[0].nombre").value("Masaje"))
                .andExpect(jsonPath("$[0].duracionMin").value(45))
                .andExpect(jsonPath("$[0].ventanas", hasSize(1)))
                .andExpect(jsonPath("$[0].ventanas[0].id").value(v.toString()));
        assertThat(ventanasEnBase(id)).containsExactly("1 09:00-12:00");

        // Y se puede reactivar.
        guardar(SERVICIOS + "/" + id, admin,
                cuerpo("Masaje", 45, true, 50000, List.of(ventana(v, 1, "09:00", "12:00"))))
                .andExpect(jsonPath("$.activo").value(true));
    }

    // ---------- Orden ----------

    @Test
    void elListadoVaOrdenadoPorNombreYLasVentanasPorDiaYLuegoInicio() throws Exception {
        guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, cuerpo("Zumba", 30, true, 1, List.of()))
                .andExpect(status().isOk());
        guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, cuerpo("masaje", 30, true, 1, List.of(
                ventana(UUID.randomUUID(), 5, "08:00", "09:00"),
                ventana(UUID.randomUUID(), 1, "14:00", "15:00"),
                ventana(UUID.randomUUID(), 1, "09:00", "10:00"),
                ventana(UUID.randomUUID(), 3, "10:00", "11:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ventanas[*].dia").value(contains(1, 1, 3, 5)))
                .andExpect(jsonPath("$.ventanas[*].inicio").value(contains("09:00", "14:00", "10:00", "08:00")));
        guardar(SERVICIOS + "/" + UUID.randomUUID(), admin, cuerpo("Alineación", 30, true, 1, List.of()))
                .andExpect(status().isOk());

        obtener(SERVICIOS, admin)
                .andExpect(jsonPath("$[*].nombre").value(contains("Alineación", "masaje", "Zumba")))
                .andExpect(jsonPath("$[1].ventanas[*].dia").value(contains(1, 1, 3, 5)))
                .andExpect(jsonPath("$[1].ventanas[*].inicio").value(contains("09:00", "14:00", "10:00", "08:00")));
    }

    @Test
    void sinServiciosElListadoEsVacio() throws Exception {
        obtener(SERVICIOS, admin).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    // ---------- Permisos ----------

    @Test
    void verPanelPuedeLeerPeroNoEscribir() throws Exception {
        UUID id = UUID.randomUUID();
        guardar(SERVICIOS + "/" + id, admin, masaje()).andExpect(status().isOk());
        UUID lector = crearUsuario("lector", crearRol("Lector", "ver_panel"));

        obtener(SERVICIOS, lector).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));

        guardar(SERVICIOS + "/" + id, lector, cuerpo("Cambiado", 45, true, 1, List.of()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        guardar(SERVICIOS + "/" + UUID.randomUUID(), lector, masaje()).andExpect(status().isForbidden());
        assertThat(contar("servicio")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT nombre FROM servicio", String.class)).isEqualTo("Masaje");
    }

    @Test
    void verCitasYGestionarServiciosTambienPuedenLeer() throws Exception {
        UUID verCitas = crearUsuario("citas", crearRol("Citas", "ver_citas"));
        UUID soloGestionar = crearUsuario("solo", crearRol("Solo", "gestionar_servicios"));

        obtener(SERVICIOS, verCitas).andExpect(status().isOk());
        obtener(SERVICIOS, soloGestionar).andExpect(status().isOk());
        guardar(SERVICIOS + "/" + UUID.randomUUID(), verCitas, masaje()).andExpect(status().isForbidden());
    }

    @Test
    void sinPermisosDeServiciosEsUn403EnLecturaYEscritura() throws Exception {
        UUID otro = crearUsuario("otro", crearRol("Otro", "gestionar_clientes", "gestionar_usuarios", "gestionar_roles"));

        obtener(SERVICIOS, otro)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        guardar(SERVICIOS + "/" + UUID.randomUUID(), otro, masaje()).andExpect(status().isForbidden());
        assertThat(contar("servicio")).isZero();
    }

    @Test
    void sinTokenEsUn401() throws Exception {
        mvc.perform(get(SERVICIOS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(put(SERVICIOS + "/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(masaje())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        assertThat(contar("servicio")).isZero();
    }

    // ---------- ServiciosServicio.buscar ----------

    @Test
    void buscarDevuelveElServicioConSusVentanasOrdenadas() throws Exception {
        UUID id = UUID.randomUUID();
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();
        guardar(SERVICIOS + "/" + id, admin, cuerpo("Masaje", 45, false, 50000.5,
                List.of(ventana(v2, 3, "09:00", "10:00"), ventana(v1, 1, "09:00", "10:00"))))
                .andExpect(status().isOk());

        Servicio servicio = servicios.buscar(id);

        assertThat(servicio.id()).isEqualTo(id);
        assertThat(servicio.nombre()).isEqualTo("Masaje");
        assertThat(servicio.duracionMin()).isEqualTo(45);
        assertThat(servicio.activo()).isFalse();
        assertThat(servicio.valor()).isEqualByComparingTo("50000.50");
        assertThat(servicio.ventanas()).extracting(w -> w.id()).containsExactly(v1, v2);
        assertThat(servicio.ventanas().get(0).inicio()).hasToString("09:00");
    }

    @Test
    void buscarUnIdInexistenteLanzaNoEncontrado() {
        assertThatThrownBy(() -> servicios.buscar(UUID.randomUUID()))
                .isInstanceOfSatisfying(ErrorNegocio.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoError.NO_ENCONTRADO));
    }

    // ---------- Restricciones de la base ----------

    private UUID insertarServicio(String duracion, String valor) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES (?, 'S', " + duracion
                + ", 1, " + valor + ")", id);
        return id;
    }

    private void insertarVentana(UUID servicioId, int dia, String inicio, String fin) {
        jdbc.update("INSERT INTO ventana_disponibilidad (id, servicio_id, dia, hora_inicio, hora_fin) "
                + "VALUES (?, ?, ?, '" + inicio + "', '" + fin + "')", UUID.randomUUID(), servicioId, dia);
    }

    @Test
    void laBaseRechazaUnDiaFueraDeUnoASiete() {
        UUID servicio = insertarServicio("30", "0");

        assertThatThrownBy(() -> insertarVentana(servicio, 8, "09:00", "10:00"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarVentana(servicio, 0, "09:00", "10:00"))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertarVentana(servicio, 1, "09:00", "10:00");
        insertarVentana(servicio, 7, "09:00", "10:00");
    }

    @Test
    void laBaseRechazaUnaVentanaConFinNoPosteriorAlInicio() {
        UUID servicio = insertarServicio("30", "0");

        assertThatThrownBy(() -> insertarVentana(servicio, 1, "10:00", "10:00"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarVentana(servicio, 1, "11:00", "10:00"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laBaseRechazaDuracionNoPositivaYValorNegativo() {
        assertThatThrownBy(() -> insertarServicio("0", "0")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarServicio("-5", "0")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarServicio("30", "-0.01")).isInstanceOf(DataIntegrityViolationException.class);
        insertarServicio("30", "0");
    }

    @Test
    void laBaseRechazaUnaVentanaDeUnServicioInexistente() {
        assertThatThrownBy(() -> insertarVentana(UUID.randomUUID(), 1, "09:00", "10:00"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void borrarUnServicioEnLaBaseBorraSusVentanasEnCascada() {
        UUID servicio = insertarServicio("30", "0");
        insertarVentana(servicio, 1, "09:00", "10:00");
        insertarVentana(servicio, 2, "09:00", "10:00");

        jdbc.update("DELETE FROM servicio WHERE id = ?", servicio);

        assertThat(contar("ventana_disponibilidad")).isZero();
    }

    @Test
    void lasVentanasLeidasDeLaBaseSeVenEnLaApi() throws Exception {
        UUID servicio = insertarServicio("30", "100");
        insertarVentana(servicio, 6, "10:30", "11:30");

        obtener(SERVICIOS, admin)
                .andExpect(jsonPath("$[0].ventanas[0].inicio").value("10:30"))
                .andExpect(jsonPath("$[0].ventanas[0].fin").value("11:30"))
                .andExpect(jsonPath("$[0].ventanas[0].dia").value(6));
    }
}

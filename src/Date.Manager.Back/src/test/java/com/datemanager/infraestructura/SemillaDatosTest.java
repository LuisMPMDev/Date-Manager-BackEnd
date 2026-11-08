package com.datemanager.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.datemanager.PruebaIntegracion;
import com.datemanager.dominio.Cita;
import com.datemanager.dominio.EstadoCita;
import com.datemanager.dominio.Permiso;
import com.datemanager.dominio.Servicio;
import com.datemanager.dominio.VentanaDisponibilidad;
import com.datemanager.infraestructura.persistencia.CitaEntidad;
import com.datemanager.infraestructura.persistencia.CitaRepositorio;
import com.datemanager.infraestructura.persistencia.ClienteRepositorio;
import com.datemanager.infraestructura.persistencia.EnlaceRepositorio;
import com.datemanager.infraestructura.persistencia.RolRepositorio;
import com.datemanager.infraestructura.persistencia.ServicioEntidad;
import com.datemanager.infraestructura.persistencia.ServicioRepositorio;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio;
import com.datemanager.infraestructura.persistencia.VentanaEntidad;
import com.datemanager.infraestructura.persistencia.VentanaRepositorio;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

/** Datos de ejemplo del perfil dev: se prueban aquí con el perfil test, construyendo la semilla a mano. */
class SemillaDatosTest extends PruebaIntegracion {

    /** Lunes 9 de noviembre de 2026, 10:00 en Bogotá. */
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 9, 10, 0);

    @Autowired
    private RolRepositorio roles;
    @Autowired
    private UsuarioRepositorio usuarios;
    @Autowired
    private ServicioRepositorio servicios;
    @Autowired
    private VentanaRepositorio ventanas;
    @Autowired
    private ClienteRepositorio clientes;
    @Autowired
    private CitaRepositorio citas;
    @Autowired
    private EnlaceRepositorio enlaces;
    @Autowired
    private PasswordEncoder codificador;
    @Autowired
    private PlatformTransactionManager transacciones;
    @Autowired
    private ApplicationContext contexto;
    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void fijarReloj() {
        fijarAhora(AHORA);
    }

    private SemillaDatos semilla() {
        return new SemillaDatos(roles, usuarios, servicios, ventanas, clientes, citas, enlaces, codificador,
                transacciones, reloj());
    }

    private int contar(String tabla) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
    }

    private String entrar(String usuario, String clave) throws Exception {
        String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("usuario", usuario, "clave", clave))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("token").asText();
    }

    // ---------- Qué se crea ----------

    @Test
    void enUnaBaseVaciaCreaRolesUsuariosServiciosClientesYCitas() {
        assertThat(semilla().sembrarSiVacia()).isTrue();

        assertThat(contar("rol")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT nombre FROM rol", String.class))
                .containsExactlyInAnyOrder("Administrador", "Recepción");
        assertThat(jdbc.queryForList("SELECT usuario FROM usuario", String.class))
                .containsExactlyInAnyOrder("marta", "andres");
        assertThat(contar("servicio")).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT duracion_min FROM servicio", Integer.class))
                .containsExactlyInAnyOrder(30, 60, 90);
        assertThat(contar("ventana_disponibilidad")).isEqualTo(25);
        assertThat(contar("cliente")).isEqualTo(8);
        assertThat(contar("cita")).isEqualTo(23);
        assertThat(contar("enlace_invitacion")).isZero();
    }

    @Test
    void elRolAdministradorTieneTodosLosPermisosYRecepcionSoloLosDeLaAgenda() {
        semilla().sembrarSiVacia();

        assertThat(permisosDe("Administrador")).containsExactlyInAnyOrder(
                Arrays.stream(Permiso.values()).map(Permiso::id).toArray(String[]::new));
        assertThat(permisosDe("Recepción")).containsExactlyInAnyOrder(
                "ver_citas", "gestionar_citas", "atender_citas", "gestionar_clientes");
    }

    private List<String> permisosDe(String rol) {
        return jdbc.queryForList("""
                SELECT p.permiso FROM rol_permiso p JOIN rol r ON r.id = p.rol_id WHERE r.nombre = ?
                """, String.class, rol);
    }

    @Test
    void laClaveDeEjemploSoloSeGuardaConHashYPermiteEntrar() throws Exception {
        semilla().sembrarSiVacia();

        for (String hash : jdbc.queryForList("SELECT clave_hash FROM usuario", String.class)) {
            assertThat(hash).startsWith("$2").doesNotContain("demo123");
            assertThat(codificador.matches("demo123", hash)).isTrue();
        }
        assertThat(entrar("marta", "demo123")).isNotBlank();
        assertThat(entrar("andres", "demo123")).isNotBlank();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"marta\",\"clave\":\"otra\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void martaEsAdministradoraYAndresRecepcion() {
        semilla().sembrarSiVacia();

        assertThat(jdbc.queryForObject("""
                SELECT r.nombre FROM usuario u JOIN rol r ON r.id = u.rol_id WHERE u.usuario = 'marta'
                """, String.class)).isEqualTo("Administrador");
        assertThat(jdbc.queryForObject("""
                SELECT r.nombre FROM usuario u JOIN rol r ON r.id = u.rol_id WHERE u.usuario = 'andres'
                """, String.class)).isEqualTo("Recepción");
    }

    // ---------- Horarios libres por la API real ----------

    @Test
    void cadaServicioSembradoTieneHorariosLibresPorElEndpointDelPanel() throws Exception {
        semilla().sembrarSiVacia();
        String token = entrar("marta", "demo123");

        for (UUID servicio : jdbc.queryForList("SELECT id FROM servicio", UUID.class)) {
            boolean algunoLibre = false;
            for (int dias = 0; dias <= 14 && !algunoLibre; dias++) {
                String fecha = AHORA.toLocalDate().plusDays(dias).toString();
                String cuerpo = mvc.perform(get("/api/panel/servicios/" + servicio + "/horarios")
                                .param("fecha", fecha).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                algunoLibre = json.readTree(cuerpo).size() > 0;
            }
            assertThat(algunoLibre).as("servicio %s con horarios libres en 15 días", servicio).isTrue();
        }
    }

    // ---------- Idempotencia y «solo si la base está vacía» ----------

    @Test
    void unaSegundaEjecucionNoDuplicaNada() {
        assertThat(semilla().sembrarSiVacia()).isTrue();
        Map<String, Integer> antes = conteos();

        assertThat(semilla().sembrarSiVacia()).isFalse();

        assertThat(conteos()).isEqualTo(antes);
    }

    @Test
    void conUnaBaseConDatosPropiosNoSiembraNada() {
        UUID rolId = UUID.randomUUID();
        jdbc.update("INSERT INTO rol (id, nombre, descripcion) VALUES (?, 'Mío', '')", rolId);

        assertThat(semilla().sembrarSiVacia()).isFalse();

        assertThat(conteos().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(1);
    }

    @Test
    void conUnClienteSueltoTampocoSiembra() {
        jdbc.update("INSERT INTO cliente (id, nombre, apellido, telefono, correo) VALUES (?, 'A', 'B', '1', 'a@b.co')",
                UUID.randomUUID());

        assertThat(semilla().sembrarSiVacia()).isFalse();

        assertThat(contar("servicio")).isZero();
        assertThat(contar("usuario")).isZero();
    }

    @Test
    void convivePorOrdenConElAdministradorInicial() {
        // La semilla corre primero: crea marta y andres, y el administrador de DATEMANAGER_ADMIN_* ya no hace falta.
        semilla().sembrarSiVacia();
        new AdministradorInicial(roles, usuarios, codificador, transacciones, "admin", "clave-de-prueba-123")
                .crearSiHaceFalta();

        assertThat(contar("usuario")).isEqualTo(2);
        assertThat(contar("rol")).isEqualTo(2);
    }

    @Test
    void siElAdministradorInicialYaExistiaLaSemillaNoLoToca() {
        new AdministradorInicial(roles, usuarios, codificador, transacciones, "marta", "clave-de-prueba-123")
                .crearSiHaceFalta();

        assertThat(semilla().sembrarSiVacia()).isFalse();

        assertThat(contar("usuario")).isEqualTo(1);
        assertThat(contar("servicio")).isZero();
    }

    private Map<String, Integer> conteos() {
        return List.of("rol", "rol_permiso", "usuario", "servicio", "ventana_disponibilidad", "cliente", "cita",
                        "enlace_invitacion").stream()
                .collect(Collectors.toMap(t -> t, this::contar));
    }

    // ---------- Solo en el perfil dev ----------

    @Test
    void estaMarcadaSoloParaElPerfilDevYNoExisteEnElContextoDePrueba() {
        Profile perfil = SemillaDatos.class.getAnnotation(Profile.class);
        assertThat(perfil).isNotNull();
        assertThat(perfil.value()).containsExactly("dev");

        assertThat(contexto.getEnvironment().getActiveProfiles()).doesNotContain("dev");
        assertThat(contexto.getBeanNamesForType(SemillaDatos.class)).isEmpty();
        // Al arrancar el contexto de prueba no sembró nada (la base la vacía PruebaIntegracion antes de cada prueba).
        assertThat(contar("usuario")).isZero();
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

                assertThat(semilla().sembrarSiVacia()).as("sembró a las %s", ahora).isTrue();

                verificarReglas(ahora);
            }
        }
    }

    private void verificarReglas(LocalDateTime ahora) {
        List<Servicio> catalogo = new ArrayList<>();
        for (ServicioEntidad s : servicios.findAll()) {
            List<VentanaDisponibilidad> propias = ventanas.findByServicioIdOrderByDiaAscInicioAscIdAsc(s.getId())
                    .stream().map(VentanaEntidad::aDominio).toList();
            catalogo.add(s.aDominio(propias));
        }
        List<Cita> todas = citas.findAll().stream().map(CitaEntidad::aDominio).toList();
        assertThat(todas).as("citas a las %s", ahora).hasSize(23);

        for (Cita c : todas) {
            String quien = "cita %s %s %s %s a las %s".formatted(c.estado(), c.servicioId(), c.fecha(), c.inicio(), ahora);
            Servicio s = catalogo.stream().filter(x -> x.id().equals(c.servicioId())).findFirst().orElseThrow();
            assertThat(c.fin()).as(quien).isEqualTo(c.inicio().plusMinutes(s.duracionMin()));
            // dentro de una ventana de ese día y alineada al paso de 30 minutos desde su inicio
            assertThat(s.ventanas().stream().anyMatch(v -> v.dia() == c.fecha().getDayOfWeek().getValue()
                    && !c.inicio().isBefore(v.inicio()) && !c.fin().isAfter(v.fin())
                    && Duration.between(v.inicio(), c.inicio()).toMinutes() % 30 == 0)).as(quien).isTrue();

            LocalDateTime empieza = c.fecha().atTime(c.inicio());
            LocalDateTime termina = c.fecha().atTime(c.fin());
            if (c.estado() == EstadoCita.Atendida) {
                assertThat(termina).as(quien).isBeforeOrEqualTo(ahora);
            }
            if (c.estado() == EstadoCita.Confirmada) {
                assertThat(empieza).as(quien).isAfterOrEqualTo(ahora);
                assertThat(c.fecha()).as(quien).isBeforeOrEqualTo(ahora.toLocalDate().plusDays(30));
            }
            assertThat(c.fecha()).as(quien).isAfterOrEqualTo(ahora.toLocalDate().minusDays(30));
            assertThat(c.creadaEn()).as(quien).isBeforeOrEqualTo(reloj().instant());
            if (c.estado() == EstadoCita.Cancelada) {
                assertThat(c.canceladaEn()).as(quien).isNotNull().isAfterOrEqualTo(c.creadaEn())
                        .isBeforeOrEqualTo(reloj().instant());
            } else {
                assertThat(c.canceladaEn()).as(quien).isNull();
            }
        }

        // sin solapes por servicio y día (las canceladas también se esquivan: así ninguna se cruza con otra)
        for (int i = 0; i < todas.size(); i++) {
            for (int j = i + 1; j < todas.size(); j++) {
                Cita a = todas.get(i);
                Cita b = todas.get(j);
                if (a.servicioId().equals(b.servicioId()) && a.fecha().equals(b.fecha())) {
                    assertThat(a.inicio().isBefore(b.fin()) && b.inicio().isBefore(a.fin()))
                            .as("solape %s/%s %s %s-%s con %s-%s", a.servicioId(), a.fecha(), a.estado(), a.inicio(),
                                    a.fin(), b.inicio(), b.fin()).isFalse();
                }
            }
        }
        assertThat(todas.stream().map(Cita::estado).distinct().toList())
                .containsExactlyInAnyOrder(EstadoCita.Atendida, EstadoCita.Confirmada, EstadoCita.Cancelada);
        assertThat(todas.stream().map(Cita::fecha).min(LocalDate::compareTo).orElseThrow())
                .isBefore(ahora.toLocalDate());
    }
}

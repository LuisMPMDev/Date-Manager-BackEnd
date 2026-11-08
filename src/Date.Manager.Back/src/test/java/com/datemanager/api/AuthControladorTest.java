package com.datemanager.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.datemanager.PruebaIntegracion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

class AuthControladorTest extends PruebaIntegracion {

    private static final String LOGIN = "/api/auth/login";
    private static final String YO = "/api/auth/yo";
    private static final String PROTEGIDA = "/api/panel/_prueba/citas";

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private RespuestasSeguridad respuestasSeguridad;

    private UUID rolId;
    private UUID usuarioId;

    @BeforeEach
    void crearUsuario() {
        rolId = UUID.randomUUID();
        jdbc.update("INSERT INTO rol (id, nombre, descripcion) VALUES (?, ?, ?)", rolId, "Recepción", "Agenda");
        // Se insertan en desorden para comprobar que la respuesta los ordena según el catálogo.
        jdbc.update("INSERT INTO rol_permiso (rol_id, permiso) VALUES (?, 'ver_citas')", rolId);
        jdbc.update("INSERT INTO rol_permiso (rol_id, permiso) VALUES (?, 'ver_panel')", rolId);
        usuarioId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuario (id, nombre, apellido, correo, telefono, usuario, clave_hash, rol_id)
                VALUES (?, 'Marta', 'Gómez', 'marta@example.com', '300 555 0201', 'marta', ?, ?)
                """, usuarioId, codificador.encode("demo123"), rolId);
    }

    private ResultActions login(String usuario, String clave) throws Exception {
        String cuerpo = json.writeValueAsString(Map.of("usuario", usuario, "clave", clave));
        return mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private String token() throws Exception {
        String respuesta = login("marta", "demo123").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode nodo = json.readTree(respuesta);
        return nodo.get("token").asText();
    }

    private ResultActions conToken(String ruta, String token) throws Exception {
        return mvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void loginCorrectoDevuelveTokenUsuarioSinClaveYPermisosDelRolEnOrdenDelCatalogo() throws Exception {
        login("marta", "demo123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario.id").value(usuarioId.toString()))
                .andExpect(jsonPath("$.usuario.nombre").value("Marta"))
                .andExpect(jsonPath("$.usuario.apellido").value("Gómez"))
                .andExpect(jsonPath("$.usuario.correo").value("marta@example.com"))
                .andExpect(jsonPath("$.usuario.telefono").value("300 555 0201"))
                .andExpect(jsonPath("$.usuario.usuario").value("marta"))
                .andExpect(jsonPath("$.usuario.rolId").value(rolId.toString()))
                .andExpect(jsonPath("$.usuario.clave").doesNotExist())
                .andExpect(jsonPath("$.usuario.claveHash").doesNotExist())
                .andExpect(jsonPath("$.rol.id").value(rolId.toString()))
                .andExpect(jsonPath("$.rol.nombre").value("Recepción"))
                .andExpect(jsonPath("$.rol.descripcion").value("Agenda"))
                .andExpect(jsonPath("$.rol.permisos").value(contains("ver_panel", "ver_citas")))
                .andExpect(content().string(not(containsString("$2"))));
    }

    @Test
    void elUsuarioConEspaciosYMayusculasEntraIgual() throws Exception {
        login(" MARTA ", "demo123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.id").value(usuarioId.toString()));
    }

    @Test
    void laClaveEsExacta() throws Exception {
        login("marta", "DEMO123").andExpect(status().isUnauthorized());
        login("marta", " demo123").andExpect(status().isUnauthorized());
    }

    @Test
    void claveMalaYUsuarioInexistenteRespondenElMismo401() throws Exception {
        String claveMala = login("marta", "otra").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String inexistente = login("nadie", "demo123").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(inexistente).isEqualTo(claveMala);
    }

    @Test
    void laClaveSeGuardaComoHashBcrypt() {
        String guardada = jdbc.queryForObject("SELECT clave_hash FROM usuario WHERE id = ?", String.class, usuarioId);

        assertThat(guardada).startsWith("$2").isNotEqualTo("demo123");
    }

    @Test
    void sinTokenResponde401NoAutenticado() throws Exception {
        mvc.perform(get(PROTEGIDA))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
        mvc.perform(get(YO)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void unTokenInvalidoResponde401NoAutenticado() throws Exception {
        conToken(PROTEGIDA, "no.es.un-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        String manipulado = token() + "x";
        conToken(PROTEGIDA, manipulado)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void conTokenYPermisoLaRutaProtegidaResponde() throws Exception {
        conToken(PROTEGIDA, token()).andExpect(status().isOk());
    }

    @Test
    void bastaUnoDeLosPermisosDeLaLista() throws Exception {
        conToken("/api/panel/_prueba/cualquiera", token()).andExpect(status().isOk());
    }

    @Test
    void yoDevuelveUsuarioYRolSinTokenNuevo() throws Exception {
        conToken(YO, token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.usuario.id").value(usuarioId.toString()))
                .andExpect(jsonPath("$.usuario.clave").doesNotExist())
                .andExpect(jsonPath("$.rol.permisos").value(contains("ver_panel", "ver_citas")));
    }

    @Test
    void elTokenVenceALos60MinutosSegunElRelojDelContexto() throws Exception {
        LocalDateTime inicio = LocalDateTime.of(2026, 11, 8, 9, 0);
        fijarAhora(inicio);
        String token = token();

        fijarAhora(inicio.plusMinutes(59));
        conToken(PROTEGIDA, token).andExpect(status().isOk());

        fijarAhora(inicio.plusMinutes(61));
        conToken(PROTEGIDA, token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void unUsuarioEliminadoTrasEmitirElTokenRecibe401() throws Exception {
        String token = token();
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);

        conToken(PROTEGIDA, token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        conToken(YO, token).andExpect(status().isUnauthorized());
    }

    @Test
    void quitarleUnPermisoAlRolDa403EnLaSiguientePeticionSinVolverAEntrar() throws Exception {
        String token = token();
        conToken(PROTEGIDA, token).andExpect(status().isOk());

        jdbc.update("DELETE FROM rol_permiso WHERE rol_id = ? AND permiso = 'ver_citas'", rolId);

        conToken(PROTEGIDA, token)
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    @Test
    void unaExcepcionDeAutenticacionDentroDeUnControladorEs401NoUn500() throws Exception {
        conToken("/api/panel/_prueba/no-autenticado", token())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void elManejadorDeAccesoDenegadoDeLaCadenaDeFiltrosResponde403SinPermiso() throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        respuestasSeguridad.handle(new MockHttpServletRequest(), respuesta, new AccessDeniedException("x"));

        assertThat(respuesta.getStatus()).isEqualTo(403);
        assertThat(respuesta.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        JsonNode cuerpo = json.readTree(respuesta.getContentAsByteArray());
        assertThat(cuerpo.get("codigo").asText()).isEqualTo("SIN_PERMISO");
        assertThat(cuerpo.get("mensaje").asText()).isNotBlank();
    }

    @Test
    void lasRutasPublicasDePruebaNoPidenToken() throws Exception {
        mvc.perform(get("/api/publico/_prueba/error-negocio/CONFLICTO"))
                .andExpect(status().isConflict());
    }

    @Test
    void corsPermiteElOrigenConfiguradoYNoOtro() throws Exception {
        mvc.perform(options(LOGIN)
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
        mvc.perform(post(LOGIN).header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"usuario\":\"marta\",\"clave\":\"demo123\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));

        mvc.perform(options(LOGIN)
                        .header(HttpHeaders.ORIGIN, "http://malicioso.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        mvc.perform(post(LOGIN).header(HttpHeaders.ORIGIN, "http://malicioso.example")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"usuario\":\"marta\",\"clave\":\"demo123\"}"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}

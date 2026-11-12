package com.datemanager.infraestructura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ConfiguracionTest {

    private static final String SECRETO = "secreto-de-prueba-con-mas-de-32-bytes!";

    private static Map<String, String> completo() {
        Map<String, String> entorno = new HashMap<>();
        entorno.put("DATEMANAGER_DB_URL", "jdbc:sqlserver://servidor-x:1433;databaseName=bd-x");
        entorno.put("DATEMANAGER_DB_USUARIO", "usuario-bd-x");
        entorno.put("DATEMANAGER_DB_CLAVE", "clave-bd-x");
        entorno.put("DATEMANAGER_JWT_SECRETO", SECRETO);
        entorno.put("DATEMANAGER_ADMIN_USUARIO", "admin-x");
        entorno.put("DATEMANAGER_ADMIN_CLAVE", "clave-admin-x");
        return entorno;
    }

    @Test
    void variablesCompletasConLosValoresPorDefecto() {
        Configuracion c = Configuracion.desdeEntorno(completo());

        assertEquals(new Configuracion(
                "jdbc:sqlserver://servidor-x:1433;databaseName=bd-x", "usuario-bd-x", "clave-bd-x", 10,
                SECRETO, ZoneId.of("America/Bogota"), List.of("http://localhost:4200"),
                "admin-x", "clave-admin-x", false, 8080), c);
    }

    @Test
    void elAdministradorInicialEsOpcional() {
        Map<String, String> entorno = completo();
        entorno.remove("DATEMANAGER_ADMIN_USUARIO");
        entorno.remove("DATEMANAGER_ADMIN_CLAVE");

        Configuracion c = Configuracion.desdeEntorno(entorno);

        assertEquals("", c.adminUsuario());
        assertEquals("", c.adminClave());
    }

    @Test
    void losOrigenesCorsSeSeparanPorComasSinVacios() {
        Map<String, String> entorno = completo();
        entorno.put("DATEMANAGER_CORS_ORIGENES", "a, b,,");

        assertEquals(List.of("a", "b"), Configuracion.desdeEntorno(entorno).corsOrigenes());
    }

    @Test
    void elPerfilDevActivaElModoDesarrollo() {
        Map<String, String> entorno = completo();
        entorno.put("DATEMANAGER_PERFIL", "dev");

        assertTrue(Configuracion.desdeEntorno(entorno).dev());
    }

    @Test
    void otroPerfilNoActivaElModoDesarrollo() {
        Map<String, String> entorno = completo();
        entorno.put("DATEMANAGER_PERFIL", "prod");

        assertFalse(Configuracion.desdeEntorno(entorno).dev());
    }

    @Test
    void zonaPoolYPuertoSePuedenConfigurar() {
        Map<String, String> entorno = completo();
        entorno.put("DATEMANAGER_ZONA_HORARIA", "Europe/Madrid");
        entorno.put("DATEMANAGER_DB_POOL", "25");
        entorno.put("DATEMANAGER_PUERTO", "9090");

        Configuracion c = Configuracion.desdeEntorno(entorno);

        assertEquals(ZoneId.of("Europe/Madrid"), c.zona());
        assertEquals(25, c.dbPool());
        assertEquals(9090, c.puerto());
    }

    @Test
    void siFaltaUnaVariableObligatoriaElMensajeDaSuNombreYNingunValor() {
        for (String falta : List.of("DATEMANAGER_DB_URL", "DATEMANAGER_DB_USUARIO", "DATEMANAGER_DB_CLAVE",
                "DATEMANAGER_JWT_SECRETO")) {
            Map<String, String> entorno = completo();
            entorno.remove(falta);

            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> Configuracion.desdeEntorno(entorno));

            assertTrue(e.getMessage().contains(falta), e.getMessage());
            entorno.values().forEach(valor -> assertFalse(e.getMessage().contains(valor),
                    "el mensaje no debe contener ningún valor: " + e.getMessage()));
        }
    }

    @Test
    void unaVariableObligatoriaEnBlancoCuentaComoFaltante() {
        Map<String, String> entorno = completo();
        entorno.put("DATEMANAGER_DB_URL", "   ");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> Configuracion.desdeEntorno(entorno));

        assertTrue(e.getMessage().contains("DATEMANAGER_DB_URL"));
    }

    @Test
    void unSecretoDe31BytesSeRechaza() {
        Map<String, String> entorno = completo();
        String corto = "x".repeat(31);
        entorno.put("DATEMANAGER_JWT_SECRETO", corto);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> Configuracion.desdeEntorno(entorno));

        assertTrue(e.getMessage().contains("DATEMANAGER_JWT_SECRETO"));
        assertTrue(e.getMessage().contains("menos de 32 bytes"));
        assertFalse(e.getMessage().contains(corto));
    }

    @Test
    void unSecretoDe32BytesSeAcepta() {
        Map<String, String> entorno = completo();
        entorno.put("DATEMANAGER_JWT_SECRETO", "x".repeat(32));

        assertEquals("x".repeat(32), Configuracion.desdeEntorno(entorno).jwtSecreto());
    }

    @Test
    void losBytesDelSecretoSeCuentanEnUtf8() {
        Map<String, String> entorno = completo();
        // 16 caracteres de 2 bytes en UTF-8 = 32 bytes: se acepta; 15 son 30 bytes: se rechaza.
        entorno.put("DATEMANAGER_JWT_SECRETO", "ñ".repeat(16));
        assertEquals(16, Configuracion.desdeEntorno(entorno).jwtSecreto().length());

        entorno.put("DATEMANAGER_JWT_SECRETO", "ñ".repeat(15));
        assertThrows(IllegalStateException.class, () -> Configuracion.desdeEntorno(entorno));
    }

    @Test
    void unPoolCeroNegativoONoNumericoSeRechaza() {
        for (String pool : List.of("0", "abc", "-3", "201", "100000")) {
            Map<String, String> entorno = completo();
            entorno.put("DATEMANAGER_DB_POOL", pool);

            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> Configuracion.desdeEntorno(entorno), pool);
            assertTrue(e.getMessage().contains("DATEMANAGER_DB_POOL"));
        }
    }

    @Test
    void elPoolAceptaDeUnoADoscientas() {
        for (String pool : List.of("1", "200")) {
            Map<String, String> entorno = completo();
            entorno.put("DATEMANAGER_DB_POOL", pool);

            assertEquals(Integer.parseInt(pool), Configuracion.desdeEntorno(entorno).dbPool());
        }
    }

    @Test
    void unaListaDeOrigenesCorsSinNingunOrigenSeRechaza() {
        for (String origenes : List.of(",,", " , ")) {
            Map<String, String> entorno = completo();
            entorno.put("DATEMANAGER_CORS_ORIGENES", origenes);

            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> Configuracion.desdeEntorno(entorno), origenes);
            assertTrue(e.getMessage().contains("DATEMANAGER_CORS_ORIGENES"), e.getMessage());
        }
    }

    @Test
    void unPuertoInvalidoOUnaZonaInvalidaSeRechazan() {
        for (String[] par : new String[][] {
                {"DATEMANAGER_PUERTO", "abc"}, {"DATEMANAGER_PUERTO", "0"}, {"DATEMANAGER_PUERTO", "70000"},
                {"DATEMANAGER_ZONA_HORARIA", "No/Existe"}}) {
            Map<String, String> entorno = completo();
            entorno.put(par[0], par[1]);

            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> Configuracion.desdeEntorno(entorno), par[0] + "=" + par[1]);
            assertTrue(e.getMessage().contains(par[0]));
        }
    }

    @Test
    void elTextoDelRecordNoRevelaSecretos() {
        String texto = Configuracion.desdeEntorno(completo()).toString();

        assertFalse(texto.contains(SECRETO));
        assertFalse(texto.contains("clave-bd-x"));
        assertFalse(texto.contains("clave-admin-x"));
    }
}

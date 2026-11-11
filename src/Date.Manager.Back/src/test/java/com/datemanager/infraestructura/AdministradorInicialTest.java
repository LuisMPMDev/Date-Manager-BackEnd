package com.datemanager.infraestructura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.datemanager.CapturaRegistro;
import com.datemanager.PruebaIntegracion;
import com.datemanager.dominio.Permiso;
import com.datemanager.infraestructura.persistencia.RolRepositorio;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio;
import com.datemanager.infraestructura.seguridad.Bcrypt;
import org.junit.jupiter.api.Test;

class AdministradorInicialTest extends PruebaIntegracion {

    private static final String CLAVE = "clave-de-prueba-123";

    private final Bcrypt codificador = new Bcrypt();

    private AdministradorInicial administrador(String usuario, String clave) {
        return new AdministradorInicial(tx, new RolRepositorio(tx), new UsuarioRepositorio(tx), codificador, usuario,
                clave);
    }

    private static int contar(String tabla) {
        return consultarInt("SELECT COUNT(*) FROM " + tabla);
    }

    @Test
    void sinUsuariosYConLasVariablesCreaElRolAdministradorConTodosLosPermisosYSuUsuario() {
        administrador("  Admin ", CLAVE).crearSiHaceFalta();

        assertEquals(1, contar("rol"));
        assertEquals("Administrador", consultarTexto("SELECT nombre FROM rol"));
        List<String> permisos = columna("SELECT permiso FROM rol_permiso ORDER BY permiso");
        assertEquals(Arrays.stream(Permiso.values()).map(Permiso::id).sorted().toList(), permisos);
        assertEquals(9, permisos.size());

        assertEquals(1, contar("usuario"));
        assertEquals("admin", consultarTexto("SELECT usuario FROM usuario"));
        String hash = consultarTexto("SELECT clave_hash FROM usuario");
        assertTrue(hash.startsWith("$2"), hash);
        assertTrue(codificador.coincide(CLAVE, hash));
        UUID rolDelUsuario = UUID.fromString(consultarTexto("SELECT CAST(rol_id AS NVARCHAR(36)) FROM usuario"));
        assertEquals(UUID.fromString(consultarTexto("SELECT CAST(id AS NVARCHAR(36)) FROM rol")), rolDelUsuario);
    }

    @Test
    void conUsuariosExistentesNoCreaNada() {
        UUID rolId = UUID.randomUUID();
        ejecutar("INSERT INTO rol (id, nombre, descripcion) VALUES (?, N'Recepción', '')", rolId);
        ejecutar("""
                INSERT INTO usuario (id, nombre, apellido, correo, telefono, usuario, clave_hash, rol_id)
                VALUES (?, 'Ana', '', '', '', 'ana', ?, ?)
                """, UUID.randomUUID(), codificador.codificar("x"), rolId);

        administrador("admin", CLAVE).crearSiHaceFalta();

        assertEquals(1, contar("rol"));
        assertEquals(0, contar("rol_permiso"));
        assertEquals(1, contar("usuario"));
    }

    @Test
    void sinVariablesArrancaSinCrearNadaYRegistraUnAviso() {
        String salida;
        try (CapturaRegistro registro = new CapturaRegistro(AdministradorInicial.class, true)) {
            administrador("", "").crearSiHaceFalta();
            administrador(null, null).crearSiHaceFalta();
            administrador("admin", " ").crearSiHaceFalta();
            salida = registro.texto();
        }

        assertEquals(0, contar("rol"));
        assertEquals(0, contar("usuario"));
        assertTrue(salida.contains("WARN"), salida);
        assertTrue(salida.contains("DATEMANAGER_ADMIN_USUARIO"), salida);
    }

    @Test
    void elAvisoNoMuestraLaClave() {
        String salida;
        try (CapturaRegistro registro = new CapturaRegistro(AdministradorInicial.class, true)) {
            administrador("", CLAVE).crearSiHaceFalta();
            salida = registro.texto();
        }

        assertFalse(salida.isEmpty());
        assertFalse(salida.contains(CLAVE));
    }

    @Test
    void unaClaveDeMasDe72BytesFallaComoEnSpringSinCrearNadaNiMostrarLaClave() {
        // Spring rechazaba al codificar una clave de más de 72 bytes (y la aplicación no arrancaba).
        String larga = "ñ".repeat(37); // 74 bytes en UTF-8
        String salida;
        IllegalArgumentException e;
        try (CapturaRegistro registro = new CapturaRegistro(AdministradorInicial.class, true)) {
            e = assertThrows(IllegalArgumentException.class, () -> administrador("admin", larga).crearSiHaceFalta());
            salida = registro.texto();
        }

        assertFalse(String.valueOf(e.getMessage()).contains(larga));
        assertFalse(salida.contains(larga));
        assertEquals(0, contar("rol"));
        assertEquals(0, contar("usuario"));
    }

    @Test
    void lasPruebasNoLeenElAdministradorNiLosOrigenesDelEntornoReal() {
        // Valores efectivos: los de PruebaIntegracion, aunque existan DATEMANAGER_ADMIN_* o
        // DATEMANAGER_CORS_ORIGENES en el entorno (la configuración de las pruebas se escribe a mano).
        Configuracion cfg = configuracion();
        assertTrue(cfg.adminUsuario().isEmpty());
        assertTrue(cfg.adminClave().isEmpty());
        assertEquals(List.of(ORIGEN_CORS), cfg.corsOrigenes());
        assertTrue(cfg.jwtSecreto().startsWith("secreto-falso-solo-para-pruebas"));
        assertFalse(cfg.dev());
    }
}

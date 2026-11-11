package com.datemanager.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import com.datemanager.PruebaAdministracion;
import com.datemanager.RelojMutable;
import com.datemanager.aplicacion.AutenticacionServicio.Sesion;
import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.Permiso;
import com.datemanager.dominio.Rol;
import com.datemanager.dominio.Usuario;
import com.datemanager.infraestructura.seguridad.Bcrypt;
import com.datemanager.infraestructura.seguridad.ServicioJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Inicio de sesión y «yo», con el BCrypt y el JWT reales (portado de las pruebas del controlador de autenticación). */
class AutenticacionServicioTest extends PruebaAdministracion {

    private static final String SECRETO = "secreto-falso-solo-para-pruebas-0123456789abcdef";

    private RelojMutable reloj;
    private Bcrypt bcrypt;
    private ServicioJwt jwt;
    private AutenticacionServicio autenticacion;
    private UUID rolId;
    private UUID usuarioId;

    @BeforeEach
    void crearUsuarioYServicio() {
        reloj = new RelojMutable(ZoneId.of("America/Bogota"));
        bcrypt = new Bcrypt();
        jwt = new ServicioJwt(SECRETO, reloj);
        autenticacion = new AutenticacionServicio(tx, usuarios, roles, bcrypt, jwt);
        rolId = nuevoId();
        tx.escribir(() -> roles.guardar(new Rol(rolId, "Recepción", "Agenda",
                EnumSet.of(Permiso.VER_CITAS, Permiso.VER_PANEL))));
        usuarioId = nuevoId();
        tx.escribir(() -> usuarios.guardar(new Usuario(usuarioId, "Marta", "Gómez", "marta@example.com",
                "300 555 0201", "marta", rolId), bcrypt.codificar("demo123")));
    }

    @Test
    void loginCorrectoDevuelveTokenUsuarioSinClaveYRolConSusPermisos() {
        Sesion sesion = autenticacion.iniciarSesion("marta", "demo123");

        assertNotNull(sesion.token());
        assertEquals(new Usuario(usuarioId, "Marta", "Gómez", "marta@example.com", "300 555 0201", "marta", rolId),
                sesion.usuario());
        assertEquals("Recepción", sesion.rol().nombre());
        assertEquals("Agenda", sesion.rol().descripcion());
        assertEquals(rolId, sesion.rol().id());
        assertEquals(EnumSet.of(Permiso.VER_CITAS, Permiso.VER_PANEL), EnumSet.copyOf(sesion.rol().permisos()));
        assertFalse(sesion.toString().contains("$2"), "la sesión no lleva el hash de la clave");
    }

    @Test
    void elTokenEsUnJwtValidoDelUsuarioQueVenceALos60Minutos() {
        LocalDateTime inicio = LocalDateTime.of(2026, 11, 8, 9, 0);
        reloj.fijarAhora(inicio);

        String token = autenticacion.iniciarSesion("marta", "demo123").token();

        assertEquals(Optional.of(usuarioId), jwt.validar(token));
        reloj.fijarAhora(inicio.plusMinutes(59));
        assertEquals(Optional.of(usuarioId), jwt.validar(token));
        reloj.fijarAhora(inicio.plusMinutes(61));
        assertEquals(Optional.empty(), jwt.validar(token));
        assertEquals(Optional.empty(), new ServicioJwt("otro-secreto-falso-0123456789abcdefghij", reloj)
                .validar(token));
    }

    @Test
    void elUsuarioConEspaciosYMayusculasEntraIgual() {
        assertEquals(usuarioId, autenticacion.iniciarSesion(" MARTA ", "demo123").usuario().id());
    }

    @Test
    void laClaveEsExacta() {
        exigirError(CodigoError.CREDENCIALES_INVALIDAS, () -> autenticacion.iniciarSesion("marta", "DEMO123"));
        exigirError(CodigoError.CREDENCIALES_INVALIDAS, () -> autenticacion.iniciarSesion("marta", " demo123"));
        exigirError(CodigoError.CREDENCIALES_INVALIDAS, () -> autenticacion.iniciarSesion("marta", ""));
    }

    @Test
    void claveMalaYUsuarioInexistenteDanElMismoError() {
        ErrorNegocio claveMala = exigirError(CodigoError.CREDENCIALES_INVALIDAS,
                () -> autenticacion.iniciarSesion("marta", "otra"));
        ErrorNegocio inexistente = exigirError(CodigoError.CREDENCIALES_INVALIDAS,
                () -> autenticacion.iniciarSesion("nadie", "demo123"));

        assertEquals("Usuario o contraseña incorrectos.", claveMala.getMessage());
        assertEquals(claveMala.getMessage(), inexistente.getMessage());
        assertEquals(claveMala.codigo(), inexistente.codigo());
        assertNull(claveMala.campos());
        assertNull(inexistente.campos());
    }

    @Test
    void usuarioOClaveNulosSonCredencialesInvalidas() {
        exigirError(CodigoError.CREDENCIALES_INVALIDAS, () -> autenticacion.iniciarSesion("marta", null));
        exigirError(CodigoError.CREDENCIALES_INVALIDAS, () -> autenticacion.iniciarSesion(null, "demo123"));
        exigirError(CodigoError.CREDENCIALES_INVALIDAS, () -> autenticacion.iniciarSesion(null, null));
    }

    @Test
    void laClaveSeGuardaComoHashBcryptCompatibleConSpring() {
        String guardada = hashDe(usuarioId);

        assertTrue(guardada.startsWith("$2a$10$"));
        assertEquals(60, guardada.length());
        assertNotEquals("demo123", guardada);
    }

    @Test
    void yoDevuelveUsuarioYRolSinTokenNuevo() {
        Sesion sesion = autenticacion.yo(usuarioId);

        assertNull(sesion.token());
        assertEquals(usuarioId, sesion.usuario().id());
        assertEquals(rolId, sesion.rol().id());
        assertEquals(EnumSet.of(Permiso.VER_CITAS, Permiso.VER_PANEL), EnumSet.copyOf(sesion.rol().permisos()));
    }

    @Test
    void yoDeUnUsuarioEliminadoEsNoAutenticado() {
        String token = autenticacion.iniciarSesion("marta", "demo123").token();
        ejecutar("DELETE FROM usuario WHERE id = ?", usuarioId);

        // El token sigue siendo un JWT válido, pero el usuario ya no existe.
        assertTrue(jwt.validar(token).isPresent());
        ErrorNegocio error = exigirError(CodigoError.NO_AUTENTICADO, () -> autenticacion.yo(usuarioId));
        assertEquals("Inicia sesión para continuar.", error.getMessage());
        exigirError(CodigoError.CREDENCIALES_INVALIDAS, () -> autenticacion.iniciarSesion("marta", "demo123"));
    }

    @Test
    void quitarleUnPermisoAlRolSeVeEnLaSiguienteSesionSinVolverAEntrar() {
        autenticacion.iniciarSesion("marta", "demo123");
        ejecutar("DELETE FROM rol_permiso WHERE rol_id = ? AND permiso = 'ver_citas'", rolId);

        assertEquals(EnumSet.of(Permiso.VER_PANEL), EnumSet.copyOf(autenticacion.yo(usuarioId).rol().permisos()));
    }

    @Test
    void unUsuarioSinRolEsUnErrorInternoNoUnaCredencialInvalida() {
        // La llave foránea impide el caso; se desactiva solo para comprobar que no se disfraza de otra cosa.
        ejecutar("ALTER TABLE usuario NOCHECK CONSTRAINT ALL");
        try {
            ejecutar("UPDATE usuario SET rol_id = ? WHERE id = ?", nuevoId(), usuarioId);

            assertThrows(IllegalStateException.class, () -> autenticacion.yo(usuarioId));
            assertThrows(IllegalStateException.class, () -> autenticacion.iniciarSesion("marta", "demo123"));
        } finally {
            ejecutar("DELETE FROM usuario");
            ejecutar("ALTER TABLE usuario WITH CHECK CHECK CONSTRAINT ALL");
        }
        assertEquals(10, pool.disponibles());
    }
}

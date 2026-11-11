package com.datemanager.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import com.datemanager.PruebaAdministracion;
import com.datemanager.dominio.Permiso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** La regla única «no dejar al sistema sin nadie que gestione roles». */
class ProteccionGestoresTest extends PruebaAdministracion {

    private ProteccionGestores gestores;

    @BeforeEach
    void armar() {
        gestores = new ProteccionGestores(roles, usuarios);
    }

    @Test
    void gestionaRolesSoloConElPermisoGestionarRoles() {
        assertTrue(ProteccionGestores.gestionaRoles(Set.of(Permiso.GESTIONAR_ROLES, Permiso.VER_PANEL)));
        assertFalse(ProteccionGestores.gestionaRoles(Set.of(Permiso.GESTIONAR_USUARIOS, Permiso.VER_PANEL)));
        assertFalse(ProteccionGestores.gestionaRoles(Set.of()));
    }

    @Test
    void otrosGestoresNoCuentaAlExcluidoNiALosDeRolesSinElPermiso() {
        UUID admin = crearRol("Admin", Permiso.GESTIONAR_ROLES);
        UUID comun = crearRol("Común", Permiso.VER_CITAS);
        UUID a = crearUsuario("a", admin);
        UUID b = crearUsuario("b", admin);
        crearUsuario("c", comun);

        assertEquals(1L, tx.leer(() -> gestores.otrosGestores(a)));
        assertEquals(2L, tx.leer(() -> gestores.otrosGestores(UUID.randomUUID())));
        tx.escribir(() -> usuarios.eliminar(b));
        assertEquals(0L, tx.leer(() -> gestores.otrosGestores(a)));
    }

    @Test
    void sinNingunRolGestorNoHayOtrosGestores() {
        UUID comun = crearRol("Común", Permiso.VER_CITAS);
        UUID a = crearUsuario("a", comun);

        assertEquals(0L, tx.leer(() -> gestores.otrosGestores(a)));
        assertEquals(0L, tx.leer(() -> gestores.gestoresTrasEditarRol(comun, Set.of(Permiso.VER_PANEL))));
    }

    @Test
    void gestoresTrasEditarUnRolCuentaConLosPermisosNuevosDeEseRol() {
        UUID x = crearRol("X", Permiso.GESTIONAR_ROLES);
        UUID y = crearRol("Y", Permiso.GESTIONAR_ROLES);
        crearUsuario("a", x);
        crearUsuario("b", y);
        crearUsuario("c", y);

        // Si X pierde el permiso solo quedan los dos de Y; si Y lo pierde, solo el de X.
        assertEquals(2L, tx.leer(() -> gestores.gestoresTrasEditarRol(x, Set.of(Permiso.VER_PANEL))));
        assertEquals(1L, tx.leer(() -> gestores.gestoresTrasEditarRol(y, Set.of())));
        // Si un rol sin el permiso lo recibe, también cuenta.
        UUID comun = crearRol("Común", Permiso.VER_CITAS);
        crearUsuario("d", comun);
        assertEquals(4L, tx.leer(() -> gestores.gestoresTrasEditarRol(comun, Set.of(Permiso.GESTIONAR_ROLES))));
    }
}

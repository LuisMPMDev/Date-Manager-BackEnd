package com.datemanager.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PermisosTest {

    private static final UUID R1 = UUID.randomUUID();
    private static final UUID R2 = UUID.randomUUID();

    /** Mismos elementos, sin importar el orden (los esperados son distintos entre sí). */
    private static <T> void assertMismosElementos(Collection<T> esperado, Collection<T> actual) {
        assertEquals(esperado.size(), actual.size(), "esperado " + esperado + " pero fue " + actual);
        assertTrue(actual.containsAll(esperado), "esperado " + esperado + " pero fue " + actual);
    }

    private static Rol rol(UUID id, Permiso... permisos) {
        return new Rol(id, "Rol", "", Set.of(permisos));
    }

    private static Usuario usuarioConRol(UUID rolId) {
        return new Usuario(UUID.randomUUID(), "Ana", "Pérez", "ana@x.com", "1", "ana", rolId);
    }

    @Test
    void elCatalogoTieneLosNuevePermisosConSusIds() {
        assertMismosElementos(
                List.of("ver_panel", "ver_citas", "gestionar_citas", "atender_citas", "gestionar_servicios",
                        "gestionar_clientes", "gestionar_usuarios", "gestionar_roles", "gestionar_enlaces"),
                Arrays.stream(Permiso.values()).map(Permiso::id).toList());
    }

    @Test
    void deIdDevuelveElPermisoYRechazaIdsDesconocidos() {
        assertEquals(Permiso.GESTIONAR_ENLACES, Permiso.deId("gestionar_enlaces"));
        ErrorNegocio e = assertThrows(ErrorNegocio.class, () -> Permiso.deId("inventado"));
        assertEquals(CodigoError.VALIDACION, e.codigo());
    }

    @Test
    void gestionarCitasAnadeVerCitas() {
        assertMismosElementos(List.of(Permiso.GESTIONAR_CITAS, Permiso.VER_CITAS),
                Permisos.conDependencias(Set.of(Permiso.GESTIONAR_CITAS)));
    }

    @Test
    void atenderCitasAnadeVerCitas() {
        assertMismosElementos(List.of(Permiso.ATENDER_CITAS, Permiso.VER_CITAS),
                Permisos.conDependencias(Set.of(Permiso.ATENDER_CITAS)));
    }

    @Test
    void sinDependenciasNoCambia() {
        assertEquals(List.of(Permiso.VER_PANEL), List.copyOf(Permisos.conDependencias(Set.of(Permiso.VER_PANEL))));
        assertTrue(Permisos.conDependencias(Set.of()).isEmpty());
    }

    @Test
    void noDuplicaVerCitasSiYaEsta() {
        assertMismosElementos(List.of(Permiso.VER_CITAS, Permiso.GESTIONAR_CITAS),
                Permisos.conDependencias(Set.of(Permiso.VER_CITAS, Permiso.GESTIONAR_CITAS)));
    }

    @Test
    void rechazaQueElActorSeQuiteGestionarRolesDeSuPropioRol() {
        Rol antes = rol(R1, Permiso.GESTIONAR_ROLES, Permiso.VER_PANEL);
        Rol despues = rol(R1, Permiso.VER_PANEL);

        Optional<String> mensaje = Permisos.validarCambioRol(R1, antes, despues);

        assertEquals(Optional.of("No puedes quitarte el permiso de gestionar roles de tu propio rol."), mensaje);
    }

    @Test
    void permiteQuitarGestionarRolesAOtroRol() {
        Rol antes = rol(R2, Permiso.GESTIONAR_ROLES);
        Rol despues = rol(R2);

        assertTrue(Permisos.validarCambioRol(R1, antes, despues).isEmpty());
    }

    @Test
    void permiteCambiosEnElPropioRolQueConservanGestionarRoles() {
        Rol antes = rol(R1, Permiso.GESTIONAR_ROLES);
        Rol despues = rol(R1, Permiso.GESTIONAR_ROLES, Permiso.VER_PANEL);

        assertTrue(Permisos.validarCambioRol(R1, antes, despues).isEmpty());
    }

    @Test
    void noSePuedeEliminarUnRolConUsuarios() {
        assertFalse(Permisos.puedeEliminarRol(R1, List.of(usuarioConRol(R1))));
    }

    @Test
    void sePuedeEliminarUnRolSinUsuarios() {
        assertTrue(Permisos.puedeEliminarRol(R2, List.of(usuarioConRol(R1))));
        assertTrue(Permisos.puedeEliminarRol(R2, List.of()));
    }

    @Test
    void conGestionarRolesElActorPuedeAsignarCualquierRol() {
        assertTrue(Permisos.puedeAsignarRol(
                Set.of(Permiso.GESTIONAR_ROLES), Set.of(Permiso.GESTIONAR_SERVICIOS, Permiso.VER_PANEL)));
    }

    @Test
    void sinGestionarRolesSoloPuedeAsignarSubconjuntos() {
        Set<Permiso> actor = Set.of(Permiso.VER_PANEL, Permiso.VER_CITAS);

        assertTrue(Permisos.puedeAsignarRol(actor, Set.of(Permiso.VER_CITAS)));
        assertTrue(Permisos.puedeAsignarRol(actor, Set.of()));
        assertFalse(Permisos.puedeAsignarRol(actor, Set.of(Permiso.VER_CITAS, Permiso.GESTIONAR_CITAS)));
    }
}

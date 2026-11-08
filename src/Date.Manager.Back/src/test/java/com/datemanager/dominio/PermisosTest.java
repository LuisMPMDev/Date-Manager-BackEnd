package com.datemanager.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PermisosTest {

    private static final UUID R1 = UUID.randomUUID();
    private static final UUID R2 = UUID.randomUUID();

    private static Rol rol(UUID id, Permiso... permisos) {
        return new Rol(id, "Rol", "", Set.of(permisos));
    }

    private static Usuario usuarioConRol(UUID rolId) {
        return new Usuario(UUID.randomUUID(), "Ana", "Pérez", "ana@x.com", "1", "ana", rolId);
    }

    @Test
    void elCatalogoTieneLosNuevePermisosConSusIds() {
        assertThat(Permiso.values()).extracting(Permiso::id).containsExactlyInAnyOrder(
                "ver_panel", "ver_citas", "gestionar_citas", "atender_citas", "gestionar_servicios",
                "gestionar_clientes", "gestionar_usuarios", "gestionar_roles", "gestionar_enlaces");
    }

    @Test
    void deIdDevuelveElPermisoYRechazaIdsDesconocidos() {
        assertThat(Permiso.deId("gestionar_enlaces")).isEqualTo(Permiso.GESTIONAR_ENLACES);
        assertThatThrownBy(() -> Permiso.deId("inventado"))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> assertThat(e.codigo()).isEqualTo(CodigoError.VALIDACION));
    }

    @Test
    void gestionarCitasAnadeVerCitas() {
        assertThat(Permisos.conDependencias(Set.of(Permiso.GESTIONAR_CITAS)))
                .containsExactlyInAnyOrder(Permiso.GESTIONAR_CITAS, Permiso.VER_CITAS);
    }

    @Test
    void atenderCitasAnadeVerCitas() {
        assertThat(Permisos.conDependencias(Set.of(Permiso.ATENDER_CITAS)))
                .containsExactlyInAnyOrder(Permiso.ATENDER_CITAS, Permiso.VER_CITAS);
    }

    @Test
    void sinDependenciasNoCambia() {
        assertThat(Permisos.conDependencias(Set.of(Permiso.VER_PANEL))).containsExactly(Permiso.VER_PANEL);
        assertThat(Permisos.conDependencias(Set.of())).isEmpty();
    }

    @Test
    void noDuplicaVerCitasSiYaEsta() {
        assertThat(Permisos.conDependencias(Set.of(Permiso.VER_CITAS, Permiso.GESTIONAR_CITAS)))
                .containsExactlyInAnyOrder(Permiso.VER_CITAS, Permiso.GESTIONAR_CITAS);
    }

    @Test
    void rechazaQueElActorSeQuiteGestionarRolesDeSuPropioRol() {
        Rol antes = rol(R1, Permiso.GESTIONAR_ROLES, Permiso.VER_PANEL);
        Rol despues = rol(R1, Permiso.VER_PANEL);

        Optional<String> mensaje = Permisos.validarCambioRol(R1, antes, despues);

        assertThat(mensaje).hasValue("No puedes quitarte el permiso de gestionar roles de tu propio rol.");
    }

    @Test
    void permiteQuitarGestionarRolesAOtroRol() {
        Rol antes = rol(R2, Permiso.GESTIONAR_ROLES);
        Rol despues = rol(R2);

        assertThat(Permisos.validarCambioRol(R1, antes, despues)).isEmpty();
    }

    @Test
    void permiteCambiosEnElPropioRolQueConservanGestionarRoles() {
        Rol antes = rol(R1, Permiso.GESTIONAR_ROLES);
        Rol despues = rol(R1, Permiso.GESTIONAR_ROLES, Permiso.VER_PANEL);

        assertThat(Permisos.validarCambioRol(R1, antes, despues)).isEmpty();
    }

    @Test
    void noSePuedeEliminarUnRolConUsuarios() {
        assertThat(Permisos.puedeEliminarRol(R1, List.of(usuarioConRol(R1)))).isFalse();
    }

    @Test
    void sePuedeEliminarUnRolSinUsuarios() {
        assertThat(Permisos.puedeEliminarRol(R2, List.of(usuarioConRol(R1)))).isTrue();
        assertThat(Permisos.puedeEliminarRol(R2, List.of())).isTrue();
    }

    @Test
    void conGestionarRolesElActorPuedeAsignarCualquierRol() {
        assertThat(Permisos.puedeAsignarRol(
                Set.of(Permiso.GESTIONAR_ROLES), Set.of(Permiso.GESTIONAR_SERVICIOS, Permiso.VER_PANEL))).isTrue();
    }

    @Test
    void sinGestionarRolesSoloPuedeAsignarSubconjuntos() {
        Set<Permiso> actor = Set.of(Permiso.VER_PANEL, Permiso.VER_CITAS);

        assertThat(Permisos.puedeAsignarRol(actor, Set.of(Permiso.VER_CITAS))).isTrue();
        assertThat(Permisos.puedeAsignarRol(actor, Set.of())).isTrue();
        assertThat(Permisos.puedeAsignarRol(actor, Set.of(Permiso.VER_CITAS, Permiso.GESTIONAR_CITAS))).isFalse();
    }
}

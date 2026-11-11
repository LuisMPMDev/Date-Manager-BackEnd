package com.datemanager.infraestructura.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.datemanager.PruebaAdministracion;
import com.datemanager.RelojMutable;
import com.datemanager.dominio.Permiso;
import com.datemanager.dominio.Rol;
import com.datemanager.dominio.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutenticadorTest extends PruebaAdministracion {

    private static final String SECRETO = "secreto-de-prueba-con-mas-de-32-bytes!!";

    private RelojMutable reloj;
    private ServicioJwt jwt;
    private Autenticador autenticador;

    @BeforeEach
    void armarAutenticador() {
        reloj = new RelojMutable(ZoneId.of("America/Bogota"));
        reloj.fijarAhora(LocalDateTime.of(2026, 11, 10, 9, 0, 0));
        jwt = new ServicioJwt(SECRETO, reloj);
        autenticador = new Autenticador(jwt, usuarios, roles, tx);
    }

    private String bearer(UUID usuario) {
        return "Bearer " + jwt.emitir(usuario);
    }

    // ------------------------------------------------------------------ autenticar

    @Test
    void unTokenValidDaElUsuarioConSuRolActual() {
        UUID rol = crearRol("Recepción", Permiso.VER_CITAS);
        UUID id = crearUsuario("ana", rol);

        assertEquals(Optional.of(new UsuarioActual(id, rol)), autenticador.autenticar(bearer(id)));
    }

    @Test
    void elUsuarioBorradoTrasEmitirElTokenYaNoEntra() {
        UUID id = crearUsuario("borrado", crearRol("Base"));
        String cabecera = bearer(id);
        assertTrue(autenticador.autenticar(cabecera).isPresent());

        tx.escribir(() -> usuarios.eliminar(id));

        assertEquals(Optional.empty(), autenticador.autenticar(cabecera));
    }

    @Test
    void unTokenValidoDeUnUsuarioQueNuncaExistioNoEntra() {
        assertEquals(Optional.empty(), autenticador.autenticar(bearer(nuevoId())));
    }

    @Test
    void elRolDelUsuarioActualSeLeeEnCadaPeticion() {
        UUID uno = crearRol("Uno");
        UUID dos = crearRol("Dos");
        UUID id = crearUsuario("cambia", uno);
        String cabecera = bearer(id);
        assertEquals(uno, autenticador.autenticar(cabecera).orElseThrow().rolId());

        tx.escribir(() -> usuarios.guardar(new Usuario(id, "Nombre", "Apellido", "cambia@correo.com", "", "cambia", dos),
                null));

        assertEquals(dos, autenticador.autenticar(cabecera).orElseThrow().rolId());
    }

    @Test
    void elEsquemaAceptaBearerEnMinusculasYMayusculas() {
        UUID id = crearUsuario("esquema", crearRol("Base"));
        String token = jwt.emitir(id);

        assertTrue(autenticador.autenticar("Bearer " + token).isPresent());
        assertTrue(autenticador.autenticar("bearer " + token).isPresent());
        assertTrue(autenticador.autenticar("BEARER " + token).isPresent());
    }

    @Test
    void otrosEsquemasOFormasMalasNoEntran() {
        UUID id = crearUsuario("formas", crearRol("Base"));
        String token = jwt.emitir(id);

        String[] malas = {
            null,
            "",
            " ",
            "Basic x",
            "Basic " + token,
            "Token " + token,
            "Bearer",
            "Bearer ",
            "bearer ",
            "Bearer  ",
            "Bearer  " + token,
            "Bearer " + token + " ",
            "Bearer " + token + " extra",
            "Bearer\t" + token,
            " Bearer " + token,
            "Bearer" + token,
            token,
            "Bearer " + token.substring(0, token.length() - 1),
            "Bearer basura",
            "Bearer a.b.c",
            "Bearer " + token + "\n",
        };
        for (String mala : malas) {
            assertEquals(Optional.empty(), autenticador.autenticar(mala), "cabecera=[" + mala + "]");
        }
        assertTrue(autenticador.autenticar("Bearer " + token).isPresent(), "control");
    }

    @Test
    void unTokenVencidoNoEntra() {
        UUID id = crearUsuario("vence", crearRol("Base"));
        String cabecera = bearer(id);

        reloj.fijarAhora(LocalDateTime.of(2026, 11, 10, 10, 0, 1));

        assertEquals(Optional.empty(), autenticador.autenticar(cabecera));
    }

    // ------------------------------------------------------------------ tiene

    private UsuarioActual actual(UUID id) {
        return autenticador.autenticar(bearer(id)).orElseThrow();
    }

    @Test
    void unPermisoDeLaListaBasta() {
        UUID rol = crearRol("Mixto", Permiso.VER_CITAS, Permiso.ATENDER_CITAS);
        UsuarioActual ana = actual(crearUsuario("ana", rol));

        assertTrue(autenticador.tiene(ana, Set.of("ver_citas")));
        assertTrue(autenticador.tiene(ana, Set.of("gestionar_servicios", "atender_citas")));
        assertFalse(autenticador.tiene(ana, Set.of("gestionar_servicios", "gestionar_clientes")));
        assertFalse(autenticador.tiene(ana, Set.of()));
    }

    @Test
    void unRolSinPermisosNoTieneNinguno() {
        UsuarioActual vacio = actual(crearUsuario("vacio", crearRol("Vacío")));

        for (Permiso permiso : Permiso.values()) {
            assertFalse(autenticador.tiene(vacio, Set.of(permiso.id())), permiso.id());
        }
    }

    @Test
    void cambiarLosPermisosDelRolSurteEfectoSinReiniciar() {
        UUID rol = crearRol("Cambiante", Permiso.VER_CITAS);
        UsuarioActual ana = actual(crearUsuario("ana", rol));
        assertTrue(autenticador.tiene(ana, Set.of("ver_citas")));
        assertFalse(autenticador.tiene(ana, Set.of("gestionar_servicios")));

        tx.escribir(() -> roles.guardar(new Rol(rol, "Cambiante", "", Set.of(Permiso.GESTIONAR_SERVICIOS))));

        assertFalse(autenticador.tiene(ana, Set.of("ver_citas")));
        assertTrue(autenticador.tiene(ana, Set.of("gestionar_servicios")));
    }

    @Test
    void cambiarElRolDelUsuarioSurteEfectoConElMismoUsuarioActual() {
        UUID conPermiso = crearRol("Con", Permiso.VER_CITAS);
        UUID sinPermiso = crearRol("Sin");
        UUID id = crearUsuario("traslado", conPermiso);
        UsuarioActual viejo = actual(id);
        assertTrue(autenticador.tiene(viejo, Set.of("ver_citas")));

        tx.escribir(() -> usuarios.guardar(
                new Usuario(id, "Nombre", "Apellido", "traslado@correo.com", "", "traslado", sinPermiso), null));

        // El UsuarioActual de la petición ya llevaba el rol viejo: lo que manda es el rol que hay ahora en la base.
        assertFalse(autenticador.tiene(viejo, Set.of("ver_citas")));
    }

    @Test
    void unUsuarioBorradoYaNoTienePermisos() {
        UUID id = crearUsuario("adios", crearRol("Con", Permiso.VER_CITAS));
        UsuarioActual ana = actual(id);

        tx.escribir(() -> usuarios.eliminar(id));

        assertFalse(autenticador.tiene(ana, Set.of("ver_citas")));
    }

    @Test
    void unPermisoDesconocidoEsUnErrorDeProgramacion() {
        UsuarioActual ana = actual(crearUsuario("ana", crearRol("Con", Permiso.VER_CITAS)));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> autenticador.tiene(ana, Set.of("ver_citas", "no_existe")));
        assertTrue(e.getMessage().contains("no_existe"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> autenticador.tiene(ana, Set.of("VER_CITAS")));
    }

    @Test
    void validarElCatalogoEsAntesDeTocarLaBase() {
        // Un permiso desconocido falla aunque el usuario ni exista: no depende de los datos.
        assertThrows(IllegalArgumentException.class,
                () -> autenticador.tiene(new UsuarioActual(nuevoId(), nuevoId()), Set.of("no_existe")));
    }
}

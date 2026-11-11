package com.datemanager.infraestructura.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.datemanager.CodificadorFalso;
import com.datemanager.PruebaAdministracion;
import com.datemanager.dominio.Usuario;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio.UsuarioConClave;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UsuarioRepositorioTest extends PruebaAdministracion {

    private static final String HASH = CodificadorFalso.hash("x");

    private UUID rol;

    private Usuario usuario(UUID id, String usuario) {
        return new Usuario(id, "Nombre", "Apellido", usuario + "@correo.com", "300", usuario, rol);
    }

    private void guardar(Usuario usuario, String hash) {
        tx.escribir(() -> usuarios.guardar(usuario, hash));
    }

    @BeforeEach
    void crearRolBase() {
        rol = crearRol("Base");
    }

    @Test
    void guardarYBuscarConservanTodosLosCampos() {
        UUID id = nuevoId();
        Usuario ana = new Usuario(id, "Ana", "Pérez", "ana@correo.com", "+57 300 111", "ana", rol);

        Usuario devuelto = tx.escribir(() -> usuarios.guardar(ana, CodificadorFalso.hash("ana")));

        assertEquals(ana, devuelto);
        assertEquals(ana, tx.leer(() -> usuarios.buscar(id)).orElseThrow());
        assertEquals(CodificadorFalso.hash("ana"), hashDe(id));
    }

    @Test
    void textoConTildesEnyeYEmojiHaceElViajeCompleto() {
        UUID id = nuevoId();
        Usuario u = new Usuario(id, "José 😀", "Ñandú Peña", "jose@correo.com", "☎ 300", "josé_ñ", rol);

        guardar(u, HASH);

        Usuario leido = tx.leer(() -> usuarios.buscar(id)).orElseThrow();
        assertEquals(u, leido);
        assertEquals("José 😀", consultarTexto("SELECT nombre FROM usuario WHERE id = ?", id));
        assertEquals(id, tx.leer(() -> usuarios.buscarPorUsuario("josé_ñ")).orElseThrow().usuario().id());
    }

    @Test
    void elNombreDeUsuarioSeGuardaNormalizado() {
        UUID id = nuevoId();

        Usuario devuelto = tx.escribir(() -> usuarios.guardar(
                new Usuario(id, "Carla", "Gómez", "carla@correo.com", "", "  CarLa ", rol), HASH));

        assertEquals("carla", devuelto.usuario());
        assertEquals("carla", consultarTexto("SELECT usuario FROM usuario WHERE id = ?", id));
    }

    @Test
    void guardarSinHashConservaElActualYConHashLoCambia() {
        UUID id = nuevoId();
        guardar(usuario(id, "ana"), CodificadorFalso.hash("1"));

        guardar(new Usuario(id, "Otro", "Apellido", "otro@correo.com", "", "ana", rol), null);
        assertEquals(CodificadorFalso.hash("1"), hashDe(id));
        assertEquals("Otro", tx.leer(() -> usuarios.buscar(id)).orElseThrow().nombre());

        guardar(usuario(id, "ana"), CodificadorFalso.hash("2"));
        assertEquals(CodificadorFalso.hash("2"), hashDe(id));
        assertEquals(1, consultarInt("SELECT COUNT(*) FROM usuario"));
    }

    @Test
    void unUsuarioNuevoNecesitaElHash() {
        assertThrows(IllegalArgumentException.class, () -> guardar(usuario(nuevoId(), "ana"), null));
        assertEquals(0, consultarInt("SELECT COUNT(*) FROM usuario"));
    }

    @Test
    void buscarPorUsuarioDevuelveElHashPeroSuTextoNoLoMuestra() {
        UUID id = nuevoId();
        guardar(usuario(id, "ana"), CodificadorFalso.hash("secreto"));

        UsuarioConClave conClave = tx.leer(() -> usuarios.buscarPorUsuario("ana")).orElseThrow();

        assertEquals(CodificadorFalso.hash("secreto"), conClave.claveHash());
        assertEquals(id, conClave.usuario().id());
        assertFalse(conClave.toString().contains(CodificadorFalso.hash("secreto")));
        assertTrue(tx.leer(() -> usuarios.buscarPorUsuario("nadie")).isEmpty());
    }

    @Test
    void elNombreDeUsuarioRepetidoLoRechazaLaBaseComoRestriccion() {
        guardar(usuario(nuevoId(), "ana"), HASH);

        ErrorSql error = assertThrows(ErrorSql.class, () -> guardar(usuario(nuevoId(), "ANA"), HASH));

        assertTrue(ErroresBase.esViolacionDeRestriccion(error));
        assertEquals(1, filasConUsuario("ana"));
    }

    @Test
    void porRolYConteosPorRol() {
        UUID otroRol = crearRol("Otro");
        UUID ana = crearUsuario("ana", rol);
        UUID beto = crearUsuario("beto", rol);
        UUID carla = crearUsuario("carla", otroRol);

        assertEquals(Set.of(ana, beto),
                Set.copyOf(tx.leer(() -> usuarios.porRol(rol)).stream().map(Usuario::id).toList()));
        assertEquals(3L, tx.leer(() -> usuarios.contarPorRolIn(List.of(rol, otroRol))));
        assertEquals(1L, tx.leer(() -> usuarios.contarPorRolIn(Set.of(otroRol))));
        assertEquals(0L, tx.leer(() -> usuarios.contarPorRolIn(Set.of(nuevoId()))));
        assertEquals(0L, tx.leer(() -> usuarios.contarPorRolIn(Set.of())));
        assertEquals(2L, tx.leer(() -> usuarios.contarOtrosPorRolIn(ana, List.of(rol, otroRol))));
        assertEquals(2L, tx.leer(() -> usuarios.contarOtrosPorRolIn(carla, List.of(rol))));
        assertEquals(0L, tx.leer(() -> usuarios.contarOtrosPorRolIn(carla, List.of(otroRol))));
        assertEquals(0L, tx.leer(() -> usuarios.contarOtrosPorRolIn(carla, Set.of())));
    }

    @Test
    void todosVienenPorNombreDeUsuarioYLuegoPorId() {
        UUID carla = crearUsuario("carla", rol);
        UUID ana = crearUsuario("ana", rol);
        UUID beto = crearUsuario("beto", rol);

        assertEquals(List.of(ana, beto, carla), tx.leer(usuarios::todos).stream().map(Usuario::id).toList());
    }

    @Test
    void eliminarBorraLaFila() {
        UUID id = crearUsuario("ana", rol);

        tx.escribir(() -> usuarios.eliminar(id));

        assertTrue(tx.leer(() -> usuarios.buscar(id)).isEmpty());
    }

    @Test
    void buscarUnoInexistenteDevuelveVacio() {
        assertTrue(tx.leer(() -> usuarios.buscar(nuevoId())).isEmpty());
    }
}

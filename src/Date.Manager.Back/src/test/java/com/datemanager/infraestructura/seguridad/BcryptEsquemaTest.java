package com.datemanager.infraestructura.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.datemanager.PruebaAdministracion;
import com.datemanager.dominio.Usuario;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio.UsuarioConClave;
import org.junit.jupiter.api.Test;

/** Lo que produce {@link Bcrypt} cumple la restricción {@code ck_usuario_clave_hash_bcrypt} de la base. */
class BcryptEsquemaTest extends PruebaAdministracion {

    private final Bcrypt bcrypt = new Bcrypt();

    private UUID guardar(String usuario, String hash) {
        UUID id = nuevoId();
        UUID rol = crearRol("Rol de " + usuario);
        tx.escribir(() -> usuarios.guardar(
                new Usuario(id, "Nombre", "Apellido", usuario + "@correo.com", "", usuario, rol), hash));
        return id;
    }

    @Test
    void unHashDeBcryptSeGuardaYSeLeeDeVueltaIntacto() {
        String hash = bcrypt.codificar("Clave de ñ😀 prueba");

        guardar("ana", hash);

        UsuarioConClave leido = tx.leer(() -> usuarios.buscarPorUsuario("ana")).orElseThrow();
        assertEquals(hash, leido.claveHash());
        assertTrue(bcrypt.coincide("Clave de ñ😀 prueba", leido.claveHash()));
    }

    @Test
    void muchosHashesGeneradosCumplenLaRestriccionDelEsquema() {
        // La sal aleatoria produce todo tipo de caracteres ('.', '/', mayúsculas, dígitos): ninguno rompe el CHECK.
        for (int i = 0; i < 15; i++) {
            guardar("usuario" + i, bcrypt.codificar("clave " + i));
        }
        assertEquals(15, filasConUsuarioPrefijo("usuario"));
    }

    @Test
    void tambienPasanLosHashesDeSpringConLos3Prefijos() {
        guardar("dosa", "$2a$10$vixASfLD20qxB.gNeD87ReDPHGDdnDFOnvX5VHfbBGI9pPlyxMomu");
        guardar("dosb", "$2b$10$VTeSo8CLU1rHgXMO9vT4EO.iKhED2UsqQNtWguNT1EYyxuHV/ki7a");
        guardar("dosy", "$2y$10$Kqdp.EnJBiSfYctig2wHju2e4IHYocEJ5b2bwuq7TGnSykZ45JtOG");
        assertEquals(3, consultarInt("SELECT COUNT(*) FROM usuario WHERE usuario LIKE 'dos_'"));
    }

    private static int filasConUsuarioPrefijo(String prefijo) {
        return consultarInt("SELECT COUNT(*) FROM usuario WHERE usuario LIKE ?", prefijo + "%");
    }
}

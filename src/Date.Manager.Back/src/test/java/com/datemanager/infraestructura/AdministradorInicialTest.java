package com.datemanager.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.datemanager.PruebaIntegracion;
import com.datemanager.dominio.Permiso;
import com.datemanager.infraestructura.persistencia.RolRepositorio;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(OutputCaptureExtension.class)
class AdministradorInicialTest extends PruebaIntegracion {

    private static final String CLAVE = "clave-de-prueba-123";

    @Autowired
    private RolRepositorio roles;

    @Autowired
    private UsuarioRepositorio usuarios;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private PlatformTransactionManager transacciones;

    private AdministradorInicial administrador(String usuario, String clave) {
        return new AdministradorInicial(roles, usuarios, codificador, transacciones, usuario, clave);
    }

    private int contar(String tabla) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
    }

    @Test
    void sinUsuariosYConLasVariablesCreaElRolAdministradorConTodosLosPermisosYSuUsuario() {
        administrador("  Admin ", CLAVE).crearSiHaceFalta();

        assertThat(contar("rol")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT nombre FROM rol", String.class)).isEqualTo("Administrador");
        List<String> permisos = jdbc.queryForList("SELECT permiso FROM rol_permiso", String.class);
        assertThat(permisos).containsExactlyInAnyOrder(
                Arrays.stream(Permiso.values()).map(Permiso::id).toArray(String[]::new));
        assertThat(permisos).hasSize(9);

        assertThat(contar("usuario")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT usuario FROM usuario", String.class)).isEqualTo("admin");
        String hash = jdbc.queryForObject("SELECT clave_hash FROM usuario", String.class);
        assertThat(hash).startsWith("$2");
        assertThat(codificador.matches(CLAVE, hash)).isTrue();
        UUID rolDelUsuario = jdbc.queryForObject("SELECT rol_id FROM usuario", UUID.class);
        assertThat(rolDelUsuario).isEqualTo(jdbc.queryForObject("SELECT id FROM rol", UUID.class));
    }

    @Test
    void conUsuariosExistentesNoCreaNada() {
        UUID rolId = UUID.randomUUID();
        jdbc.update("INSERT INTO rol (id, nombre, descripcion) VALUES (?, 'Recepción', '')", rolId);
        jdbc.update("""
                INSERT INTO usuario (id, nombre, apellido, correo, telefono, usuario, clave_hash, rol_id)
                VALUES (?, 'Ana', '', '', '', 'ana', ?, ?)
                """, UUID.randomUUID(), codificador.encode("x"), rolId);

        administrador("admin", CLAVE).crearSiHaceFalta();

        assertThat(contar("rol")).isEqualTo(1);
        assertThat(contar("rol_permiso")).isZero();
        assertThat(contar("usuario")).isEqualTo(1);
    }

    @Test
    void sinVariablesArrancaSinCrearNadaYRegistraUnAviso(CapturedOutput salida) {
        administrador("", "").crearSiHaceFalta();
        administrador(null, null).crearSiHaceFalta();
        administrador("admin", " ").crearSiHaceFalta();

        assertThat(contar("rol")).isZero();
        assertThat(contar("usuario")).isZero();
        assertThat(salida.getOut()).contains("WARN").contains("DATEMANAGER_ADMIN_USUARIO");
    }

    @Test
    void elAvisoNoMuestraLaClave(CapturedOutput salida) {
        administrador("", CLAVE).crearSiHaceFalta();

        assertThat(salida.getAll()).doesNotContain(CLAVE);
    }
}

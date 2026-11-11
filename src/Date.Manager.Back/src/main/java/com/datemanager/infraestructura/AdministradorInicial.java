package com.datemanager.infraestructura;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.datemanager.aplicacion.CodificadorClave;
import com.datemanager.dominio.Permiso;
import com.datemanager.dominio.Rol;
import com.datemanager.dominio.Usuario;
import com.datemanager.dominio.Validaciones;
import com.datemanager.infraestructura.persistencia.ErrorSql;
import com.datemanager.infraestructura.persistencia.RolRepositorio;
import com.datemanager.infraestructura.persistencia.Transaccion;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio;

/**
 * Al arrancar, si no hay ningún usuario, crea el rol Administrador (todos los permisos) y su usuario con
 * {@code DATEMANAGER_ADMIN_USUARIO} y {@code DATEMANAGER_ADMIN_CLAVE}. Si faltan, arranca igual y avisa.
 * Nunca registra la clave. Una clave de más de 72 bytes hace fallar el arranque (como en la versión Spring, cuyo
 * codificador la rechazaba).
 */
public final class AdministradorInicial {

    private static final Logger LOG = Logger.getLogger(AdministradorInicial.class.getName());

    private final Transaccion transaccion;
    private final RolRepositorio roles;
    private final UsuarioRepositorio usuarios;
    private final CodificadorClave codificador;
    private final String usuario;
    private final String clave;

    public AdministradorInicial(Transaccion transaccion, RolRepositorio roles, UsuarioRepositorio usuarios,
            CodificadorClave codificador, String usuario, String clave) {
        this.transaccion = transaccion;
        this.roles = roles;
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.usuario = usuario;
        this.clave = clave;
    }

    public void crearSiHaceFalta() {
        transaccion.escribir(() -> {
            if (hayUsuarios()) {
                return;
            }
            String normalizado = Validaciones.normalizarUsuario(usuario);
            if (normalizado.isEmpty() || clave == null || clave.isBlank()) {
                LOG.warning("No hay usuarios y faltan DATEMANAGER_ADMIN_USUARIO o DATEMANAGER_ADMIN_CLAVE: "
                        + "no se crea el administrador inicial y nadie podrá iniciar sesión.");
                return;
            }
            String hash = codificador.codificar(clave);
            Rol rol = roles.guardar(new Rol(UUID.randomUUID(), "Administrador", "Acceso completo al sistema",
                    EnumSet.allOf(Permiso.class)));
            usuarios.guardar(new Usuario(UUID.randomUUID(), "Administrador", "", "", "", normalizado, rol.id()), hash);
            LOG.log(Level.INFO, "Administrador inicial creado con el usuario «{0}».", normalizado);
        });
    }

    private boolean hayUsuarios() {
        try (PreparedStatement s = transaccion.conexion().prepareStatement(
                "SELECT CASE WHEN EXISTS (SELECT 1 FROM usuario) THEN 1 ELSE 0 END");
                ResultSet r = s.executeQuery()) {
            r.next();
            return r.getInt(1) == 1;
        } catch (SQLException e) {
            throw new ErrorSql(e);
        }
    }
}

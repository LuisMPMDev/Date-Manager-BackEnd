package com.datemanager.infraestructura;

import java.util.EnumSet;

import com.datemanager.dominio.Permiso;
import com.datemanager.dominio.Validaciones;
import com.datemanager.infraestructura.persistencia.RolEntidad;
import com.datemanager.infraestructura.persistencia.RolRepositorio;
import com.datemanager.infraestructura.persistencia.UsuarioEntidad;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Al arrancar, si no hay ningún usuario, crea el rol Administrador (todos los permisos) y su usuario con
 * {@code DATEMANAGER_ADMIN_USUARIO} y {@code DATEMANAGER_ADMIN_CLAVE}. Si faltan, arranca igual y avisa.
 * Nunca registra la clave.
 */
@Component
public class AdministradorInicial implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdministradorInicial.class);

    private final RolRepositorio roles;
    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder codificador;
    private final TransactionTemplate transaccion;
    private final String usuario;
    private final String clave;

    public AdministradorInicial(RolRepositorio roles, UsuarioRepositorio usuarios, PasswordEncoder codificador,
            PlatformTransactionManager transacciones,
            @Value("${datemanager.admin.usuario:}") String usuario,
            @Value("${datemanager.admin.clave:}") String clave) {
        this.roles = roles;
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.transaccion = new TransactionTemplate(transacciones);
        this.usuario = usuario;
        this.clave = clave;
    }

    @Override
    public void run(ApplicationArguments args) {
        crearSiHaceFalta();
    }

    public void crearSiHaceFalta() {
        transaccion.executeWithoutResult(estado -> {
            if (usuarios.count() > 0) {
                return;
            }
            String normalizado = Validaciones.normalizarUsuario(usuario);
            if (normalizado.isEmpty() || clave == null || clave.isBlank()) {
                LOG.warn("No hay usuarios y faltan DATEMANAGER_ADMIN_USUARIO o DATEMANAGER_ADMIN_CLAVE: "
                        + "no se crea el administrador inicial y nadie podrá iniciar sesión.");
                return;
            }
            RolEntidad rol = roles.save(new RolEntidad("Administrador", "Acceso completo al sistema",
                    EnumSet.allOf(Permiso.class)));
            usuarios.save(new UsuarioEntidad("Administrador", "", "", "", normalizado,
                    codificador.encode(clave), rol.getId()));
            LOG.info("Administrador inicial creado con el usuario «{}».", normalizado);
        });
    }
}

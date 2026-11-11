package com.datemanager.aplicacion;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.Permiso;
import com.datemanager.dominio.Permisos;
import com.datemanager.dominio.Rol;
import com.datemanager.dominio.Usuario;
import com.datemanager.infraestructura.persistencia.BloqueoAdministracion;
import com.datemanager.infraestructura.persistencia.RolRepositorio;
import com.datemanager.infraestructura.persistencia.Transaccion;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio;

/** Casos de uso de roles del panel. Las protecciones se comprueban aquí, dentro de la transacción. */
public class RolesServicio {

    private final Transaccion transaccion;
    private final RolRepositorio roles;
    private final UsuarioRepositorio usuarios;
    private final BloqueoAdministracion bloqueo;
    private final ProteccionGestores gestores;

    public RolesServicio(Transaccion transaccion, RolRepositorio roles, UsuarioRepositorio usuarios,
            BloqueoAdministracion bloqueo) {
        this.transaccion = Objects.requireNonNull(transaccion);
        this.roles = Objects.requireNonNull(roles);
        this.usuarios = Objects.requireNonNull(usuarios);
        this.bloqueo = Objects.requireNonNull(bloqueo);
        this.gestores = new ProteccionGestores(roles, usuarios);
    }

    public List<Rol> listar() {
        return transaccion.leer(roles::todos);
    }

    /**
     * Crea el rol con ese id si no existe; si existe, lo reemplaza. Los permisos se guardan con sus
     * dependencias ({@code gestionar_citas} o {@code atender_citas} implican {@code ver_citas}).
     *
     * <p>La regla «no dejar al sistema sin quien gestione roles» se comprueba aquí con el estado fresco, bajo el
     * bloqueo: el permiso del actor ya se verificó en el filtro, antes de esperar el bloqueo, y otra petición pudo
     * quitárselo (o quitárselo a otro rol) entretanto. Por eso se vuelve a leer su rol y se cuenta quién seguiría
     * gestionando roles después del cambio.
     */
    public Rol guardar(UUID id, String nombre, String descripcion, Set<Permiso> permisos, UUID actorId) {
        return transaccion.escribir(() -> {
            bloqueo.bloquear();
            Usuario actor = actor(actorId);
            boolean actorGestionaRoles = roles.buscar(actor.rolId())
                    .map(rol -> ProteccionGestores.gestionaRoles(rol.permisos())).orElse(false);
            if (!actorGestionaRoles) {
                throw new ErrorNegocio(CodigoError.SIN_PERMISO, "No tienes permiso para realizar esta acción.");
            }
            Set<Permiso> conDependencias = Permisos.conDependencias(permisos);
            String nombreFinal = nombre.strip();
            String descripcionFinal = descripcion == null ? "" : descripcion.strip();
            Rol despues = new Rol(id, nombreFinal, descripcionFinal, conDependencias);

            Optional<Rol> existente = roles.buscar(id);
            if (existente.isEmpty()) {
                return roles.guardar(despues);
            }
            Rol antes = existente.get();
            Permisos.validarCambioRol(actor.rolId(), antes, despues).ifPresent(mensaje -> {
                throw new ErrorNegocio(CodigoError.CONFLICTO, mensaje);
            });
            if (ProteccionGestores.gestionaRoles(antes.permisos()) && !ProteccionGestores.gestionaRoles(conDependencias)
                    && gestores.gestoresTrasEditarRol(id, conDependencias) == 0) {
                throw new ErrorNegocio(CodigoError.CONFLICTO, ProteccionGestores.MENSAJE);
            }
            return roles.guardar(despues);
        });
    }

    public void eliminar(UUID id) {
        transaccion.escribir(() -> {
            bloqueo.bloquear();
            roles.buscar(id).orElseThrow(() -> new ErrorNegocio(CodigoError.NO_ENCONTRADO, "El rol no existe."));
            List<Usuario> conEseRol = usuarios.porRol(id);
            if (!Permisos.puedeEliminarRol(id, conEseRol)) {
                // Incluye el rol del propio actor: él es uno de sus usuarios.
                throw new ErrorNegocio(CodigoError.CONFLICTO,
                        "No se puede eliminar un rol que tiene usuarios asignados.");
            }
            roles.eliminar(id);
        });
    }

    private Usuario actor(UUID actorId) {
        return usuarios.buscar(actorId)
                .orElseThrow(() -> new ErrorNegocio(CodigoError.NO_AUTENTICADO, "Inicia sesión para continuar."));
    }
}

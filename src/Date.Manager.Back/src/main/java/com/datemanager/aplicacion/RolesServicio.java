package com.datemanager.aplicacion;

import java.util.List;
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
import com.datemanager.infraestructura.persistencia.RolEntidad;
import com.datemanager.infraestructura.persistencia.RolRepositorio;
import com.datemanager.infraestructura.persistencia.UsuarioEntidad;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de roles del panel. Las protecciones se comprueban aquí, dentro de la transacción. */
@Service
public class RolesServicio {

    private final RolRepositorio roles;
    private final UsuarioRepositorio usuarios;
    private final BloqueoAdministracion bloqueo;

    public RolesServicio(RolRepositorio roles, UsuarioRepositorio usuarios, BloqueoAdministracion bloqueo) {
        this.roles = roles;
        this.usuarios = usuarios;
        this.bloqueo = bloqueo;
    }

    @Transactional(readOnly = true)
    public List<Rol> listar() {
        return roles.findAll(Sort.by("nombre", "id")).stream().map(RolEntidad::aDominio).toList();
    }

    /**
     * Crea el rol con ese id si no existe; si existe, lo reemplaza. Los permisos se guardan con sus
     * dependencias ({@code gestionar_citas} o {@code atender_citas} implican {@code ver_citas}).
     *
     * <p>No hace falta comprobar aquí «no dejar al sistema sin gestor de roles»: para llegar hasta aquí el actor
     * tiene {@code gestionar_roles} en su propio rol, así que mientras ese rol lo conserve (lo que impone
     * {@link Permisos#validarCambioRol}) siempre queda al menos un usuario con ese permiso, él mismo.
     */
    @Transactional
    public Rol guardar(UUID id, String nombre, String descripcion, Set<Permiso> permisos, UUID actorId) {
        bloqueo.bloquear();
        Set<Permiso> conDependencias = Permisos.conDependencias(permisos);
        String nombreFinal = nombre.strip();
        String descripcionFinal = descripcion == null ? "" : descripcion.strip();

        Optional<RolEntidad> existente = roles.findById(id);
        if (existente.isEmpty()) {
            return roles.saveAndFlush(new RolEntidad(id, nombreFinal, descripcionFinal, conDependencias)).aDominio();
        }
        RolEntidad entidad = existente.get();
        Rol antes = entidad.aDominio();
        Rol despues = new Rol(id, nombreFinal, descripcionFinal, conDependencias);
        UsuarioEntidad actor = actor(actorId);
        Permisos.validarCambioRol(actor.getRolId(), antes, despues).ifPresent(mensaje -> {
            throw new ErrorNegocio(CodigoError.CONFLICTO, mensaje);
        });
        entidad.setNombre(nombreFinal);
        entidad.setDescripcion(descripcionFinal);
        entidad.setPermisos(conDependencias);
        return roles.saveAndFlush(entidad).aDominio();
    }

    @Transactional
    public void eliminar(UUID id) {
        bloqueo.bloquear();
        RolEntidad rol = roles.findById(id)
                .orElseThrow(() -> new ErrorNegocio(CodigoError.NO_ENCONTRADO, "El rol no existe."));
        List<Usuario> conEseRol = usuarios.findByRolId(id).stream().map(UsuarioEntidad::aDominio).toList();
        if (!Permisos.puedeEliminarRol(id, conEseRol)) {
            // Incluye el rol del propio actor: él es uno de sus usuarios.
            throw new ErrorNegocio(CodigoError.CONFLICTO,
                    "No se puede eliminar un rol que tiene usuarios asignados.");
        }
        roles.delete(rol);
        roles.flush();
    }

    private UsuarioEntidad actor(UUID actorId) {
        return usuarios.findById(actorId)
                .orElseThrow(() -> new ErrorNegocio(CodigoError.NO_AUTENTICADO, "Inicia sesión para continuar."));
    }
}

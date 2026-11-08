package com.datemanager.aplicacion;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.Permiso;
import com.datemanager.dominio.Permisos;
import com.datemanager.dominio.Usuario;
import com.datemanager.dominio.Validaciones;
import com.datemanager.infraestructura.persistencia.BloqueoAdministracion;
import com.datemanager.infraestructura.persistencia.RolEntidad;
import com.datemanager.infraestructura.persistencia.RolRepositorio;
import com.datemanager.infraestructura.persistencia.UsuarioEntidad;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de usuarios del panel. Las protecciones se comprueban aquí, dentro de la transacción. */
@Service
public class UsuariosServicio {

    static final String MENSAJE_ULTIMO_GESTOR = "Debe quedar al menos un usuario que pueda gestionar roles.";
    private static final String MENSAJE_USUARIO_REPETIDO = "Ya existe otro usuario con ese nombre de usuario.";
    /** BCrypt solo usa los primeros 72 bytes de la clave; más allá, otra clave valdría igual. */
    private static final int MAX_BYTES_CLAVE = 72;

    /** Datos de un usuario a guardar. {@code clave} vacía o nula = conservar la actual (al editar). */
    public record Datos(String nombre, String apellido, String correo, String telefono, String usuario, String clave,
            UUID rolId) {

        /** La clave nunca debe poder salir en un log por accidente. */
        @Override
        public String toString() {
            return "Datos[usuario=" + usuario + ", rolId=" + rolId + "]";
        }
    }

    private final UsuarioRepositorio usuarios;
    private final RolRepositorio roles;
    private final PasswordEncoder codificador;
    private final BloqueoAdministracion bloqueo;

    public UsuariosServicio(UsuarioRepositorio usuarios, RolRepositorio roles, PasswordEncoder codificador,
            BloqueoAdministracion bloqueo) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.codificador = codificador;
        this.bloqueo = bloqueo;
    }

    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return usuarios.findAll(Sort.by("usuario", "id")).stream().map(UsuarioEntidad::aDominio).toList();
    }

    /** Crea el usuario con ese id si no existe; si existe, lo reemplaza (la clave solo cambia si llega una nueva). */
    @Transactional
    public Usuario guardar(UUID id, Datos datos, UUID actorId) {
        bloqueo.bloquear();
        UsuarioEntidad actor = actor(actorId);

        String nombreUsuario = Validaciones.normalizarUsuario(datos.usuario());
        String correo = Validaciones.normalizarCorreo(datos.correo());
        if (!Validaciones.esCorreoValido(correo)) {
            throw new ErrorNegocio(CodigoError.VALIDACION, "El correo no es válido.",
                    Map.of("correo", "Escribe un correo válido, por ejemplo nombre@correo.com."));
        }
        boolean hayClaveNueva = datos.clave() != null && !datos.clave().isBlank();
        if (hayClaveNueva && datos.clave().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_CLAVE) {
            throw new ErrorNegocio(CodigoError.VALIDACION, "La clave es demasiado larga.",
                    Map.of("clave", "La clave no puede pasar de 72 bytes."));
        }
        RolEntidad rolDestino = datos.rolId() == null ? null : roles.findById(datos.rolId()).orElse(null);
        if (rolDestino == null) {
            throw new ErrorNegocio(CodigoError.VALIDACION, "El rol no existe.", Map.of("rolId", "Elige un rol."));
        }

        // Escalada: sin gestionar_roles solo se asignan roles que no excedan los permisos del propio actor.
        Set<Permiso> permisosDelActor = roles.findById(actor.getRolId()).map(RolEntidad::getPermisos)
                .orElse(Set.of());
        if (!Permisos.puedeAsignarRol(permisosDelActor, rolDestino.getPermisos())) {
            throw new ErrorNegocio(CodigoError.SIN_PERMISO,
                    "No puedes asignar un rol con más permisos que los tuyos.");
        }

        UsuarioEntidad existente = usuarios.findById(id).orElse(null);
        if (existente == null && !hayClaveNueva) {
            throw new ErrorNegocio(CodigoError.VALIDACION, "Escribe la clave.", Map.of("clave", "Escribe la clave."));
        }
        usuarios.findByUsuario(nombreUsuario).filter(otro -> !otro.getId().equals(id)).ifPresent(otro -> {
            throw new ErrorNegocio(CodigoError.CONFLICTO, MENSAJE_USUARIO_REPETIDO);
        });

        UsuarioEntidad entidad;
        if (existente == null) {
            entidad = new UsuarioEntidad(id, datos.nombre().strip(), datos.apellido().strip(), correo,
                    textoOpcional(datos.telefono()), nombreUsuario, codificador.encode(datos.clave()),
                    rolDestino.getId());
        } else {
            if (!existente.getRolId().equals(rolDestino.getId())) {
                protegerAlUltimoGestor(existente, rolDestino);
            }
            existente.setNombre(datos.nombre().strip());
            existente.setApellido(datos.apellido().strip());
            existente.setCorreo(correo);
            existente.setTelefono(textoOpcional(datos.telefono()));
            existente.setUsuario(nombreUsuario);
            existente.setRolId(rolDestino.getId());
            if (hayClaveNueva) {
                existente.setClaveHash(codificador.encode(datos.clave()));
            }
            entidad = existente;
        }
        try {
            return usuarios.saveAndFlush(entidad).aDominio();
        } catch (DataIntegrityViolationException e) {
            // La restricción única de la base es la última defensa contra duplicados (p. ej. otra instancia).
            throw new ErrorNegocio(CodigoError.CONFLICTO, MENSAJE_USUARIO_REPETIDO);
        }
    }

    @Transactional
    public void eliminar(UUID id, UUID actorId) {
        bloqueo.bloquear();
        UsuarioEntidad objetivo = usuarios.findById(id)
                .orElseThrow(() -> new ErrorNegocio(CodigoError.NO_ENCONTRADO, "El usuario no existe."));
        if (objetivo.getId().equals(actorId)) {
            throw new ErrorNegocio(CodigoError.CONFLICTO, "No puedes eliminar tu propio usuario.");
        }
        boolean esGestorDeRoles = roles.findById(objetivo.getRolId())
                .map(rol -> rol.getPermisos().contains(Permiso.GESTIONAR_ROLES)).orElse(false);
        if (esGestorDeRoles && otrosGestoresDeRoles(objetivo.getId()) == 0) {
            throw new ErrorNegocio(CodigoError.CONFLICTO, MENSAJE_ULTIMO_GESTOR);
        }
        usuarios.delete(objetivo);
        usuarios.flush();
    }

    /** Cambiar de rol a quien hoy es el último con {@code gestionar_roles} solo vale si el nuevo rol también lo da. */
    private void protegerAlUltimoGestor(UsuarioEntidad existente, RolEntidad rolNuevo) {
        boolean eraGestor = roles.findById(existente.getRolId())
                .map(rol -> rol.getPermisos().contains(Permiso.GESTIONAR_ROLES)).orElse(false);
        boolean seguiraSiendoGestor = rolNuevo.getPermisos().contains(Permiso.GESTIONAR_ROLES);
        if (eraGestor && !seguiraSiendoGestor && otrosGestoresDeRoles(existente.getId()) == 0) {
            throw new ErrorNegocio(CodigoError.CONFLICTO, MENSAJE_ULTIMO_GESTOR);
        }
    }

    /** Cuántos usuarios, sin contar a {@code excluido}, tienen hoy {@code gestionar_roles} por su rol. */
    private long otrosGestoresDeRoles(UUID excluido) {
        Set<UUID> rolesGestores = roles.findAll().stream()
                .filter(rol -> rol.getPermisos().contains(Permiso.GESTIONAR_ROLES))
                .map(RolEntidad::getId)
                .collect(Collectors.toSet());
        return rolesGestores.isEmpty() ? 0 : usuarios.countByIdNotAndRolIdIn(excluido, rolesGestores);
    }

    private static String textoOpcional(String texto) {
        return texto == null ? "" : texto.strip();
    }

    private UsuarioEntidad actor(UUID actorId) {
        return usuarios.findById(actorId)
                .orElseThrow(() -> new ErrorNegocio(CodigoError.NO_AUTENTICADO, "Inicia sesión para continuar."));
    }
}

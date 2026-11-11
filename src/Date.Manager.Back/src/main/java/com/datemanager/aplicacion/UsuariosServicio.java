package com.datemanager.aplicacion;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.Permiso;
import com.datemanager.dominio.Permisos;
import com.datemanager.dominio.Rol;
import com.datemanager.dominio.Usuario;
import com.datemanager.dominio.Validaciones;
import com.datemanager.infraestructura.persistencia.BloqueoAdministracion;
import com.datemanager.infraestructura.persistencia.EnlaceRepositorio;
import com.datemanager.infraestructura.persistencia.ErrorSql;
import com.datemanager.infraestructura.persistencia.ErroresBase;
import com.datemanager.infraestructura.persistencia.RolRepositorio;
import com.datemanager.infraestructura.persistencia.Transaccion;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio;

/** Casos de uso de usuarios del panel. Las protecciones se comprueban aquí, dentro de la transacción. */
public class UsuariosServicio {

    private static final String MENSAJE_USUARIO_REPETIDO = "Ya existe otro usuario con ese nombre de usuario.";
    private static final String MENSAJE_CREO_ENLACES =
            "No se puede eliminar un usuario que creó enlaces de invitación.";
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

    private final Transaccion transaccion;
    private final UsuarioRepositorio usuarios;
    private final RolRepositorio roles;
    private final CodificadorClave codificador;
    private final BloqueoAdministracion bloqueo;
    private final ProteccionGestores gestores;
    private final EnlaceRepositorio enlaces;

    public UsuariosServicio(Transaccion transaccion, UsuarioRepositorio usuarios, RolRepositorio roles,
            CodificadorClave codificador, BloqueoAdministracion bloqueo, EnlaceRepositorio enlaces) {
        this.transaccion = Objects.requireNonNull(transaccion);
        this.usuarios = Objects.requireNonNull(usuarios);
        this.roles = Objects.requireNonNull(roles);
        this.codificador = Objects.requireNonNull(codificador);
        this.bloqueo = Objects.requireNonNull(bloqueo);
        this.enlaces = Objects.requireNonNull(enlaces);
        this.gestores = new ProteccionGestores(roles, usuarios);
    }

    public List<Usuario> listar() {
        return transaccion.leer(usuarios::todos);
    }

    /** Crea el usuario con ese id si no existe; si existe, lo reemplaza (la clave solo cambia si llega una nueva). */
    public Usuario guardar(UUID id, Datos datos, UUID actorId) {
        return transaccion.escribir(() -> {
            bloqueo.bloquear();
            Usuario actor = actor(actorId);

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
            Rol rolDestino = datos.rolId() == null ? null : roles.buscar(datos.rolId()).orElse(null);
            if (rolDestino == null) {
                throw new ErrorNegocio(CodigoError.VALIDACION, "El rol no existe.", Map.of("rolId", "Elige un rol."));
            }

            // Escalada: sin gestionar_roles solo se asignan roles que no excedan los permisos del propio actor.
            Set<Permiso> permisosDelActor = permisosDe(actor);
            if (!Permisos.puedeAsignarRol(permisosDelActor, rolDestino.permisos())) {
                throw new ErrorNegocio(CodigoError.SIN_PERMISO,
                        "No puedes asignar un rol con más permisos que los tuyos.");
            }

            Usuario existente = usuarios.buscar(id).orElse(null);
            if (existente == null && !hayClaveNueva) {
                throw new ErrorNegocio(CodigoError.VALIDACION, "Escribe la clave.",
                        Map.of("clave", "Escribe la clave."));
            }
            usuarios.buscarPorUsuario(nombreUsuario).filter(otro -> !otro.usuario().id().equals(id))
                    .ifPresent(otro -> {
                        throw new ErrorNegocio(CodigoError.CONFLICTO, MENSAJE_USUARIO_REPETIDO);
                    });

            if (existente != null && !existente.rolId().equals(rolDestino.id())) {
                protegerAlUltimoGestor(existente, rolDestino);
                // Tampoco se toca a quien hoy tiene un rol más poderoso que el del actor: bajarle el rol y a la vez
                // cambiarle la clave sería adueñarse de su cuenta. Se lee su rol ACTUAL, ya bajo el bloqueo.
                exigirQuePuedaTratarloElActor(permisosDelActor, existente);
            }
            Usuario guardado = new Usuario(id, datos.nombre().strip(), datos.apellido().strip(), correo,
                    textoOpcional(datos.telefono()), nombreUsuario, rolDestino.id());
            String claveHash = hayClaveNueva ? codificador.codificar(datos.clave()) : null;
            try {
                return usuarios.guardar(guardado, claveHash);
            } catch (ErrorSql e) {
                // La restricción única de la base es la última defensa contra duplicados (p. ej. otra instancia).
                if (ErroresBase.esViolacionDeRestriccion(e)) {
                    throw new ErrorNegocio(CodigoError.CONFLICTO, MENSAJE_USUARIO_REPETIDO);
                }
                throw e;
            }
        });
    }

    public void eliminar(UUID id, UUID actorId) {
        transaccion.escribir(() -> {
            bloqueo.bloquear();
            Usuario objetivo = usuarios.buscar(id)
                    .orElseThrow(() -> new ErrorNegocio(CodigoError.NO_ENCONTRADO, "El usuario no existe."));
            if (objetivo.id().equals(actorId)) {
                throw new ErrorNegocio(CodigoError.CONFLICTO, "No puedes eliminar tu propio usuario.");
            }
            boolean esGestorDeRoles = ProteccionGestores.gestionaRoles(permisosDe(objetivo));
            if (esGestorDeRoles && gestores.otrosGestores(objetivo.id()) == 0) {
                throw new ErrorNegocio(CodigoError.CONFLICTO, ProteccionGestores.MENSAJE);
            }
            exigirQuePuedaTratarloElActor(permisosDe(actor(actorId)), objetivo);
            // Después de las protecciones de siempre (mismo orden y mismos códigos que antes): los enlaces guardan
            // quién los creó y la llave foránea no tiene cascada.
            if (enlaces.existeCreadoPor(objetivo.id())) {
                throw new ErrorNegocio(CodigoError.CONFLICTO, MENSAJE_CREO_ENLACES);
            }
            try {
                usuarios.eliminar(objetivo.id());
            } catch (ErrorSql e) {
                // Un enlace creado por él justo ahora (crear enlaces no toma el bloqueo de administración).
                if (ErroresBase.esViolacionDeRestriccion(e)) {
                    throw new ErrorNegocio(CodigoError.CONFLICTO, MENSAJE_CREO_ENLACES);
                }
                throw e;
            }
        });
    }

    /** Cambiar de rol a quien hoy es el último con {@code gestionar_roles} solo vale si el nuevo rol también lo da. */
    private void protegerAlUltimoGestor(Usuario existente, Rol rolNuevo) {
        boolean eraGestor = ProteccionGestores.gestionaRoles(permisosDe(existente));
        boolean seguiraSiendoGestor = ProteccionGestores.gestionaRoles(rolNuevo.permisos());
        if (eraGestor && !seguiraSiendoGestor && gestores.otrosGestores(existente.id()) == 0) {
            throw new ErrorNegocio(CodigoError.CONFLICTO, ProteccionGestores.MENSAJE);
        }
    }

    /** Sin {@code gestionar_roles}, no se edita ni elimina a quien hoy tiene un rol con más permisos que el actor. */
    private void exigirQuePuedaTratarloElActor(Set<Permiso> permisosDelActor, Usuario objetivo) {
        if (!Permisos.puedeAsignarRol(permisosDelActor, permisosDe(objetivo))) {
            throw new ErrorNegocio(CodigoError.SIN_PERMISO,
                    "No puedes modificar a un usuario cuyo rol tiene más permisos que los tuyos.");
        }
    }

    /** Permisos del rol ACTUAL del usuario, leídos ahora (bajo el bloqueo), no los del token. */
    private Set<Permiso> permisosDe(Usuario usuario) {
        return roles.buscar(usuario.rolId()).map(Rol::permisos).orElse(Set.of());
    }

    private static String textoOpcional(String texto) {
        return texto == null ? "" : texto.strip();
    }

    private Usuario actor(UUID actorId) {
        return usuarios.buscar(actorId)
                .orElseThrow(() -> new ErrorNegocio(CodigoError.NO_AUTENTICADO, "Inicia sesión para continuar."));
    }
}

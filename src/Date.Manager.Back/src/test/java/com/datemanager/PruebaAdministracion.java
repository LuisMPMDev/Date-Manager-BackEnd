package com.datemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.datemanager.aplicacion.RolesServicio;
import com.datemanager.aplicacion.UsuariosServicio;
import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.Permiso;
import com.datemanager.dominio.Rol;
import com.datemanager.dominio.Usuario;
import com.datemanager.infraestructura.persistencia.BloqueoAdministracion;
import com.datemanager.infraestructura.persistencia.EnlaceRepositorio;
import com.datemanager.infraestructura.persistencia.RolRepositorio;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.function.Executable;

/** Base de las pruebas de roles y usuarios: repositorios, servicios y atajos para sembrar datos. */
public abstract class PruebaAdministracion extends PruebaBase {

    protected RolRepositorio roles;
    protected UsuarioRepositorio usuarios;
    protected EnlaceRepositorio enlaces;
    protected BloqueoAdministracion bloqueo;
    protected CodificadorFalso codificador;
    protected RolesServicio rolesServicio;
    protected UsuariosServicio usuariosServicio;

    @BeforeEach
    void armarAdministracion() {
        roles = new RolRepositorio(tx);
        usuarios = new UsuarioRepositorio(tx);
        enlaces = new EnlaceRepositorio(tx);
        bloqueo = new BloqueoAdministracion(tx);
        codificador = new CodificadorFalso();
        rolesServicio = new RolesServicio(tx, roles, usuarios, bloqueo);
        usuariosServicio = new UsuariosServicio(tx, usuarios, roles, codificador, bloqueo, enlaces);
    }

    protected UUID crearRol(String nombre, Permiso... permisos) {
        Set<Permiso> conjunto = permisos.length == 0 ? Set.of() : EnumSet.of(permisos[0], permisos);
        UUID id = nuevoId();
        tx.escribir(() -> roles.guardar(new Rol(id, nombre, "", conjunto)));
        return id;
    }

    /** Crea un usuario con la clave {@code "clave"} (hash del codificador falso). */
    protected UUID crearUsuario(String usuario, UUID rolId) {
        UUID id = nuevoId();
        tx.escribir(() -> usuarios.guardar(
                new Usuario(id, "Nombre", "Apellido", usuario + "@correo.com", "", usuario, rolId),
                codificador.codificar("clave")));
        return id;
    }

    protected static String hashDe(UUID usuarioId) {
        return consultarTexto("SELECT clave_hash FROM usuario WHERE id = ?", usuarioId);
    }

    protected static int filasConUsuario(String usuario) {
        return consultarInt("SELECT COUNT(*) FROM usuario WHERE usuario = ?", usuario);
    }

    protected static int gestoresDeRoles() {
        return consultarInt("""
                SELECT COUNT(*) FROM usuario u
                WHERE EXISTS (SELECT 1 FROM rol_permiso rp WHERE rp.rol_id = u.rol_id AND rp.permiso = 'gestionar_roles')
                """);
    }

    /** Ejecuta y exige un {@link ErrorNegocio} con ese código; lo devuelve para mirar el mensaje o los campos. */
    protected static ErrorNegocio exigirError(CodigoError codigo, Executable accion) {
        ErrorNegocio error = assertThrows(ErrorNegocio.class, accion);
        assertEquals(codigo, error.codigo(), "código de error (mensaje: " + error.getMessage() + ")");
        return error;
    }

    /**
     * Lanza las tareas a la vez, cada una en su hilo, y devuelve por cada una su resultado o la excepción que lanzó
     * (en el mismo orden).
     */
    @SafeVarargs
    protected static List<Object> enParalelo(Callable<?>... tareas) throws InterruptedException {
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(tareas.length);
        try {
            List<Future<Object>> futuros = new ArrayList<>();
            for (Callable<?> tarea : tareas) {
                futuros.add(hilos.submit(() -> {
                    salida.await();
                    return tarea.call();
                }));
            }
            salida.countDown();
            List<Object> resultados = new ArrayList<>();
            for (Future<Object> futuro : futuros) {
                try {
                    resultados.add(futuro.get());
                } catch (ExecutionException e) {
                    resultados.add(e.getCause());
                }
            }
            return resultados;
        } finally {
            hilos.shutdownNow();
        }
    }
}

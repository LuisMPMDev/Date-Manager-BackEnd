package com.datemanager.infraestructura.persistencia;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pool de conexiones JDBC de tamaño fijo. Las conexiones se abren cuando hacen falta, hasta {@code tamano}; quien
 * pide una con {@link #prestar()} espera como mucho {@value #ESPERA_POR_DEFECTO_SEGUNDOS} segundos a que haya una
 * libre y, si no, recibe {@link IllegalStateException}. Al {@link #devolver(Connection)} se revierte lo que quedara
 * pendiente y se restablece el modo de confirmación y el aislamiento; una conexión cerrada o rota se descarta (su
 * lugar queda libre y la siguiente petición abre otra).
 *
 * <p>Los mensajes de error no llevan la URL, el usuario ni la clave.
 */
public final class Pool {

    private static final Logger LOG = Logger.getLogger(Pool.class.getName());

    static final int ESPERA_POR_DEFECTO_SEGUNDOS = 10;
    /** Una conexión inactiva más tiempo que esto se comprueba antes de prestarla (el servidor pudo cerrarla). */
    private static final long INACTIVIDAD_A_VALIDAR_NANOS = Duration.ofSeconds(30).toNanos();
    private static final int SEGUNDOS_DE_VALIDACION = 3;
    private static final int SEGUNDOS_PARA_ABRIR = 15;

    private record Inactiva(Connection conexion, long desdeNanos) {
    }

    /** Cómo se abre una conexión nueva. */
    @FunctionalInterface
    interface Abridor {
        Connection abrir() throws SQLException;
    }

    private final Abridor abridor;
    private final int tamano;
    private final Duration espera;
    private final Semaphore permisos;
    private final Object cerrojo = new Object();
    /** Conexiones libres, la más reciente primero; protegido por {@link #cerrojo}. */
    private final Deque<Inactiva> libres = new ArrayDeque<>();
    /** Conexiones prestadas (por identidad); protegido por {@link #cerrojo}. */
    private final Set<Connection> prestadas = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean cerrado;

    public Pool(String url, String usuario, String clave, int tamano) {
        this(url, usuario, clave, tamano, Duration.ofSeconds(ESPERA_POR_DEFECTO_SEGUNDOS));
    }

    /** Como el anterior, con otro plazo de espera (para pruebas). */
    public Pool(String url, String usuario, String clave, int tamano, Duration espera) {
        this(() -> abrirConDriver(url, usuario, clave), tamano, espera);
    }

    /** Con otra forma de abrir conexiones (para probar el pool con conexiones que fallan). */
    Pool(Abridor abridor, int tamano, Duration espera) {
        if (tamano < 1) {
            throw new IllegalArgumentException("El tamaño del pool debe ser al menos 1.");
        }
        this.abridor = abridor;
        this.tamano = tamano;
        this.espera = espera;
        this.permisos = new Semaphore(tamano, true);
    }

    /**
     * Presta una conexión (confirmación automática, aislamiento por defecto). Quien la pide debe devolverla con
     * {@link #devolver(Connection)}, siempre (en un {@code finally}).
     *
     * @throws IllegalStateException si el pool está cerrado, si no hay conexión libre en el plazo o si no se pudo abrir
     */
    public Connection prestar() {
        if (estaCerrado()) {
            throw new IllegalStateException("El pool de conexiones está cerrado.");
        }
        try {
            if (!permisos.tryAcquire(espera.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new IllegalStateException("No hay conexiones disponibles en el pool (tamaño " + tamano
                        + "): se agotó la espera de " + espera.toSeconds() + " s.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la espera de una conexión del pool.", e);
        }
        Connection conexion = null;
        try {
            conexion = tomarLibreOAbrir();
            synchronized (cerrojo) {
                if (cerrado) {
                    throw new IllegalStateException("El pool de conexiones está cerrado.");
                }
                prestadas.add(conexion);
            }
            return conexion;
        } catch (RuntimeException | Error e) {
            cerrarSilencioso(conexion);
            permisos.release();
            throw e;
        }
    }

    /**
     * Devuelve una conexión prestada. Si quedó una transacción abierta se revierte; se restablece la confirmación
     * automática y el aislamiento. Una conexión cerrada, rota o que no se puede restablecer se cierra y se descarta.
     * Devolver {@code null}, una conexión que no está prestada o la misma dos veces no tiene efecto.
     */
    public void devolver(Connection conexion) {
        if (conexion == null) {
            return;
        }
        synchronized (cerrojo) {
            if (!prestadas.remove(conexion)) {
                return;
            }
        }
        try {
            boolean sana = restablecer(conexion);
            synchronized (cerrojo) {
                if (sana && !cerrado) {
                    libres.addFirst(new Inactiva(conexion, System.nanoTime()));
                    conexion = null;
                }
            }
        } finally {
            cerrarSilencioso(conexion);
            permisos.release();
        }
    }

    /** Cuántas conexiones se pueden prestar ahora mismo sin esperar (el tamaño menos las prestadas). */
    public int disponibles() {
        return permisos.availablePermits();
    }

    /** Cierra las conexiones libres y no presta más; las prestadas se cierran cuando se devuelvan. */
    public void cerrar() {
        Deque<Inactiva> aCerrar;
        synchronized (cerrojo) {
            cerrado = true;
            aCerrar = new ArrayDeque<>(libres);
            libres.clear();
        }
        for (Inactiva i : aCerrar) {
            cerrarSilencioso(i.conexion());
        }
    }

    private boolean estaCerrado() {
        synchronized (cerrojo) {
            return cerrado;
        }
    }

    /** Una libre y sana, o una nueva. Se llama con un permiso ya tomado. */
    private Connection tomarLibreOAbrir() {
        while (true) {
            Inactiva inactiva;
            synchronized (cerrojo) {
                inactiva = libres.pollFirst();
            }
            if (inactiva == null) {
                return abrir();
            }
            if (sigueViva(inactiva)) {
                return inactiva.conexion();
            }
            cerrarSilencioso(inactiva.conexion());
        }
    }

    private static boolean sigueViva(Inactiva inactiva) {
        try {
            if (inactiva.conexion().isClosed()) {
                return false;
            }
            if (System.nanoTime() - inactiva.desdeNanos() > INACTIVIDAD_A_VALIDAR_NANOS) {
                return inactiva.conexion().isValid(SEGUNDOS_DE_VALIDACION);
            }
            return true;
        } catch (SQLException | RuntimeException e) {
            return false;
        }
    }

    private static Connection abrirConDriver(String url, String usuario, String clave) throws SQLException {
        Properties propiedades = new Properties();
        propiedades.setProperty("user", usuario);
        propiedades.setProperty("password", clave);
        propiedades.setProperty("loginTimeout", String.valueOf(SEGUNDOS_PARA_ABRIR));
        return DriverManager.getConnection(url, propiedades);
    }

    private Connection abrir() {
        try {
            Connection conexion = abridor.abrir();
            conexion.setAutoCommit(true);
            return conexion;
        } catch (SQLException e) {
            // La causa conserva el detalle del driver; el mensaje propio no lleva URL ni credenciales.
            throw new IllegalStateException("No se pudo abrir una conexión a la base de datos (código "
                    + e.getErrorCode() + ").", e);
        }
    }

    /** Deja la conexión como nueva; {@code false} si está cerrada o si algo falla (hay que descartarla). */
    private static boolean restablecer(Connection conexion) {
        try {
            if (conexion.isClosed()) {
                return false;
            }
            // Primero revertir: cambiar a confirmación automática con una transacción abierta la confirmaría.
            if (!conexion.getAutoCommit()) {
                conexion.rollback();
            }
            conexion.setAutoCommit(true);
            if (conexion.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                conexion.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            }
            if (conexion.isReadOnly()) {
                conexion.setReadOnly(false);
            }
            return true;
        } catch (SQLException | RuntimeException e) {
            LOG.log(Level.FINE, "Se descarta una conexión que no se pudo restablecer: {0}", e.getClass().getSimpleName());
            return false;
        }
    }

    private static void cerrarSilencioso(Connection conexion) {
        if (conexion == null) {
            return;
        }
        try {
            conexion.close();
        } catch (SQLException | RuntimeException e) {
            // Ya se descarta; no hay nada más que hacer.
        }
    }
}

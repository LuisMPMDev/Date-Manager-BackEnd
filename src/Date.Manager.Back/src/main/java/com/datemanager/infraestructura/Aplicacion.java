package com.datemanager.infraestructura;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import com.datemanager.api.AuthControlador;
import com.datemanager.api.CitasControlador;
import com.datemanager.api.ClientesControlador;
import com.datemanager.api.EnlacesControlador;
import com.datemanager.api.InicioControlador;
import com.datemanager.api.PublicoControlador;
import com.datemanager.api.RolesControlador;
import com.datemanager.api.ServiciosControlador;
import com.datemanager.api.UsuariosControlador;
import com.datemanager.aplicacion.AutenticacionServicio;
import com.datemanager.aplicacion.CitasServicio;
import com.datemanager.aplicacion.ClientesServicio;
import com.datemanager.aplicacion.CodificadorClave;
import com.datemanager.aplicacion.EnlacesServicio;
import com.datemanager.aplicacion.InicioServicio;
import com.datemanager.aplicacion.RolesServicio;
import com.datemanager.aplicacion.ServiciosServicio;
import com.datemanager.aplicacion.UsuariosServicio;
import com.datemanager.infraestructura.http.Enrutador;
import com.datemanager.infraestructura.http.Ruta;
import com.datemanager.infraestructura.http.ServidorHttp;
import com.datemanager.infraestructura.openapi.SwaggerUi;
import com.datemanager.infraestructura.persistencia.BloqueoAdministracion;
import com.datemanager.infraestructura.persistencia.CitaRepositorio;
import com.datemanager.infraestructura.persistencia.ClienteRepositorio;
import com.datemanager.infraestructura.persistencia.EnlaceRepositorio;
import com.datemanager.infraestructura.persistencia.Migrador;
import com.datemanager.infraestructura.persistencia.Pool;
import com.datemanager.infraestructura.persistencia.RolRepositorio;
import com.datemanager.infraestructura.persistencia.ServicioRepositorio;
import com.datemanager.infraestructura.persistencia.Transaccion;
import com.datemanager.infraestructura.persistencia.UsuarioRepositorio;
import com.datemanager.infraestructura.persistencia.VentanaRepositorio;
import com.datemanager.infraestructura.seguridad.Autenticador;
import com.datemanager.infraestructura.seguridad.Bcrypt;
import com.datemanager.infraestructura.seguridad.ServicioJwt;

/**
 * Cableado a mano de la aplicación (lo que antes hacía el contenedor de Spring): repositorios, servicios,
 * controladores, rutas y servidor HTTP, y su ciclo de vida.
 *
 * <p>{@link #iniciar()} hace lo que hacía el arranque de Spring, en el mismo orden: migra el esquema (Flyway), siembra
 * los datos de ejemplo si el perfil es {@code dev} y la base está vacía ({@link SemillaDatos}), crea el administrador
 * inicial si no hay usuarios ({@link AdministradorInicial}) y por último abre el puerto. Si algo falla, lanza y el
 * servidor no queda escuchando.
 */
public final class Aplicacion {

    private final Enrutador rutas = new Enrutador();
    private final ServidorHttp servidor;
    private final Pool pool;
    /** Si el pool lo creó esta aplicación (y por tanto lo cierra al detenerse). */
    private final boolean poolPropio;
    private final Migrador migrador;
    private final SemillaDatos semilla;
    private final AdministradorInicial administrador;
    private boolean detenida;

    /**
     * Para producción: crea su propio pool con la configuración y lo cierra en {@link #detener()}.
     *
     * @param reloj reloj en la zona del negocio
     */
    public Aplicacion(Configuracion cfg, Clock reloj) {
        this(cfg, new Pool(cfg.dbUrl(), cfg.dbUsuario(), cfg.dbClave(), cfg.dbPool()), true, reloj, new Bcrypt());
    }

    /**
     * Sobre un pool ajeno: lo crea y lo cierra quien llama.
     *
     * @param reloj reloj en la zona del negocio (las pruebas pasan uno que se puede fijar)
     */
    public Aplicacion(Configuracion cfg, Pool pool, Clock reloj) {
        this(cfg, pool, reloj, new Bcrypt());
    }

    public Aplicacion(Configuracion cfg, Pool pool, Clock reloj, CodificadorClave codificador) {
        this(cfg, pool, false, reloj, codificador);
    }

    private Aplicacion(Configuracion cfg, Pool pool, boolean poolPropio, Clock reloj, CodificadorClave codificador) {
        Objects.requireNonNull(cfg);
        Objects.requireNonNull(reloj);
        this.pool = Objects.requireNonNull(pool);
        this.poolPropio = poolPropio;
        Transaccion tx = new Transaccion(pool);

        VentanaRepositorio ventanas = new VentanaRepositorio(tx);
        ServicioRepositorio serviciosRepo = new ServicioRepositorio(tx, ventanas);
        ClienteRepositorio clientesRepo = new ClienteRepositorio(tx);
        CitaRepositorio citasRepo = new CitaRepositorio(tx);
        UsuarioRepositorio usuarios = new UsuarioRepositorio(tx);
        RolRepositorio roles = new RolRepositorio(tx);
        EnlaceRepositorio enlacesRepo = new EnlaceRepositorio(tx);
        BloqueoAdministracion bloqueo = new BloqueoAdministracion(tx);

        ServicioJwt jwt = new ServicioJwt(cfg.jwtSecreto(), reloj);
        ServiciosServicio servicios = new ServiciosServicio(tx, serviciosRepo);
        ClientesServicio clientes = new ClientesServicio(tx, clientesRepo);
        CitasServicio citas = new CitasServicio(tx, citasRepo, servicios, clientes, reloj);
        AutenticacionServicio autenticacion = new AutenticacionServicio(tx, usuarios, roles, codificador, jwt);
        RolesServicio rolesServicio = new RolesServicio(tx, roles, usuarios, bloqueo);
        UsuariosServicio usuariosServicio = new UsuariosServicio(tx, usuarios, roles, codificador, bloqueo,
                enlacesRepo);
        EnlacesServicio enlaces = new EnlacesServicio(tx, enlacesRepo, usuarios, servicios, citas, reloj);
        InicioServicio inicio = new InicioServicio(tx, citasRepo, clientesRepo, servicios, reloj);

        registrar(new AuthControlador(autenticacion).rutas());
        registrar(new ServiciosControlador(servicios).rutas());
        registrar(new ClientesControlador(clientes).rutas());
        registrar(new CitasControlador(citas).rutas());
        registrar(new RolesControlador(rolesServicio).rutas());
        registrar(new UsuariosControlador(usuariosServicio).rutas());
        registrar(new EnlacesControlador(enlaces).rutas());
        registrar(new PublicoControlador(enlaces).rutas());
        registrar(new InicioControlador(inicio).rutas());
        if (cfg.dev()) {
            // Swagger como antes con springdoc: solo en dev; fuera de dev estas rutas no existen (404).
            registrar(SwaggerUi.rutas(rutas));
        }

        servidor = new ServidorHttp(cfg, rutas, new Autenticador(jwt, usuarios, roles, tx));
        migrador = new Migrador(pool);
        semilla = cfg.dev()
                ? new SemillaDatos(tx, roles, usuarios, serviciosRepo, clientesRepo, citasRepo, codificador, reloj)
                : null;
        administrador = new AdministradorInicial(tx, roles, usuarios, codificador, cfg.adminUsuario(),
                cfg.adminClave());
    }

    private void registrar(List<Ruta> nuevas) {
        nuevas.forEach(rutas::agregar);
    }

    /** La tabla de rutas; se pueden añadir más solo antes de {@link #iniciar()}. */
    public Enrutador rutas() {
        return rutas;
    }

    /** Migra, siembra (solo en {@code dev}), crea el administrador inicial y abre el puerto. */
    public synchronized void iniciar() {
        if (detenida) {
            throw new IllegalStateException("La aplicación ya se detuvo.");
        }
        migrador.migrar();
        if (semilla != null) {
            semilla.sembrarSiVacia();
        }
        administrador.crearSiHaceFalta();
        servidor.iniciar();
    }

    /** El puerto en que escucha (el elegido por el sistema si la configuración pedía el 0). */
    public int puerto() {
        return servidor.puerto();
    }

    /** Cierra el puerto (esperando un poco a las peticiones en curso) y, si es suyo, el pool. Se puede repetir. */
    public synchronized void detener() {
        if (detenida) {
            return;
        }
        detenida = true;
        servidor.detener();
        if (poolPropio) {
            pool.cerrar();
        }
    }
}

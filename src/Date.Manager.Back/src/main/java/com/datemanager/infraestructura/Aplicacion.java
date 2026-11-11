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
import com.datemanager.infraestructura.persistencia.BloqueoAdministracion;
import com.datemanager.infraestructura.persistencia.CitaRepositorio;
import com.datemanager.infraestructura.persistencia.ClienteRepositorio;
import com.datemanager.infraestructura.persistencia.EnlaceRepositorio;
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
 * controladores, rutas y servidor HTTP. El pool lo crea y lo cierra quien llama.
 *
 * <p>Registra todas las rutas de la API (autenticación, panel y zona pública). El ciclo de vida completo (migrar,
 * administrador inicial, semilla) llega en una tarea posterior.
 */
public final class Aplicacion {

    private final Enrutador rutas = new Enrutador();
    private final ServidorHttp servidor;

    /**
     * @param reloj reloj en la zona del negocio (las pruebas pasan uno que se puede fijar)
     */
    public Aplicacion(Configuracion cfg, Pool pool, Clock reloj) {
        this(cfg, pool, reloj, new Bcrypt());
    }

    public Aplicacion(Configuracion cfg, Pool pool, Clock reloj, CodificadorClave codificador) {
        Objects.requireNonNull(cfg);
        Objects.requireNonNull(reloj);
        Transaccion tx = new Transaccion(Objects.requireNonNull(pool));

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

        servidor = new ServidorHttp(cfg, rutas, new Autenticador(jwt, usuarios, roles, tx));
    }

    private void registrar(List<Ruta> nuevas) {
        nuevas.forEach(rutas::agregar);
    }

    /** La tabla de rutas; se pueden añadir más solo antes de {@link #iniciar()}. */
    public Enrutador rutas() {
        return rutas;
    }

    public void iniciar() {
        servidor.iniciar();
    }

    /** El puerto en que escucha (el elegido por el sistema si la configuración pedía el 0). */
    public int puerto() {
        return servidor.puerto();
    }

    public void detener() {
        servidor.detener();
    }
}

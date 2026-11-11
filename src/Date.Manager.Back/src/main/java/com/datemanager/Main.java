package com.datemanager;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.datemanager.infraestructura.Aplicacion;
import com.datemanager.infraestructura.Configuracion;

/**
 * Punto de entrada: lee la configuración de las variables {@code DATEMANAGER_*}, arranca la {@link Aplicacion} y la
 * detiene ordenadamente al apagar la JVM. Si falta una variable obligatoria o el arranque falla (base inaccesible,
 * migración rechazada, clave del administrador inicial inválida...), escribe un mensaje claro, sin valores de las
 * variables, y termina con código 1, como cuando Spring no arrancaba.
 */
public final class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private Main() {
    }

    public static void main(String[] args) {
        Configuracion cfg;
        try {
            cfg = Configuracion.desdeEntorno(System.getenv());
        } catch (IllegalStateException e) {
            // El mensaje nombra la variable, nunca su valor.
            System.err.println("No se puede arrancar Date Manager API: " + e.getMessage());
            System.exit(1);
            return;
        }

        Aplicacion aplicacion = null;
        try {
            aplicacion = new Aplicacion(cfg, Clock.system(cfg.zona()));
            aplicacion.iniciar();
        } catch (Throwable e) {
            // Sin traza de pila ni mensajes crudos: podrían repetir la URL de la base o alguna clave.
            LOG.log(Level.SEVERE, "No se pudo arrancar Date Manager API: {0}", descripcion(e, cfg));
            if (aplicacion != null) {
                try {
                    aplicacion.detener();
                } catch (Throwable ignorada) {
                    // Ya se va a salir con código 1.
                }
            }
            System.exit(1);
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(aplicacion::detener, "apagado"));
        LOG.log(Level.INFO, "Date Manager API escuchando en el puerto {0,number,#}.", aplicacion.puerto());
    }

    /**
     * La cadena de causas como «Clase: mensaje», con los valores de configuración sensibles (URL, usuario y claves)
     * sustituidos por {@code ***}. Sin trazas de pila.
     */
    static String descripcion(Throwable error, Configuracion cfg) {
        List<String> sensibles = new ArrayList<>();
        for (String valor : new String[] {cfg.dbUrl(), cfg.dbUsuario(), cfg.dbClave(), cfg.jwtSecreto(),
                cfg.adminClave()}) {
            if (valor != null && !valor.isBlank()) {
                sensibles.add(valor);
            }
        }
        sensibles.sort(Comparator.comparingInt(String::length).reversed());
        StringBuilder texto = new StringBuilder();
        Throwable actual = error;
        for (int i = 0; actual != null && i < 5; i++, actual = actual.getCause()) {
            if (i > 0) {
                texto.append(" <- ");
            }
            String mensaje = actual.getMessage() == null ? "" : actual.getMessage();
            for (String valor : sensibles) {
                mensaje = mensaje.replace(valor, "***");
            }
            texto.append(actual.getClass().getName());
            if (!mensaje.isBlank()) {
                texto.append(": ").append(mensaje);
            }
        }
        return texto.toString();
    }
}

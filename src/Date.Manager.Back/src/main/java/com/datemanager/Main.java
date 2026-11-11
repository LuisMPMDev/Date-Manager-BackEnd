package com.datemanager;

import java.time.Clock;
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

        Aplicacion aplicacion = new Aplicacion(cfg, Clock.system(cfg.zona()));
        try {
            aplicacion.iniciar();
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "No se pudo arrancar Date Manager API.", e);
            aplicacion.detener();
            System.exit(1);
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(aplicacion::detener, "apagado"));
        LOG.log(Level.INFO, "Date Manager API escuchando en el puerto {0,number,#}.", aplicacion.puerto());
    }
}

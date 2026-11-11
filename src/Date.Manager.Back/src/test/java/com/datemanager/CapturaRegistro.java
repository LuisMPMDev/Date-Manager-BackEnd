package com.datemanager;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Captura lo que registra un {@link Logger} de {@code java.util.logging} (cada línea con su nivel:
 * {@code WARNING}, {@code INFO}...) mientras está abierta; lo que hacía {@code OutputCaptureExtension} en la versión
 * Spring. Con {@code silenciar} el registrador no pasa nada a la consola mientras tanto.
 */
public final class CapturaRegistro extends Handler implements AutoCloseable {

    private final Logger registrador;
    private final Level nivelAntes;
    private final boolean usabaPadresAntes;
    private final StringBuilder texto = new StringBuilder();
    private final SimpleFormatter formato = new SimpleFormatter();

    public CapturaRegistro(Class<?> clase, boolean silenciar) {
        registrador = Logger.getLogger(clase.getName());
        nivelAntes = registrador.getLevel();
        usabaPadresAntes = registrador.getUseParentHandlers();
        setLevel(Level.ALL);
        registrador.setLevel(Level.ALL);
        registrador.addHandler(this);
        if (silenciar) {
            registrador.setUseParentHandlers(false);
        }
    }

    @Override
    public synchronized void publish(LogRecord registro) {
        // Nombre del nivel sin traducir (WARNING, no «ADVERTENCIA» en una JVM en español).
        texto.append(registro.getLevel().getName()).append(": ").append(formato.formatMessage(registro))
                .append(System.lineSeparator());
        if (registro.getThrown() != null) {
            StringWriter traza = new StringWriter();
            registro.getThrown().printStackTrace(new PrintWriter(traza));
            texto.append(traza);
        }
    }

    /** Todo lo capturado hasta ahora. */
    public synchronized String texto() {
        return texto.toString();
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
        registrador.removeHandler(this);
        registrador.setLevel(nivelAntes);
        registrador.setUseParentHandlers(usabaPadresAntes);
    }
}

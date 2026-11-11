package com.datemanager.aplicacion;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.Servicio;
import com.datemanager.dominio.Validaciones;
import com.datemanager.dominio.VentanaDisponibilidad;
import com.datemanager.infraestructura.persistencia.ServicioRepositorio;
import com.datemanager.infraestructura.persistencia.Transaccion;

/** Casos de uso de servicios y sus ventanas de disponibilidad. */
public class ServiciosServicio {

    /** DECIMAL(12,2): hasta 10 dígitos enteros y 2 decimales. */
    private static final int ENTEROS_MAXIMOS = 10;
    private static final int DECIMALES = 2;

    private final Transaccion transaccion;
    private final ServicioRepositorio servicios;

    public ServiciosServicio(Transaccion transaccion, ServicioRepositorio servicios) {
        this.transaccion = Objects.requireNonNull(transaccion);
        this.servicios = Objects.requireNonNull(servicios);
    }

    /** Todos los servicios (también los inactivos) por nombre, cada uno con sus ventanas por día e inicio. */
    public List<Servicio> listar() {
        return transaccion.leer(servicios::todos);
    }

    /** El servicio con sus ventanas; {@code NO_ENCONTRADO} si no existe. */
    public Servicio buscar(UUID id) {
        return transaccion.leer(() -> servicios.buscar(id).orElseThrow(ServiciosServicio::noEncontrado));
    }

    /**
     * Como {@link #buscar}, pero deja bloqueada la fila del servicio (la agenda del servicio) hasta que termine la
     * transacción en curso: nadie más reserva, mueve ni cambia sus ventanas mientras tanto. Exige una transacción
     * abierta ({@link IllegalStateException} si no la hay).
     */
    public Servicio bloquear(UUID id) {
        transaccion.exigirActiva();
        return servicios.bloquear(id).orElseThrow(ServiciosServicio::noEncontrado);
    }

    /**
     * Crea el servicio con ese id si no existe; si existe, lo reemplaza, ventanas incluidas: las ventanas guardadas
     * se borran y se insertan las recibidas, de modo que ningún paso intermedio deja dos ventanas cruzadas y
     * reenviar los mismos ids no choca con la llave.
     * Toma el mismo bloqueo que las reservas, así dos guardados simultáneos (o un guardado y una reserva) no
     * mezclan ventanas.
     */
    public Servicio guardar(Servicio recibido) {
        UUID id = recibido.id();
        validar(recibido);
        BigDecimal valor = recibido.valor().setScale(DECIMALES);
        String nombre = recibido.nombre().strip();
        return transaccion.escribir(() -> {
            servicios.bloquear(id);
            return servicios.guardar(
                    new Servicio(id, nombre, recibido.duracionMin(), recibido.activo(), valor, recibido.ventanas()));
        });
    }

    private static void validar(Servicio s) {
        Set<UUID> vistos = new HashSet<>();
        for (VentanaDisponibilidad v : s.ventanas()) {
            if (v.dia() < 1 || v.dia() > 7) {
                throw invalido("El día de la ventana debe estar entre 1 (lunes) y 7 (domingo).");
            }
            if (!vistos.add(v.id())) {
                throw invalido("Hay ventanas repetidas: cada ventana debe tener un id distinto.");
            }
        }
        Validaciones.errorServicio(s).ifPresent(mensaje -> {
            throw invalido(mensaje);
        });
        BigDecimal valor = s.valor();
        if (valor.stripTrailingZeros().scale() > DECIMALES) {
            throw invalido("El valor admite como máximo dos decimales.");
        }
        if (valor.precision() - valor.scale() > ENTEROS_MAXIMOS) {
            throw invalido("El valor es demasiado grande.");
        }
    }

    private static ErrorNegocio noEncontrado() {
        return new ErrorNegocio(CodigoError.NO_ENCONTRADO, "El servicio no existe.");
    }

    private static ErrorNegocio invalido(String mensaje) {
        return new ErrorNegocio(CodigoError.VALIDACION, mensaje);
    }
}

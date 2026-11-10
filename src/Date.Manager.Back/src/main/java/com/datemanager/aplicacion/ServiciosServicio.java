package com.datemanager.aplicacion;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.Servicio;
import com.datemanager.dominio.Validaciones;
import com.datemanager.dominio.VentanaDisponibilidad;
import com.datemanager.infraestructura.persistencia.ServicioEntidad;
import com.datemanager.infraestructura.persistencia.ServicioRepositorio;
import com.datemanager.infraestructura.persistencia.VentanaEntidad;
import com.datemanager.infraestructura.persistencia.VentanaRepositorio;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de servicios y sus ventanas de disponibilidad. */
@Service
public class ServiciosServicio {

    /** DECIMAL(12,2): hasta 10 dígitos enteros y 2 decimales. */
    private static final int ENTEROS_MAXIMOS = 10;
    private static final int DECIMALES = 2;

    private final ServicioRepositorio servicios;
    private final VentanaRepositorio ventanas;

    public ServiciosServicio(ServicioRepositorio servicios, VentanaRepositorio ventanas) {
        this.servicios = servicios;
        this.ventanas = ventanas;
    }

    /** Todos los servicios (también los inactivos) por nombre, cada uno con sus ventanas por día e inicio. */
    @Transactional(readOnly = true)
    public List<Servicio> listar() {
        List<ServicioEntidad> todos = servicios.findAll(Sort.by("nombre", "id"));
        if (todos.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<VentanaDisponibilidad>> porServicio = ventanas
                .findByServicioIdInOrderByDiaAscInicioAscIdAsc(todos.stream().map(ServicioEntidad::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(VentanaEntidad::getServicioId,
                        Collectors.mapping(VentanaEntidad::aDominio, Collectors.toList())));
        return todos.stream().map(s -> s.aDominio(porServicio.getOrDefault(s.getId(), List.of()))).toList();
    }

    /** El servicio con sus ventanas; {@code NO_ENCONTRADO} si no existe. */
    @Transactional(readOnly = true)
    public Servicio buscar(UUID id) {
        ServicioEntidad entidad = servicios.findById(id)
                .orElseThrow(() -> new ErrorNegocio(CodigoError.NO_ENCONTRADO, "El servicio no existe."));
        return entidad.aDominio(ventanasDe(id));
    }

    /**
     * Como {@link #buscar}, pero deja bloqueada la fila del servicio (la agenda del servicio) hasta que termine la
     * transacción en curso: nadie más reserva, mueve ni cambia sus ventanas mientras tanto.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Servicio bloquear(UUID id) {
        ServicioEntidad entidad = servicios.bloquear(id)
                .orElseThrow(() -> new ErrorNegocio(CodigoError.NO_ENCONTRADO, "El servicio no existe."));
        return entidad.aDominio(ventanasDe(id));
    }

    /**
     * Crea el servicio con ese id si no existe; si existe, lo reemplaza, ventanas incluidas: las ventanas guardadas
     * se borran (con flush) y se insertan las recibidas, de modo que ningún paso intermedio deja dos ventanas
     * cruzadas y reenviar los mismos ids no choca con la llave.
     * Toma el mismo bloqueo que las reservas, así dos guardados simultáneos (o un guardado y una reserva) no
     * mezclan ventanas.
     */
    @Transactional
    public Servicio guardar(Servicio recibido) {
        UUID id = recibido.id();
        validar(recibido);
        BigDecimal valor = recibido.valor().setScale(DECIMALES);
        String nombre = recibido.nombre().strip();

        ServicioEntidad entidad = servicios.bloquear(id).orElse(null);
        if (entidad == null) {
            entidad = new ServicioEntidad(id, nombre, recibido.duracionMin(), recibido.activo(), valor);
        } else {
            entidad.actualizar(nombre, recibido.duracionMin(), recibido.activo(), valor);
        }
        servicios.save(entidad);

        // Se borran todas, se vacía (flush) y luego se insertan las recibidas: Hibernate escribe los inserts antes que
        // los borrados, y el disparador tr_ventana_sin_cruce rechazaría cualquier paso intermedio con ventanas
        // cruzadas. Como el conjunto final no tiene cruces (validar), ningún subconjunto lo tiene. El flush también
        // evita que reenviar los mismos ids choque con la llave (servicio, id).
        ventanas.deleteAll(ventanas.findByServicioIdOrderByDiaAscInicioAscIdAsc(id));
        ventanas.flush();
        ventanas.saveAll(recibido.ventanas().stream()
                .map(v -> new VentanaEntidad(id, v.id(), v.dia(), v.inicio(), v.fin()))
                .toList());
        servicios.flush();
        return entidad.aDominio(ventanasDe(id));
    }

    private List<VentanaDisponibilidad> ventanasDe(UUID servicioId) {
        return ventanas.findByServicioIdOrderByDiaAscInicioAscIdAsc(servicioId).stream()
                .map(VentanaEntidad::aDominio).toList();
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

    private static ErrorNegocio invalido(String mensaje) {
        return new ErrorNegocio(CodigoError.VALIDACION, mensaje);
    }
}

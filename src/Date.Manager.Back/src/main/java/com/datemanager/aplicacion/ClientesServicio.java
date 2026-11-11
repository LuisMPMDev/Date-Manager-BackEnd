package com.datemanager.aplicacion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.datemanager.dominio.Cliente;
import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.DatosCliente;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.dominio.Validaciones;
import com.datemanager.infraestructura.persistencia.ClienteRepositorio;
import com.datemanager.infraestructura.persistencia.ErrorSql;
import com.datemanager.infraestructura.persistencia.ErroresBase;
import com.datemanager.infraestructura.persistencia.Transaccion;

/** Casos de uso de clientes. Un cliente se reconoce por su correo normalizado (trim + minúsculas). */
public class ClientesServicio {

    private static final String MENSAJE_CORREO_REPETIDO = "Ya existe otro cliente con ese correo.";

    private final Transaccion transaccion;
    private final ClienteRepositorio clientes;

    public ClientesServicio(Transaccion transaccion, ClienteRepositorio clientes) {
        this.transaccion = Objects.requireNonNull(transaccion);
        this.clientes = Objects.requireNonNull(clientes);
    }

    /** Todos los clientes por nombre y apellido. */
    public List<Cliente> listar() {
        return transaccion.leer(clientes::todos);
    }

    /** Edita un cliente existente (el panel no crea clientes: nacen al reservar). */
    public Cliente guardar(UUID id, DatosCliente datos) {
        validar(datos);
        return transaccion.escribir(() -> {
            clientes.buscar(id).orElseThrow(() -> new ErrorNegocio(CodigoError.NO_ENCONTRADO, "El cliente no existe."));
            String correo = Validaciones.normalizarCorreo(datos.correo());
            clientes.porCorreo(correo).filter(otro -> !otro.id().equals(id)).ifPresent(otro -> {
                throw new ErrorNegocio(CodigoError.CONFLICTO, MENSAJE_CORREO_REPETIDO);
            });
            try {
                return clientes.guardar(new Cliente(id, datos.nombre().strip(), datos.apellido().strip(),
                        datos.telefono().strip(), correo));
            } catch (ErrorSql e) {
                // El único de la base es la última defensa (p. ej. una reserva que creó ese correo a la vez).
                if (ErroresBase.esViolacionDeRestriccion(e)) {
                    throw new ErrorNegocio(CodigoError.CONFLICTO, MENSAJE_CORREO_REPETIDO);
                }
                throw e;
            }
        });
    }

    /**
     * Busca al cliente por correo normalizado y le actualiza nombre, apellido y teléfono, o lo crea si no existe
     * (regla de la demo). Bloquea el correo, así que dos reservas simultáneas con el mismo correo nuevo crean un
     * solo cliente. Debe llamarse dentro de la transacción de la reserva ({@link IllegalStateException} si no la hay).
     */
    public Cliente obtenerOCrear(DatosCliente datos) {
        transaccion.exigirActiva();
        validar(datos);
        String correo = Validaciones.normalizarCorreo(datos.correo());
        String nombre = datos.nombre().strip();
        String apellido = datos.apellido().strip();
        String telefono = datos.telefono().strip();
        UUID id = clientes.porCorreoBloqueando(correo).map(Cliente::id).orElseGet(UUID::randomUUID);
        return clientes.guardar(new Cliente(id, nombre, apellido, telefono, correo));
    }

    /**
     * Datos obligatorios, correo válido y largos que caben en la base. Falla con {@code VALIDACION} y los campos
     * inválidos (los mensajes de la demo).
     */
    public static void validar(DatosCliente datos) {
        Map<String, String> campos = new LinkedHashMap<>();
        if (datos == null) {
            throw new ErrorNegocio(CodigoError.VALIDACION, "Faltan los datos del cliente.",
                    Map.of("cliente", "Escribe los datos del cliente."));
        }
        obligatorio(campos, "nombre", datos.nombre(), 100, "Escribe el nombre.", "El nombre es demasiado largo.");
        obligatorio(campos, "apellido", datos.apellido(), 100, "Escribe el apellido.",
                "El apellido es demasiado largo.");
        obligatorio(campos, "telefono", datos.telefono(), 40, "Escribe el teléfono.",
                "El teléfono es demasiado largo.");
        if (obligatorio(campos, "correo", datos.correo(), 254, "Escribe el correo.", "El correo es demasiado largo.")
                && !Validaciones.esCorreoValido(datos.correo())) {
            campos.put("correo", "Escribe un correo válido, por ejemplo nombre@correo.com.");
        }
        if (!campos.isEmpty()) {
            throw new ErrorNegocio(CodigoError.VALIDACION, campos.values().iterator().next(), campos);
        }
    }

    /** {@code true} si el texto está y cabe; si no, anota el error del campo. */
    private static boolean obligatorio(Map<String, String> campos, String campo, String texto, int maximo,
            String vacio, String largo) {
        if (texto == null || texto.isBlank()) {
            campos.put(campo, vacio);
            return false;
        }
        if (texto.strip().length() > maximo) {
            campos.put(campo, largo);
            return false;
        }
        return true;
    }
}

package com.datemanager.dominio;

/** Datos que da quien reserva; el cliente se reconoce por el correo normalizado (porta {@code DatosCliente} de la demo). */
public record DatosCliente(String nombre, String apellido, String telefono, String correo) {
}

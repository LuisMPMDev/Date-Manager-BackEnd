package com.datemanager.infraestructura.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Tabla de rutas: método + patrón con segmentos literales y {@code {parámetros}}. Entre dos rutas que encajan con
 * la misma ruta, gana la que tiene literal en el primer segmento donde difieren ({@code /citas/hoy} antes que
 * {@code /citas/{id}}). Se registran todas antes de arrancar el servidor; después solo se lee.
 */
public final class Enrutador {

    /** Orden con el que se listan los métodos en {@code Allow}. */
    private static final List<String> METODOS = List.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private record Entrada(Ruta ruta, List<String> segmentos) {
    }

    /** La ruta que atiende la petición y los valores de sus {@code {parámetros}}, ya decodificados. */
    record Coincidencia(Ruta ruta, Map<String, String> parametros) {
    }

    private final List<Entrada> entradas = new ArrayList<>();

    /** @throws IllegalArgumentException si ya hay una ruta con el mismo método y la misma forma */
    public Enrutador agregar(Ruta ruta) {
        List<String> segmentos = Ruta.segmentos(ruta.patron());
        for (Entrada existente : entradas) {
            if (existente.ruta().metodo().equals(ruta.metodo()) && forma(existente.segmentos()).equals(forma(segmentos))) {
                throw new IllegalArgumentException("Ruta repetida: " + ruta.metodo() + " " + ruta.patron()
                        + " choca con " + existente.ruta().patron());
            }
        }
        entradas.add(new Entrada(ruta, segmentos));
        return this;
    }

    /** Las rutas registradas, en orden de registro (para generar la documentación). */
    public List<Ruta> rutas() {
        return entradas.stream().map(Entrada::ruta).toList();
    }

    /**
     * Busca la ruta de una petición. {@code HEAD} se atiende con la ruta {@code GET}.
     *
     * @param rutaCruda ruta tal como llegó, sin consulta: se parte por {@code /} antes de decodificar, así que un
     *     {@code %2F} queda dentro de su segmento
     * @throws ErrorHttp 404 si ningún patrón encaja con la ruta; 405 con {@code Allow} si encaja con otros métodos;
     *     400 si la ruta trae una codificación inválida
     */
    Coincidencia buscar(String metodo, String rutaCruda) {
        List<String> segmentos = segmentosDe(rutaCruda);
        String efectivo = metodo.equals("HEAD") ? "GET" : metodo;
        Entrada mejor = null;
        Map<String, String> mejoresParametros = null;
        Set<String> metodosDeLaRuta = new HashSet<>();
        for (Entrada entrada : entradas) {
            Map<String, String> parametros = encaja(entrada.segmentos(), segmentos);
            if (parametros == null) {
                continue;
            }
            metodosDeLaRuta.add(entrada.ruta().metodo());
            if (entrada.ruta().metodo().equals(efectivo) && (mejor == null
                    || masEspecifica(entrada.segmentos(), mejor.segmentos()))) {
                mejor = entrada;
                mejoresParametros = parametros;
            }
        }
        if (mejor != null) {
            return new Coincidencia(mejor.ruta(), mejoresParametros);
        }
        if (metodosDeLaRuta.isEmpty()) {
            throw ErrorHttp.noEncontrado();
        }
        throw ErrorHttp.metodoNoPermitido(allow(metodosDeLaRuta));
    }

    /**
     * Valor de {@code Allow} para una ruta existente: sus métodos, más {@code HEAD} si tiene {@code GET} y
     * {@code OPTIONS}.
     *
     * @throws ErrorHttp 404 si ningún patrón encaja con la ruta
     */
    String allowDe(String rutaCruda) {
        List<String> segmentos = segmentosDe(rutaCruda);
        Set<String> metodos = new HashSet<>();
        for (Entrada entrada : entradas) {
            if (encaja(entrada.segmentos(), segmentos) != null) {
                metodos.add(entrada.ruta().metodo());
            }
        }
        if (metodos.isEmpty()) {
            throw ErrorHttp.noEncontrado();
        }
        return allow(metodos);
    }

    private static String allow(Set<String> declarados) {
        StringJoiner allow = new StringJoiner(", ");
        for (String metodo : METODOS) {
            if (declarados.contains(metodo)) {
                allow.add(metodo);
                if (metodo.equals("GET")) {
                    allow.add("HEAD");
                }
            }
        }
        allow.add("OPTIONS");
        return allow.toString();
    }

    // ------------------------------------------------------------------ segmentos

    /** Parte la ruta cruda y decodifica cada segmento ({@code %C3%B1} es ñ; el {@code +} no es un espacio). */
    private static List<String> segmentosDe(String rutaCruda) {
        if (rutaCruda == null || !rutaCruda.startsWith("/")) {
            throw ErrorHttp.noEncontrado();
        }
        List<String> segmentos = new ArrayList<>();
        for (String crudo : rutaCruda.substring(1).split("/", -1)) {
            segmentos.add(Solicitud.decodificar(crudo, false));
        }
        return segmentos;
    }

    /** Los valores de los parámetros si el patrón encaja con la ruta; {@code null} si no. */
    private static Map<String, String> encaja(List<String> patron, List<String> ruta) {
        if (patron.size() != ruta.size()) {
            return null;
        }
        Map<String, String> parametros = new HashMap<>();
        for (int i = 0; i < patron.size(); i++) {
            String segmento = patron.get(i);
            if (Ruta.esParametro(segmento)) {
                if (ruta.get(i).isEmpty()) {
                    return null;
                }
                parametros.put(segmento.substring(1, segmento.length() - 1), ruta.get(i));
            } else if (!segmento.equals(ruta.get(i))) {
                return null;
            }
        }
        return parametros;
    }

    /** {@code true} si {@code a} tiene literal donde {@code b} tiene parámetro, en el primer segmento que difiere. */
    private static boolean masEspecifica(List<String> a, List<String> b) {
        for (int i = 0; i < a.size(); i++) {
            boolean literalA = !Ruta.esParametro(a.get(i));
            boolean literalB = !Ruta.esParametro(b.get(i));
            if (literalA != literalB) {
                return literalA;
            }
        }
        return false;
    }

    /** El patrón sin los nombres de parámetro: dos patrones con la misma forma se pisan. */
    private static List<String> forma(List<String> segmentos) {
        return segmentos.stream().map(s -> Ruta.esParametro(s) ? "{}" : s).toList();
    }
}

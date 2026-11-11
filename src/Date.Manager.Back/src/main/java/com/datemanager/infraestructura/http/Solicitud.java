package com.datemanager.infraestructura.http;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import com.datemanager.infraestructura.seguridad.UsuarioActual;

/**
 * Lo que un {@link Manejador} ve de la petición: parámetros de la ruta y de la consulta ya decodificados, el cuerpo
 * JSON y, si la ruta exige sesión, el usuario. Los valores inválidos se rechazan con {@link ErrorNegocio}
 * {@code VALIDACION}, que el manejador de errores convierte en un 400.
 */
public final class Solicitud {

    private static final char MARCA_DE_ORDEN = (char) 0xFEFF;

    private final String metodo;
    private final String ruta;
    private final Map<String, String> parametros;
    private final Map<String, List<String>> consulta;
    private final String tipoContenido;
    private final byte[] cuerpo;
    private final UsuarioActual usuario;

    /**
     * @param ruta ruta tal como llegó (sin decodificar ni consulta)
     * @param parametros valores de los {@code {...}} de la ruta, ya decodificados
     * @param consulta parámetros de la consulta, ya decodificados; si un nombre se repite, el primero gana
     * @param tipoContenido cabecera {@code Content-Type}, o {@code null}
     * @param cuerpo bytes del cuerpo (vacío si no hay)
     * @param usuario quien hace la petición, o {@code null} en una ruta pública
     */
    public Solicitud(String metodo, String ruta, Map<String, String> parametros,
            Map<String, List<String>> consulta, String tipoContenido, byte[] cuerpo, UsuarioActual usuario) {
        this.metodo = metodo;
        this.ruta = ruta;
        this.parametros = Map.copyOf(parametros);
        this.consulta = Map.copyOf(consulta);
        this.tipoContenido = tipoContenido;
        this.cuerpo = cuerpo == null ? new byte[0] : cuerpo;
        this.usuario = usuario;
    }

    public String metodo() {
        return metodo;
    }

    /** Ruta tal como llegó, sin la consulta ni decodificar (los {@code %2F} siguen escritos así). */
    public String ruta() {
        return ruta;
    }

    /** Valor ya decodificado del {@code {nombre}} de la ruta. */
    public String parametro(String nombre) {
        String valor = parametros.get(nombre);
        if (valor == null) {
            throw new IllegalArgumentException("La ruta no tiene el parámetro {" + nombre + "}");
        }
        return valor;
    }

    /** Valor del parámetro de consulta (el primero si se repite); vacío si no viene. Puede ser el texto vacío. */
    public Optional<String> consulta(String nombre) {
        List<String> valores = consulta.get(nombre);
        return valores == null ? Optional.empty() : Optional.of(valores.get(0));
    }

    public UUID parametroUuid(String nombre) {
        return uuid(parametro(nombre));
    }

    /** El UUID de la consulta, o {@code null} si no viene o viene vacío. */
    public UUID consultaUuid(String nombre) {
        String valor = consulta(nombre).map(String::strip).orElse("");
        return valor.isEmpty() ? null : uuid(valor);
    }

    /** El valor (sensible a mayúsculas) de la consulta como constante del enum, o {@code null} si no viene o es vacío. */
    public <E extends Enum<E>> E consultaEnum(String nombre, Class<E> tipo) {
        String valor = consulta(nombre).map(String::strip).orElse("");
        if (valor.isEmpty()) {
            return null;
        }
        try {
            return Enum.valueOf(tipo, valor);
        } catch (IllegalArgumentException e) {
            throw peticionInvalida();
        }
    }

    /** Si la petición trae cuerpo (para los cuerpos opcionales, el {@code required = false} de Spring). */
    public boolean tieneCuerpo() {
        return cuerpo.length > 0;
    }

    /**
     * El cuerpo como objeto JSON. Se acepta sin {@code Content-Type} o con uno JSON (415 si es otro).
     *
     * @throws ErrorNegocio {@code VALIDACION} si falta, no es UTF-8, no es JSON o su raíz no es un objeto
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> cuerpoObjeto() {
        if (cuerpo.length == 0) {
            throw datosInvalidos();
        }
        if (!esTipoJson(tipoContenido)) {
            throw ErrorHttp.tipoNoSoportado();
        }
        Object raiz;
        try {
            raiz = Json.parse(texto(cuerpo));
        } catch (JsonInvalido | CharacterCodingException e) {
            throw datosInvalidos();
        }
        if (!(raiz instanceof Map)) {
            throw datosInvalidos();
        }
        return (Map<String, Object>) raiz;
    }

    /**
     * Quién hace la petición.
     *
     * @throws IllegalStateException en una ruta pública, que no tiene usuario (error de programación)
     */
    public UsuarioActual usuario() {
        if (usuario == null) {
            throw new IllegalStateException("La ruta es pública: no hay usuario.");
        }
        return usuario;
    }

    // ------------------------------------------------------------------ interno

    /** Separa la consulta cruda ({@code a=1&b=2}) y decodifica nombres y valores ({@code +} es un espacio). */
    static Map<String, List<String>> leerConsulta(String consultaCruda) {
        Map<String, List<String>> resultado = new LinkedHashMap<>();
        if (consultaCruda == null || consultaCruda.isEmpty()) {
            return resultado;
        }
        for (String par : consultaCruda.split("&")) {
            if (par.isEmpty()) {
                continue;
            }
            int igual = par.indexOf('=');
            String nombre = decodificar(igual < 0 ? par : par.substring(0, igual), true);
            String valor = igual < 0 ? "" : decodificar(par.substring(igual + 1), true);
            resultado.computeIfAbsent(nombre, k -> new ArrayList<>()).add(valor);
        }
        return resultado;
    }

    /**
     * Decodifica los {@code %XX} como UTF-8 estricto; en la consulta, {@code masEsEspacio} convierte además el
     * {@code +} en un espacio (en la ruta es un {@code +}).
     *
     * @throws ErrorHttp 400 si un {@code %} no va seguido de dos dígitos o los bytes no son UTF-8 válido
     */
    static String decodificar(String crudo, boolean masEsEspacio) {
        if (crudo.indexOf('%') < 0 && !(masEsEspacio && crudo.indexOf('+') >= 0)) {
            return crudo;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(crudo.length());
        for (int i = 0; i < crudo.length(); i++) {
            char c = crudo.charAt(i);
            if (c == '%') {
                int alto = i + 2 < crudo.length() ? hexa(crudo.charAt(i + 1)) : -1;
                int bajo = i + 2 < crudo.length() ? hexa(crudo.charAt(i + 2)) : -1;
                if (alto < 0 || bajo < 0) {
                    throw ErrorHttp.peticionInvalida();
                }
                bytes.write(alto * 16 + bajo);
                i += 2;
            } else {
                byte[] propios = (masEsEspacio && c == '+' ? " " : String.valueOf(c)).getBytes(StandardCharsets.UTF_8);
                bytes.write(propios, 0, propios.length);
            }
        }
        try {
            return estricto(bytes.toByteArray());
        } catch (CharacterCodingException e) {
            throw ErrorHttp.peticionInvalida();
        }
    }

    private static int hexa(char c) {
        return c < 128 ? Character.digit(c, 16) : -1;
    }

    /** UTF-8 estricto: los bytes inválidos no se sustituyen por un carácter de reemplazo, fallan. */
    private static String estricto(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static boolean esTipoJson(String tipoContenido) {
        if (tipoContenido == null || tipoContenido.isBlank()) {
            return true;
        }
        int punto = tipoContenido.indexOf(';');
        String tipo = (punto < 0 ? tipoContenido : tipoContenido.substring(0, punto)).strip()
                .toLowerCase(Locale.ROOT);
        return tipo.equals("application/json") || tipo.endsWith("+json");
    }

    /** El cuerpo como texto UTF-8 estricto y sin la marca de orden de bytes inicial. */
    private static String texto(byte[] bytes) throws CharacterCodingException {
        String texto = estricto(bytes);
        return !texto.isEmpty() && texto.charAt(0) == MARCA_DE_ORDEN ? texto.substring(1) : texto;
    }

    /** UUID en su forma canónica de 36 caracteres (UUID.fromString acepta también variantes como 1-1-1-1-1). */
    private static UUID uuid(String texto) {
        String valor = texto.strip();
        if (valor.length() == 36) {
            boolean forma = true;
            for (int i = 0; i < 36 && forma; i++) {
                char c = valor.charAt(i);
                if (i == 8 || i == 13 || i == 18 || i == 23) {
                    forma = c == '-';
                } else {
                    forma = c < 128 && Character.digit(c, 16) >= 0;
                }
            }
            if (forma) {
                return UUID.fromString(valor);
            }
        }
        throw peticionInvalida();
    }

    private static ErrorNegocio peticionInvalida() {
        return new ErrorNegocio(CodigoError.VALIDACION, ManejadorErrores.MENSAJE_PETICION_INVALIDA);
    }

    private static ErrorNegocio datosInvalidos() {
        return new ErrorNegocio(CodigoError.VALIDACION, ManejadorErrores.MENSAJE_DATOS_INVALIDOS);
    }
}

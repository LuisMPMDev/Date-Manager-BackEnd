package com.datemanager.infraestructura.http;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * JSON mínimo y sin dependencias para la capa HTTP: {@link #parse} lee texto a {@code Map}/{@code List}/valores,
 * {@link #escribir} serializa valores y {@code record}, y {@link #leer} construye un {@code record} (las
 * {@code *Peticion}) desde un objeto ya leído.
 *
 * <p>Imita lo que antes hacía Jackson con la configuración de Spring Boot: al escribir se omiten los
 * {@code null} (de records y de mapas) y al leer se ignoran los campos desconocidos.
 */
public final class Json {

    /** Niveles máximos de anidamiento de objetos y arreglos al leer. */
    static final int PROFUNDIDAD_MAXIMA = 64;

    private Json() {
    }

    // ------------------------------------------------------------------ lectura

    /**
     * Lee un valor JSON completo. Devuelve {@code Map<String,Object>} (en orden de inserción; si una clave se
     * repite gana la última), {@code List<Object>}, {@code String}, {@code Long} (entero sin punto ni exponente
     * que cabe en un {@code long}), {@code Double} (el resto de números), {@code Boolean} o {@code null}.
     *
     * @throws JsonInvalido si la sintaxis es inválida, sobra texto tras el valor o el anidamiento pasa de
     *     {@value #PROFUNDIDAD_MAXIMA} niveles
     */
    public static Object parse(String texto) {
        if (texto == null) {
            throw new JsonInvalido("JSON inválido: no hay texto.");
        }
        Lector lector = new Lector(texto);
        Object valor = lector.valor(0);
        lector.saltarBlancos();
        if (!lector.fin()) {
            throw lector.error("hay texto después del valor");
        }
        return valor;
    }

    private static final class Lector {
        private final String s;
        private int i;

        Lector(String s) {
            this.s = s;
        }

        boolean fin() {
            return i >= s.length();
        }

        JsonInvalido error(String detalle) {
            return new JsonInvalido("JSON inválido: " + detalle + " (posición " + i + ").");
        }

        void saltarBlancos() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    i++;
                } else {
                    return;
                }
            }
        }

        Object valor(int profundidad) {
            saltarBlancos();
            if (fin()) {
                throw error("se esperaba un valor");
            }
            char c = s.charAt(i);
            switch (c) {
                case '{':
                    return objeto(profundidad + 1);
                case '[':
                    return arreglo(profundidad + 1);
                case '"':
                    return cadena();
                case 't':
                    literal("true");
                    return Boolean.TRUE;
                case 'f':
                    literal("false");
                    return Boolean.FALSE;
                case 'n':
                    literal("null");
                    return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return numero();
                    }
                    throw error("carácter inesperado");
            }
        }

        private void literal(String esperado) {
            if (!s.startsWith(esperado, i)) {
                throw error("valor desconocido");
            }
            i += esperado.length();
        }

        private Map<String, Object> objeto(int profundidad) {
            if (profundidad > PROFUNDIDAD_MAXIMA) {
                throw error("anidamiento demasiado profundo");
            }
            i++; // {
            Map<String, Object> resultado = new LinkedHashMap<>();
            saltarBlancos();
            if (!fin() && s.charAt(i) == '}') {
                i++;
                return resultado;
            }
            while (true) {
                saltarBlancos();
                if (fin() || s.charAt(i) != '"') {
                    throw error("se esperaba el nombre de un campo");
                }
                String clave = cadena();
                saltarBlancos();
                if (fin() || s.charAt(i) != ':') {
                    throw error("se esperaba «:»");
                }
                i++;
                resultado.put(clave, valor(profundidad));
                saltarBlancos();
                if (fin()) {
                    throw error("objeto sin cerrar");
                }
                char c = s.charAt(i++);
                if (c == '}') {
                    return resultado;
                }
                if (c != ',') {
                    i--;
                    throw error("se esperaba «,» o «}»");
                }
            }
        }

        private List<Object> arreglo(int profundidad) {
            if (profundidad > PROFUNDIDAD_MAXIMA) {
                throw error("anidamiento demasiado profundo");
            }
            i++; // [
            List<Object> resultado = new ArrayList<>();
            saltarBlancos();
            if (!fin() && s.charAt(i) == ']') {
                i++;
                return resultado;
            }
            while (true) {
                resultado.add(valor(profundidad));
                saltarBlancos();
                if (fin()) {
                    throw error("arreglo sin cerrar");
                }
                char c = s.charAt(i++);
                if (c == ']') {
                    return resultado;
                }
                if (c != ',') {
                    i--;
                    throw error("se esperaba «,» o «]»");
                }
            }
        }

        private String cadena() {
            i++; // comilla de apertura
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (fin()) {
                    throw error("texto sin cerrar");
                }
                char c = s.charAt(i);
                if (c == '"') {
                    i++;
                    return sb.toString();
                }
                if (c < 0x20) {
                    throw error("carácter de control sin escapar en un texto");
                }
                if (c == '\\') {
                    i++;
                    sb.append(escape());
                    continue;
                }
                if (Character.isHighSurrogate(c)) {
                    if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) {
                        throw error("par sustituto incompleto");
                    }
                    sb.append(c).append(s.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (Character.isLowSurrogate(c)) {
                    throw error("par sustituto incompleto");
                }
                sb.append(c);
                i++;
            }
        }

        /** Lee lo que sigue a una barra invertida; devuelve el texto que representa. */
        private String escape() {
            if (fin()) {
                throw error("texto sin cerrar");
            }
            char c = s.charAt(i++);
            switch (c) {
                case '"':
                    return "\"";
                case '\\':
                    return "\\";
                case '/':
                    return "/";
                case 'b':
                    return "\b";
                case 'f':
                    return "\f";
                case 'n':
                    return "\n";
                case 'r':
                    return "\r";
                case 't':
                    return "\t";
                case 'u':
                    return unicode();
                default:
                    i--;
                    throw error("escape desconocido");
            }
        }

        private String unicode() {
            char primero = (char) hexa4();
            if (Character.isLowSurrogate(primero)) {
                throw error("par sustituto incompleto");
            }
            if (!Character.isHighSurrogate(primero)) {
                return String.valueOf(primero);
            }
            if (i + 1 >= s.length() || s.charAt(i) != '\\' || s.charAt(i + 1) != 'u') {
                throw error("par sustituto incompleto");
            }
            i += 2;
            char segundo = (char) hexa4();
            if (!Character.isLowSurrogate(segundo)) {
                throw error("par sustituto incompleto");
            }
            return new String(new char[] {primero, segundo});
        }

        private int hexa4() {
            if (i + 4 > s.length()) {
                throw error("escape \\u incompleto");
            }
            int valor = 0;
            for (int k = 0; k < 4; k++) {
                char c = s.charAt(i + k);
                int d;
                if (c >= '0' && c <= '9') {
                    d = c - '0';
                } else if (c >= 'a' && c <= 'f') {
                    d = c - 'a' + 10;
                } else if (c >= 'A' && c <= 'F') {
                    d = c - 'A' + 10;
                } else {
                    i += k;
                    throw error("escape \\u inválido");
                }
                valor = valor * 16 + d;
            }
            i += 4;
            return valor;
        }

        private Object numero() {
            int inicio = i;
            if (s.charAt(i) == '-') {
                i++;
            }
            if (fin() || !digito(s.charAt(i))) {
                throw error("número inválido");
            }
            if (s.charAt(i) == '0') {
                i++;
                if (!fin() && digito(s.charAt(i))) {
                    throw error("número con ceros a la izquierda");
                }
            } else {
                digitos();
            }
            boolean entero = true;
            if (!fin() && s.charAt(i) == '.') {
                entero = false;
                i++;
                if (fin() || !digito(s.charAt(i))) {
                    throw error("número inválido");
                }
                digitos();
            }
            if (!fin() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                entero = false;
                i++;
                if (!fin() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                    i++;
                }
                if (fin() || !digito(s.charAt(i))) {
                    throw error("número inválido");
                }
                digitos();
            }
            String texto = s.substring(inicio, i);
            if (entero) {
                try {
                    return Long.parseLong(texto);
                } catch (NumberFormatException e) {
                    // Cabe solo como Double (con pérdida de precisión).
                }
            }
            double valor = Double.parseDouble(texto);
            if (Double.isInfinite(valor)) {
                i = inicio;
                throw error("número fuera de rango");
            }
            return valor;
        }

        private void digitos() {
            while (!fin() && digito(s.charAt(i))) {
                i++;
            }
        }

        private static boolean digito(char c) {
            return c >= '0' && c <= '9';
        }
    }

    // ------------------------------------------------------------------ escritura

    /**
     * Serializa un valor a JSON. Acepta {@code null}, {@code String}, {@code Number}, {@code Boolean},
     * {@code UUID}, enums (su nombre), {@code Instant}/{@code LocalDate}/{@code LocalTime} (su
     * {@code toString()}), {@code Map}, {@code Iterable}, arreglos y {@code record} (componentes en orden de
     * declaración, omitiendo los {@code null}; los mapas también omiten los valores {@code null}). Los números
     * decimales salen sin notación científica para magnitudes normales y {@code BigDecimal} con
     * {@code toPlainString()}. Escapa {@code "}, {@code \} y los controles menores que 0x20; el resto del
     * Unicode va tal cual.
     *
     * @throws IllegalArgumentException con {@code Optional}, números no finitos o cualquier tipo no soportado
     */
    public static String escribir(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribirValor(sb, valor);
        return sb.toString();
    }

    private static void escribirValor(StringBuilder sb, Object valor) {
        if (valor == null) {
            sb.append("null");
        } else if (valor instanceof String texto) {
            escribirTexto(sb, texto);
        } else if (valor instanceof Boolean b) {
            sb.append(b.booleanValue());
        } else if (valor instanceof Number n) {
            sb.append(numero(n));
        } else if (valor instanceof UUID || valor instanceof Instant || valor instanceof LocalDate
                || valor instanceof LocalTime) {
            escribirTexto(sb, valor.toString());
        } else if (valor instanceof Enum<?> e) {
            escribirTexto(sb, e.name());
        } else if (valor instanceof Map<?, ?> mapa) {
            escribirMapa(sb, mapa);
        } else if (valor instanceof Iterable<?> iterable) {
            sb.append('[');
            boolean primero = true;
            for (Object elemento : iterable) {
                if (!primero) {
                    sb.append(',');
                }
                primero = false;
                escribirValor(sb, elemento);
            }
            sb.append(']');
        } else if (valor.getClass().isArray()) {
            sb.append('[');
            int n = Array.getLength(valor);
            for (int k = 0; k < n; k++) {
                if (k > 0) {
                    sb.append(',');
                }
                escribirValor(sb, Array.get(valor, k));
            }
            sb.append(']');
        } else if (valor instanceof Record registro) {
            escribirRecord(sb, registro);
        } else {
            throw new IllegalArgumentException("No se puede escribir como JSON un " + valor.getClass().getName());
        }
    }

    private static void escribirMapa(StringBuilder sb, Map<?, ?> mapa) {
        sb.append('{');
        boolean primero = true;
        for (Map.Entry<?, ?> entrada : mapa.entrySet()) {
            Object clave = entrada.getKey();
            if (clave == null) {
                throw new IllegalArgumentException("Un mapa JSON no puede tener una clave nula.");
            }
            if (entrada.getValue() == null) {
                continue;
            }
            if (!primero) {
                sb.append(',');
            }
            primero = false;
            escribirTexto(sb, clave instanceof Enum<?> e ? e.name() : clave.toString());
            sb.append(':');
            escribirValor(sb, entrada.getValue());
        }
        sb.append('}');
    }

    private static void escribirRecord(StringBuilder sb, Record registro) {
        sb.append('{');
        boolean primero = true;
        for (RecordComponent componente : registro.getClass().getRecordComponents()) {
            Object valor = leerComponente(registro, componente);
            if (valor == null) {
                continue;
            }
            if (!primero) {
                sb.append(',');
            }
            primero = false;
            escribirTexto(sb, componente.getName());
            sb.append(':');
            escribirValor(sb, valor);
        }
        sb.append('}');
    }

    private static Object leerComponente(Record registro, RecordComponent componente) {
        try {
            Method acceso = componente.getAccessor();
            acceso.setAccessible(true);
            return acceso.invoke(registro);
        } catch (InvocationTargetException e) {
            throw relanzar(e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("No se puede leer el componente " + componente.getName(), e);
        }
    }

    private static String numero(Number n) {
        if (n instanceof BigDecimal b) {
            return b.toPlainString();
        }
        if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("JSON no admite NaN ni infinito.");
            }
            String texto = n instanceof Float ? Float.toString(n.floatValue()) : Double.toString(d);
            double magnitud = Math.abs(d);
            if (texto.indexOf('E') < 0 || magnitud < 1e-9 || magnitud >= 1e15) {
                return texto;
            }
            return new BigDecimal(texto).stripTrailingZeros().toPlainString();
        }
        if (n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte
                || n instanceof java.math.BigInteger) {
            return n.toString();
        }
        throw new IllegalArgumentException("No se puede escribir como JSON un " + n.getClass().getName());
    }

    private static final char[] HEXA = "0123456789abcdef".toCharArray();

    private static void escribirTexto(StringBuilder sb, String texto) {
        sb.append('"');
        int n = texto.length();
        for (int k = 0; k < n; k++) {
            char c = texto.charAt(k);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        unicode(sb, c);
                    } else if (Character.isHighSurrogate(c) && k + 1 < n && Character.isLowSurrogate(texto.charAt(k + 1))) {
                        sb.append(c).append(texto.charAt(++k));
                    } else if (Character.isSurrogate(c)) {
                        unicode(sb, c); // suelto: no es texto válido en UTF-8, se deja escapado
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    private static void unicode(StringBuilder sb, char c) {
        sb.append("\\u").append(HEXA[c >> 12 & 0xF]).append(HEXA[c >> 8 & 0xF]).append(HEXA[c >> 4 & 0xF])
                .append(HEXA[c & 0xF]);
    }

    // ------------------------------------------------------------------ records

    /**
     * Construye un {@code record} desde un objeto ya leído con {@link #parse}. Los campos ausentes o {@code null}
     * quedan en {@code null} (en cero/falso si el componente es primitivo) y los desconocidos se ignoran. Tipos
     * soportados: {@code String}, {@code UUID} (texto con forma de UUID), {@code Integer}, {@code Long},
     * {@code Double}, {@code Boolean}, {@code BigDecimal}, enums (por nombre), {@code record} anidados y
     * {@code List}/{@code Set}/{@code Collection} de cualquiera de ellos. No se convierte entre tipos (un número
     * no sirve como texto ni un texto como número); un entero acepta 1e3 pero no 1.5.
     *
     * @throws JsonInvalido si un campo trae un tipo equivocado o un valor inválido
     * @throws IllegalArgumentException si el record declara un tipo no soportado
     */
    public static <T extends Record> T leer(Map<String, Object> objeto, Class<T> tipo) {
        if (objeto == null) {
            throw new JsonInvalido("Se esperaba un objeto JSON.");
        }
        return construir(objeto, tipo, "");
    }

    private static <T> T construir(Map<?, ?> objeto, Class<T> tipo, String ruta) {
        RecordComponent[] componentes = tipo.getRecordComponents();
        Class<?>[] tipos = new Class<?>[componentes.length];
        Object[] argumentos = new Object[componentes.length];
        for (int k = 0; k < componentes.length; k++) {
            RecordComponent c = componentes[k];
            tipos[k] = c.getType();
            argumentos[k] = convertir(c.getGenericType(), objeto.get(c.getName()), ruta + c.getName());
        }
        try {
            Constructor<T> constructor = tipo.getDeclaredConstructor(tipos);
            constructor.setAccessible(true);
            return constructor.newInstance(argumentos);
        } catch (InvocationTargetException e) {
            throw relanzar(e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("No se puede construir " + tipo.getName(), e);
        }
    }

    private static RuntimeException relanzar(InvocationTargetException e) {
        Throwable causa = e.getCause();
        if (causa instanceof RuntimeException r) {
            return r;
        }
        if (causa instanceof Error err) {
            throw err;
        }
        return new IllegalStateException(causa);
    }

    private static JsonInvalido tipoEquivocado(String ruta) {
        return new JsonInvalido("El campo «" + ruta + "» no tiene el tipo esperado.");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object convertir(Type tipo, Object valor, String ruta) {
        if (tipo instanceof ParameterizedType parametrizado) {
            Class<?> crudo = (Class<?>) parametrizado.getRawType();
            if (valor == null) {
                return null;
            }
            boolean conjunto = crudo == Set.class;
            if (!conjunto && crudo != List.class && crudo != Collection.class) {
                throw new IllegalArgumentException("Tipo no soportado en " + ruta + ": " + tipo.getTypeName());
            }
            if (!(valor instanceof List<?> lista)) {
                throw tipoEquivocado(ruta);
            }
            Type elemento = parametrizado.getActualTypeArguments()[0];
            Collection<Object> resultado = conjunto ? new LinkedHashSet<>() : new ArrayList<>();
            int k = 0;
            for (Object item : lista) {
                resultado.add(convertir(elemento, item, ruta + "[" + k++ + "]"));
            }
            return resultado;
        }
        if (!(tipo instanceof Class<?> clase)) {
            throw new IllegalArgumentException("Tipo no soportado en " + ruta + ": " + tipo.getTypeName());
        }
        if (clase.isPrimitive()) {
            if (valor == null) {
                return valorPorDefecto(clase);
            }
            clase = envoltorio(clase);
        }
        if (valor == null) {
            return null;
        }
        if (clase == String.class) {
            if (valor instanceof String) {
                return valor;
            }
            throw tipoEquivocado(ruta);
        }
        if (clase == Boolean.class) {
            if (valor instanceof Boolean) {
                return valor;
            }
            throw tipoEquivocado(ruta);
        }
        if (clase == Integer.class) {
            long entero = enteroLargo(valor, ruta);
            if (entero < Integer.MIN_VALUE || entero > Integer.MAX_VALUE) {
                throw tipoEquivocado(ruta);
            }
            return (int) entero;
        }
        if (clase == Long.class) {
            return enteroLargo(valor, ruta);
        }
        if (clase == Double.class) {
            if (valor instanceof Number n) {
                return n.doubleValue();
            }
            throw tipoEquivocado(ruta);
        }
        if (clase == BigDecimal.class) {
            if (valor instanceof Long l) {
                return BigDecimal.valueOf(l);
            }
            if (valor instanceof Double d) {
                return BigDecimal.valueOf(d);
            }
            throw tipoEquivocado(ruta);
        }
        if (clase == UUID.class) {
            if (valor instanceof String texto && tieneFormaDeUuid(texto)) {
                return UUID.fromString(texto);
            }
            throw tipoEquivocado(ruta);
        }
        if (clase.isEnum()) {
            if (valor instanceof String texto) {
                try {
                    return Enum.valueOf((Class<Enum>) clase, texto);
                } catch (IllegalArgumentException e) {
                    throw tipoEquivocado(ruta);
                }
            }
            throw tipoEquivocado(ruta);
        }
        if (clase.isRecord()) {
            if (valor instanceof Map<?, ?> mapa) {
                return construir(mapa, clase, ruta + ".");
            }
            throw tipoEquivocado(ruta);
        }
        throw new IllegalArgumentException("Tipo no soportado en " + ruta + ": " + clase.getName());
    }

    /** Un {@code Long} tal cual, o un {@code Double} sin parte decimal que cabe en un {@code long}. */
    private static long enteroLargo(Object valor, String ruta) {
        if (valor instanceof Long l) {
            return l;
        }
        if (valor instanceof Double d && d == Math.rint(d) && Math.abs(d) < 9.0e18) {
            return d.longValue();
        }
        throw tipoEquivocado(ruta);
    }

    /** 8-4-4-4-12 dígitos hexadecimales; {@code UUID.fromString} solo es más permisivo (acepta «1-1-1-1-1»). */
    private static boolean tieneFormaDeUuid(String texto) {
        if (texto.length() != 36) {
            return false;
        }
        for (int k = 0; k < 36; k++) {
            char c = texto.charAt(k);
            if (k == 8 || k == 13 || k == 18 || k == 23) {
                if (c != '-') {
                    return false;
                }
            } else if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> envoltorio(Class<?> primitivo) {
        if (primitivo == int.class) {
            return Integer.class;
        }
        if (primitivo == long.class) {
            return Long.class;
        }
        if (primitivo == double.class) {
            return Double.class;
        }
        if (primitivo == boolean.class) {
            return Boolean.class;
        }
        throw new IllegalArgumentException("Tipo no soportado: " + primitivo.getName());
    }

    private static Object valorPorDefecto(Class<?> primitivo) {
        if (primitivo == int.class) {
            return 0;
        }
        if (primitivo == long.class) {
            return 0L;
        }
        if (primitivo == double.class) {
            return 0.0;
        }
        if (primitivo == boolean.class) {
            return false;
        }
        throw new IllegalArgumentException("Tipo no soportado: " + primitivo.getName());
    }
}

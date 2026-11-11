package com.datemanager.infraestructura.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class JsonTest {

    enum Color { ROJO, VERDE }

    record Punto(String nombre, Integer x) {
    }

    record Cliente(String nombre, String correo) {
    }

    record Ventana(UUID id, Integer dia, String inicio, String fin) {
    }

    record Servicio(String nombre, Integer duracionMin, Boolean activo, BigDecimal valor, List<Ventana> ventanas) {
    }

    record Reserva(UUID servicioId, String fecha, Cliente cliente) {
    }

    record Rol(String nombre, Set<String> permisos, List<String> otros) {
    }

    record Mixto(Long grande, Double real, Color color, Boolean ok) {
    }

    record Primitivos(int n, boolean b, double d, long l) {
    }

    record Estricto(String nombre) {
        Estricto {
            if (nombre != null && nombre.isEmpty()) {
                throw new IllegalStateException("vacío");
            }
        }
    }

    record Salida(UUID id, String nombre, Integer nulo, int usos, double ocupacion, Number actual, Color color,
            List<Punto> puntos) {
    }

    // ---------------------------------------------------------------- parse: estructura

    @Test
    void parseDaLaEstructuraEsperada() {
        Object valor = Json.parse("{\"a\":[1,2.5,true,null,\"x\"]}");

        Map<String, Object> esperado = new LinkedHashMap<>();
        List<Object> lista = new ArrayList<>();
        lista.add(1L);
        lista.add(2.5);
        lista.add(true);
        lista.add(null);
        lista.add("x");
        esperado.put("a", lista);
        assertEquals(esperado, valor);
    }

    @Test
    void parseConservaElOrdenDeInsercionYLaUltimaClaveRepetidaGana() {
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) Json.parse("{\"z\":1,\"a\":2,\"m\":3,\"z\":9}");
        assertEquals(List.of("z", "a", "m"), new ArrayList<>(m.keySet()));
        assertEquals(9L, m.get("z"));
    }

    @Test
    void parseAceptaEscalaresEnLaRaizYEspaciosAlrededor() {
        assertEquals("hola", Json.parse("  \"hola\" \n\t\r"));
        assertEquals(true, Json.parse("true"));
        assertEquals(false, Json.parse(" false "));
        assertNull(Json.parse("null"));
        assertEquals(7L, Json.parse("7"));
        assertEquals(Map.of(), Json.parse("{ }"));
        assertEquals(List.of(), Json.parse("[ ]"));
    }

    @Test
    void parseAceptaHastaSesentaYCuatroNivelesYRechazaElSesentaYCinco() {
        assertInstanceOf(List.class, Json.parse("[".repeat(64) + "]".repeat(64)));
        assertThrows(JsonInvalido.class, () -> Json.parse("[".repeat(65) + "]".repeat(65)));
        assertThrows(JsonInvalido.class, () -> Json.parse("[".repeat(65)));
        assertThrows(JsonInvalido.class, () -> Json.parse("{\"a\":".repeat(65) + "1" + "}".repeat(65)));
        assertInstanceOf(Map.class, Json.parse("{\"a\":".repeat(64) + "1" + "}".repeat(64)));
    }

    @Test
    void anidamientoMuyProfundoNoDesbordaLaPila() {
        assertThrows(JsonInvalido.class, () -> Json.parse("[".repeat(200_000)));
    }

    // ---------------------------------------------------------------- parse: cadenas

    @Test
    void parseDecodificaEscapesUnicodeYParesSustitutos() {
        assertEquals("ñ 😀", Json.parse("\"\\u00f1 \\ud83d\\ude00\""));
        assertEquals("ñ 😀", Json.parse("\"\\u00F1 \\uD83D\\uDE00\""));
    }

    @Test
    void parseDecodificaLosEscapesSimples() {
        assertEquals("\" \\ / \b \f \n \r \t", Json.parse("\"\\\" \\\\ \\/ \\b \\f \\n \\r \\t\""));
    }

    @Test
    void parseDejaPasarElTextoNoAsciiTalCual() {
        assertEquals("Peña — 😀 ü", Json.parse("\"Peña — 😀 ü\""));
    }

    @Test
    void parseRechazaSustitutosSolitarios() {
        assertThrows(JsonInvalido.class, () -> Json.parse("\"\\ud83d\""));
        assertThrows(JsonInvalido.class, () -> Json.parse("\"\\ude00\""));
        assertThrows(JsonInvalido.class, () -> Json.parse("\"\\ud83dx\""));
        assertThrows(JsonInvalido.class, () -> Json.parse("\"\\ud83d\\u0041\""));
        assertThrows(JsonInvalido.class, () -> Json.parse("\"\\ude00\\ud83d\""));
    }

    @Test
    void parseRechazaCadenasMalFormadas() {
        for (String texto : List.of(
                "\"sin cerrar",
                "\"con\nsalto\"",
                "\"con\u0001control\"",
                "\"con\ttab\"",
                "\"\\x\"",
                "\"\\u12\"",
                "\"\\u12G4\"",
                "\"\\",
                "'comillas simples'")) {
            assertThrows(JsonInvalido.class, () -> Json.parse(texto), texto);
        }
    }

    // ---------------------------------------------------------------- parse: números

    @Test
    void parseDistingueEnterosYDecimales() {
        assertEquals(0L, Json.parse("0"));
        assertEquals(0L, Json.parse("-0"));
        assertEquals(-12L, Json.parse("-12"));
        assertEquals(Long.MAX_VALUE, Json.parse("9223372036854775807"));
        assertEquals(Long.MIN_VALUE, Json.parse("-9223372036854775808"));
        assertEquals(2.5, Json.parse("2.5"));
        assertEquals(-0.0, Json.parse("-0.0"));
        assertEquals(1000.0, Json.parse("1e3"));
        assertEquals(1000.0, Json.parse("1E3"));
        assertEquals(150.0, Json.parse("1.5E+2"));
        assertEquals(0.015, Json.parse("1.5e-2"));
        assertEquals(10.0, Json.parse("100e-1"));
    }

    @Test
    void parseUnEnteroFueraDeLongPasaADouble() {
        assertEquals(9223372036854775808.0, Json.parse("9223372036854775808"));
        assertInstanceOf(Double.class, Json.parse("-9223372036854775809"));
    }

    @Test
    void parseRechazaNumerosMalFormados() {
        for (String texto : List.of("01", "-01", "00", "-", "+1", "1.", ".5", "1e", "1e+", "1.e3", "0x10", "1_000",
                "NaN", "Infinity", "-Infinity", "1e999", "--1", "1 2")) {
            assertThrows(JsonInvalido.class, () -> Json.parse(texto), texto);
        }
    }

    // ---------------------------------------------------------------- parse: sintaxis inválida

    @Test
    void parseRechazaSintaxisInvalida() {
        for (String texto : List.of("{", "}", "[", "]", "[1,]", "[,1]", "[1 2]", "{} x", "[] []", "", "   ", "{\"a\":1,}",
                "{a:1}", "{\"a\" 1}", "{\"a\":}", "{\"a\"}", "{1:2}", "{\"a\":1 \"b\":2}", "tru", "nul", "falsey", "True",
                "NULL", "// c\n1", "\u00a01", "\ufeff{}", "{}}", "[1]]", "[\"a\"", "{\"a\":1")) {
            assertThrows(JsonInvalido.class, () -> Json.parse(texto), texto);
        }
    }

    @Test
    void parseDeNuloEsInvalido() {
        assertThrows(JsonInvalido.class, () -> Json.parse(null));
    }

    @Test
    void jsonInvalidoEsUnaRuntimeException() {
        assertTrue(RuntimeException.class.isAssignableFrom(JsonInvalido.class));
    }

    // ---------------------------------------------------------------- escribir

    @Test
    void escribirEscalares() {
        assertEquals("null", Json.escribir(null));
        assertEquals("true", Json.escribir(true));
        assertEquals("false", Json.escribir(Boolean.FALSE));
        assertEquals("\"hola\"", Json.escribir("hola"));
        assertEquals("42", Json.escribir(42));
        assertEquals("-7", Json.escribir(-7L));
        assertEquals("3", Json.escribir((short) 3));
        assertEquals("123456789012345678901234567890", Json.escribir(new BigInteger("123456789012345678901234567890")));
    }

    @Test
    void escribirEscapaComillasBarrasYControles() {
        assertEquals("\"\\\"q\\\" \\\\ \\n \\r \\t \\b \\f\"", Json.escribir("\"q\" \\ \n \r \t \b \f"));
        assertEquals("\"\\u0000\\u0001\\u001f\"", Json.escribir("\u0000\u0001\u001f"));
        assertEquals("\"/\"", Json.escribir("/"));
        assertEquals("\"\u007f\"", Json.escribir("\u007f"));
    }

    @Test
    void escribirDejaElNoAsciiTalCual() {
        assertEquals("\"Peña — 😀 ü\"", Json.escribir("Peña — 😀 ü"));
    }

    @Test
    void escribirEscapaUnSustitutoSolitarioParaNoEmitirTextoInvalido() {
        assertEquals("\"\\ud83d\"", Json.escribir("\ud83d"));
        assertEquals("\"a\\ude00b\"", Json.escribir("a\ude00b"));
        assertEquals("\"\\ud83d\ud83d\ude00\"", Json.escribir("\ud83d\ud83d\ude00"));
    }

    @Test
    void idaYVueltaDeTextoConTildesEmojiSaltosYComillas() {
        String original = "Peña — 😀\n\"q\"";
        assertEquals(original, Json.parse(Json.escribir(original)));
        String raro = "\u0000\u001f\\/\"ñ😀\u2028\u007f";
        assertEquals(raro, Json.parse(Json.escribir(raro)));
    }

    @Test
    void escribirNumerosDecimalesSinNotacionCientificaParaValoresNormales() {
        assertEquals("12.5", Json.escribir(12.5));
        assertEquals("100.0", Json.escribir(100.0));
        assertEquals("0.0", Json.escribir(0.0));
        assertEquals("-3.25", Json.escribir(-3.25));
        assertEquals("10000000", Json.escribir(1.0E7));
        assertEquals("123456789012", Json.escribir(123456789012.0));
        assertEquals("0.0001", Json.escribir(0.0001));
        assertEquals("0.00001", Json.escribir(1.0E-5));
        assertEquals("1.5", Json.escribir(1.5f));
    }

    @Test
    void escribirNoFinitosEsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> Json.escribir(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Json.escribir(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> Json.escribir(Float.NEGATIVE_INFINITY));
    }

    @Test
    void escribirBigDecimalComoNumeroPlano() {
        assertEquals("12.50", Json.escribir(new BigDecimal("12.50")));
        assertEquals("1000", Json.escribir(new BigDecimal("1E+3")));
        assertEquals("0.00000001", Json.escribir(new BigDecimal("1E-8")));
        assertEquals("25000.00", Json.escribir(new BigDecimal("25000.00")));
    }

    @Test
    void escribirTiposDeValorConTextoPropio() {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        assertEquals("\"123e4567-e89b-12d3-a456-426614174000\"", Json.escribir(id));
        assertEquals("\"VERDE\"", Json.escribir(Color.VERDE));
        assertEquals("\"2026-11-10T15:30:00Z\"", Json.escribir(Instant.parse("2026-11-10T15:30:00Z")));
        assertEquals("\"2026-11-10\"", Json.escribir(LocalDate.of(2026, 11, 10)));
        assertEquals("\"09:30\"", Json.escribir(LocalTime.of(9, 30)));
    }

    @Test
    void escribirMapasListasYArreglos() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("b", 1);
        m.put("a", List.of("x", "y"));
        m.put("c", Map.of("k", true));
        assertEquals("{\"b\":1,\"a\":[\"x\",\"y\"],\"c\":{\"k\":true}}", Json.escribir(m));
        assertEquals("[1,2,3]", Json.escribir(new int[] {1, 2, 3}));
        assertEquals("[\"a\",\"b\"]", Json.escribir(new String[] {"a", "b"}));
        assertEquals("[[1],[2]]", Json.escribir(new Object[] {List.of(1), new long[] {2}}));
        assertEquals("[]", Json.escribir(List.of()));
        assertEquals("{}", Json.escribir(Map.of()));
        assertEquals("[\"b\",\"a\"]", Json.escribir(new LinkedHashSet<>(List.of("b", "a"))));
    }

    @Test
    void escribirListaConNuloConservaElNulo() {
        List<Object> lista = new ArrayList<>();
        lista.add(1);
        lista.add(null);
        assertEquals("[1,null]", Json.escribir(lista));
    }

    @Test
    void escribirMapaOmiteLosValoresNulosYAceptaClavesNoTexto() {
        Map<Object, Object> m = new LinkedHashMap<>();
        m.put("a", null);
        m.put(Color.ROJO, 1);
        m.put(5, 2);
        assertEquals("{\"ROJO\":1,\"5\":2}", Json.escribir(m));
        Map<Object, Object> conClaveNula = new LinkedHashMap<>();
        conClaveNula.put(null, 1);
        assertThrows(IllegalArgumentException.class, () -> Json.escribir(conClaveNula));
    }

    @Test
    void escribirRecordOmiteLosNulosYSigueElOrdenDeDeclaracion() {
        assertEquals("{\"nombre\":\"Ana\"}", Json.escribir(new Punto("Ana", null)));
        assertEquals("{\"x\":3}", Json.escribir(new Punto(null, 3)));
        assertEquals("{}", Json.escribir(new Punto(null, null)));
        assertEquals("{\"nombre\":\"Ana\",\"x\":3}", Json.escribir(new Punto("Ana", 3)));
    }

    @Test
    void escribirRecordCompletoConAnidadosEnumsYPrimitivos() {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        Salida salida = new Salida(id, "Peña", null, 3, 0.5, 12, Color.ROJO,
                List.of(new Punto("a", 1), new Punto("b", null)));
        assertEquals("{\"id\":\"123e4567-e89b-12d3-a456-426614174000\",\"nombre\":\"Peña\",\"usos\":3,"
                + "\"ocupacion\":0.5,\"actual\":12,\"color\":\"ROJO\",\"puntos\":[{\"nombre\":\"a\",\"x\":1},"
                + "{\"nombre\":\"b\"}]}", Json.escribir(salida));
    }

    @Test
    void escribirPrecioBigDecimalComoNumeroPlano() {
        Servicio s = new Servicio("Corte", 30, true, new BigDecimal("12.50"), List.of());
        assertEquals("{\"nombre\":\"Corte\",\"duracionMin\":30,\"activo\":true,\"valor\":12.50,\"ventanas\":[]}",
                Json.escribir(s));
    }

    @Test
    void escribirOptionalYTiposDesconocidosEsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> Json.escribir(Optional.of(1)));
        assertThrows(IllegalArgumentException.class, () -> Json.escribir(Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> Json.escribir(new Object()));
        assertThrows(IllegalArgumentException.class, () -> Json.escribir(new StringBuilder("x")));
        assertThrows(IllegalArgumentException.class, () -> Json.escribir(new java.util.concurrent.atomic.AtomicInteger(1)));
        assertThrows(IllegalArgumentException.class, () -> Json.escribir(java.time.LocalDateTime.now()));
        assertThrows(IllegalArgumentException.class, () -> Json.escribir(List.of(new Object())));
    }

    @Test
    void escribirOptionalDentroDeUnRecordEsIllegalArgument() {
        record ConOpcional(Optional<String> valor) {
        }
        assertThrows(IllegalArgumentException.class, () -> Json.escribir(new ConOpcional(Optional.empty())));
    }

    // ---------------------------------------------------------------- leer

    private static Map<String, Object> objeto(String json) {
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) Json.parse(json);
        return m;
    }

    @Test
    void leerConstruyeUnRecordConAnidadosListasYBigDecimal() {
        Servicio s = Json.leer(objeto("{\"nombre\":\"Corte\",\"duracionMin\":30,\"activo\":true,\"valor\":12.50,"
                + "\"ventanas\":[{\"id\":\"123e4567-e89b-12d3-a456-426614174000\",\"dia\":1,\"inicio\":\"09:00\","
                + "\"fin\":\"12:00\"},{\"dia\":2}]}"), Servicio.class);

        assertEquals("Corte", s.nombre());
        assertEquals(30, s.duracionMin());
        assertEquals(Boolean.TRUE, s.activo());
        assertEquals(0, new BigDecimal("12.50").compareTo(s.valor()));
        assertEquals(2, s.ventanas().size());
        assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), s.ventanas().get(0).id());
        assertEquals("09:00", s.ventanas().get(0).inicio());
        assertNull(s.ventanas().get(1).id());
        assertEquals(2, s.ventanas().get(1).dia());
        assertNull(s.ventanas().get(1).fin());
    }

    @Test
    void leerValorEnteroDeBigDecimalYDecimalesLargos() {
        assertEquals(new BigDecimal("15000"), Json.leer(objeto("{\"valor\":15000}"), Servicio.class).valor());
        assertEquals(new BigDecimal("19.99"), Json.leer(objeto("{\"valor\":19.99}"), Servicio.class).valor());
        assertEquals(new BigDecimal("0.1"), Json.leer(objeto("{\"valor\":0.1}"), Servicio.class).valor());
        assertEquals(new BigDecimal("1000.0"), Json.leer(objeto("{\"valor\":1e3}"), Servicio.class).valor());
    }

    @Test
    void leerCamposAusentesOExplicitamenteNulosDanNull() {
        Servicio vacio = Json.leer(objeto("{}"), Servicio.class);
        assertNull(vacio.nombre());
        assertNull(vacio.duracionMin());
        assertNull(vacio.activo());
        assertNull(vacio.valor());
        assertNull(vacio.ventanas());

        Punto p = Json.leer(objeto("{\"nombre\":null,\"x\":null}"), Punto.class);
        assertNull(p.nombre());
        assertNull(p.x());
    }

    @Test
    void leerIgnoraLosCamposDesconocidos() {
        Punto p = Json.leer(objeto("{\"nombre\":\"a\",\"extra\":{\"k\":[1,2]},\"x\":4,\"otro\":null}"), Punto.class);
        assertEquals(new Punto("a", 4), p);
    }

    @Test
    void leerAnidadoYUuid() {
        Reserva r = Json.leer(objeto("{\"servicioId\":\"123E4567-E89B-12D3-A456-426614174000\",\"fecha\":\"2026-11-10\","
                + "\"cliente\":{\"nombre\":\"Ana\",\"correo\":\"a@x.co\",\"basura\":1}}"), Reserva.class);
        assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), r.servicioId());
        assertEquals(new Cliente("Ana", "a@x.co"), r.cliente());
    }

    @Test
    void leerSetYListaDeTexto() {
        Rol rol = Json.leer(objeto("{\"nombre\":\"Admin\",\"permisos\":[\"b\",\"a\",\"b\"],\"otros\":[\"z\",null,\"y\"]}"),
                Rol.class);
        assertEquals(List.of("b", "a"), new ArrayList<>(rol.permisos()));
        assertInstanceOf(LinkedHashSet.class, rol.permisos());
        List<String> esperada = new ArrayList<>();
        esperada.add("z");
        esperada.add(null);
        esperada.add("y");
        assertEquals(esperada, rol.otros());
    }

    @Test
    void leerEnterosSinParteDecimalYNumeroGrandeYEnum() {
        Mixto m = Json.leer(objeto("{\"grande\":9007199254740993,\"real\":3,\"color\":\"VERDE\",\"ok\":false}"),
                Mixto.class);
        assertEquals(9007199254740993L, m.grande());
        assertEquals(3.0, m.real());
        assertEquals(Color.VERDE, m.color());
        assertEquals(Boolean.FALSE, m.ok());
        assertEquals(1000, Json.leer(objeto("{\"x\":1e3}"), Punto.class).x());
        assertEquals(-5, Json.leer(objeto("{\"x\":-5}"), Punto.class).x());
    }

    @Test
    void leerTipoEquivocadoEsJsonInvalido() {
        for (String json : List.of(
                "{\"nombre\":5}",
                "{\"nombre\":true}",
                "{\"nombre\":[\"a\"]}",
                "{\"nombre\":{\"a\":1}}",
                "{\"x\":\"5\"}",
                "{\"x\":1.5}",
                "{\"x\":true}",
                "{\"x\":2147483648}",
                "{\"x\":-2147483649}",
                "{\"x\":1e20}",
                "{\"x\":[]}")) {
            assertThrows(JsonInvalido.class, () -> Json.leer(objeto(json), Punto.class), json);
        }
        assertThrows(JsonInvalido.class, () -> Json.leer(objeto("{\"activo\":\"true\"}"), Servicio.class));
        assertThrows(JsonInvalido.class, () -> Json.leer(objeto("{\"activo\":1}"), Servicio.class));
        assertThrows(JsonInvalido.class, () -> Json.leer(objeto("{\"valor\":\"12.5\"}"), Servicio.class));
        assertThrows(JsonInvalido.class, () -> Json.leer(objeto("{\"valor\":true}"), Servicio.class));
        assertThrows(JsonInvalido.class, () -> Json.leer(objeto("{\"ventanas\":{}}"), Servicio.class));
        assertThrows(JsonInvalido.class, () -> Json.leer(objeto("{\"ventanas\":[1]}"), Servicio.class));
        assertThrows(JsonInvalido.class, () -> Json.leer(objeto("{\"ventanas\":\"x\"}"), Servicio.class));
        assertThrows(JsonInvalido.class, () -> Json.leer(objeto("{\"cliente\":\"Ana\"}"), Reserva.class));
        assertThrows(JsonInvalido.class, () -> Json.leer(objeto("{\"cliente\":[]}"), Reserva.class));
        assertThrows(JsonInvalido.class, () -> Json.leer(objeto("{\"permisos\":[1]}"), Rol.class));
        assertThrows(JsonInvalido.class, () -> Json.leer(objeto("{\"permisos\":\"a\"}"), Rol.class));
        assertThrows(JsonInvalido.class, () -> Json.leer(objeto("{\"grande\":1.5}"), Mixto.class));
        assertThrows(JsonInvalido.class, () -> Json.leer(objeto("{\"real\":\"1\"}"), Mixto.class));
        assertThrows(JsonInvalido.class, () -> Json.leer(objeto("{\"color\":\"AZUL\"}"), Mixto.class));
        assertThrows(JsonInvalido.class, () -> Json.leer(objeto("{\"color\":1}"), Mixto.class));
    }

    @Test
    void leerUuidInvalidoEsJsonInvalido() {
        for (String texto : List.of("no-es-uuid", "", "123e4567e89b12d3a456426614174000",
                "123e4567-e89b-12d3-a456-42661417400", "123e4567-e89b-12d3-a456-4266141740000",
                "1-1-1-1-1", "123e4567-e89b-12d3-a456-42661417400g", "+23e4567-e89b-12d3-a456-426614174000")) {
            assertThrows(JsonInvalido.class,
                    () -> Json.leer(objeto("{\"servicioId\":\"" + texto + "\"}"), Reserva.class), texto);
        }
        assertThrows(JsonInvalido.class, () -> Json.leer(objeto("{\"servicioId\":5}"), Reserva.class));
    }

    @Test
    void leerRecordConPrimitivosUsaElValorPorDefectoSiFaltaONulo() {
        Primitivos p = Json.leer(objeto("{}"), Primitivos.class);
        assertEquals(new Primitivos(0, false, 0.0, 0L), p);
        assertEquals(new Primitivos(0, false, 0.0, 0L), Json.leer(objeto("{\"n\":null,\"b\":null}"), Primitivos.class));
        assertEquals(new Primitivos(4, true, 2.5, 9L),
                Json.leer(objeto("{\"n\":4,\"b\":true,\"d\":2.5,\"l\":9}"), Primitivos.class));
        assertThrows(JsonInvalido.class, () -> Json.leer(objeto("{\"n\":\"4\"}"), Primitivos.class));
    }

    @Test
    void leerDeNuloEsJsonInvalido() {
        assertThrows(JsonInvalido.class, () -> Json.leer(null, Punto.class));
    }

    @Test
    void leerPropagaLaExcepcionDelConstructorCompacto() {
        assertEquals("vacío", assertThrows(IllegalStateException.class,
                () -> Json.leer(objeto("{\"nombre\":\"\"}"), Estricto.class)).getMessage());
    }

    @Test
    void leerNoRevelaElValorRecibidoEnElMensaje() {
        JsonInvalido e = assertThrows(JsonInvalido.class,
                () -> Json.leer(objeto("{\"servicioId\":\"secreto-123\"}"), Reserva.class));
        assertTrue(!e.getMessage().contains("secreto-123"));
    }

    @Test
    void escribirYLeerSonSimetricosParaUnaPeticionCompleta() {
        String json = "{\"servicioId\":\"123e4567-e89b-12d3-a456-426614174000\",\"fecha\":\"2026-11-10\","
                + "\"cliente\":{\"nombre\":\"Peña 😀\",\"correo\":\"a@x.co\"}}";
        Reserva r = Json.leer(objeto(json), Reserva.class);
        assertEquals(json, Json.escribir(r));
        assertEquals(r, Json.leer(objeto(Json.escribir(r)), Reserva.class));
    }
}

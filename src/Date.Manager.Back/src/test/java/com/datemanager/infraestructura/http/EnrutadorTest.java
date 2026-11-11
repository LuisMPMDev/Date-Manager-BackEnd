package com.datemanager.infraestructura.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class EnrutadorTest {

    private static Ruta ruta(String metodo, String patron) {
        return new Ruta(metodo, patron, Set.of(), true, s -> Respuesta.sinContenido(), null);
    }

    @Test
    void extraeYDecodificaLosParametros() {
        Enrutador rutas = new Enrutador().agregar(ruta("GET", "/api/{tipo}/{id}/estado"));

        Enrutador.Coincidencia c = rutas.buscar("GET", "/api/citas/ni%C3%B1o%2Fx/estado");

        assertEquals(Map.of("tipo", "citas", "id", "niño/x"), c.parametros());
    }

    @Test
    void elLiteralGanaAlParametroSeaCualSeaElOrdenDeRegistro() {
        Ruta literal = ruta("GET", "/api/citas/hoy");
        Ruta parametro = ruta("GET", "/api/citas/{id}");

        assertSame(literal, new Enrutador().agregar(literal).agregar(parametro).buscar("GET", "/api/citas/hoy").ruta());
        assertSame(literal, new Enrutador().agregar(parametro).agregar(literal).buscar("GET", "/api/citas/hoy").ruta());
        assertSame(parametro, new Enrutador().agregar(parametro).agregar(literal).buscar("GET", "/api/citas/x").ruta());
    }

    @Test
    void elPrimerSegmentoDondeDifierenDecide() {
        Ruta a = ruta("GET", "/api/{x}/fijo");
        Ruta b = ruta("GET", "/api/fijo/{y}");

        assertSame(b, new Enrutador().agregar(a).agregar(b).buscar("GET", "/api/fijo/zzz").ruta());
        assertSame(a, new Enrutador().agregar(a).agregar(b).buscar("GET", "/api/otro/fijo").ruta());
    }

    @Test
    void unParametroNoEncajaConUnSegmentoVacio() {
        Enrutador rutas = new Enrutador().agregar(ruta("GET", "/api/{id}"));

        assertEquals(404, assertThrows(ErrorHttp.class, () -> rutas.buscar("GET", "/api/")).estado());
    }

    @Test
    void rechazaRutasRepetidasPeroNoElMismoPatronConOtroMetodo() {
        Enrutador rutas = new Enrutador().agregar(ruta("GET", "/api/{id}")).agregar(ruta("POST", "/api/{id}"));

        assertThrows(IllegalArgumentException.class, () -> rutas.agregar(ruta("GET", "/api/{otro}")));
        assertEquals(List.of("/api/{id}", "/api/{id}"), rutas.rutas().stream().map(Ruta::patron).toList());
    }

    @Test
    void rechazaPatronesMalFormados() {
        for (String patron : List.of("api/x", "/api/{}", "/api/{a}/{a}", "/api/a{b}", "/api/{a", "/api//x", "/api/x/")) {
            assertThrows(IllegalArgumentException.class, () -> ruta("GET", patron), patron);
        }
    }

    @Test
    void rechazaRutasContradictorias() {
        Manejador m = s -> Respuesta.sinContenido();

        assertThrows(IllegalArgumentException.class, () -> new Ruta("GET", "/a", Set.of("p"), true, m, null));
        assertThrows(IllegalArgumentException.class, () -> new Ruta("HEAD", "/a", Set.of(), true, m, null));
        assertThrows(IllegalArgumentException.class, () -> new Ruta("OPTIONS", "/a", Set.of(), true, m, null));
        assertThrows(IllegalArgumentException.class, () -> new Ruta("GET", "/a", Set.of(), true, null, null));
    }

    @Test
    void unaRutaQueNoEmpiezaPorBarraEsUn404() {
        Enrutador rutas = new Enrutador().agregar(ruta("GET", "/api/x"));

        assertEquals(404, assertThrows(ErrorHttp.class, () -> rutas.buscar("GET", "*")).estado());
    }
}

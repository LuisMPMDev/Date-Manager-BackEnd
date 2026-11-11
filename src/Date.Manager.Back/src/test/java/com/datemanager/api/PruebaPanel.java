package com.datemanager.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.UUID;

import com.datemanager.CodificadorFalso;
import com.datemanager.PruebaIntegracion;
import com.datemanager.infraestructura.seguridad.Bcrypt;

/** Utilidades para probar las rutas del panel: crear roles y usuarios por SQL, emitir tokens y llamar con ellos. */
abstract class PruebaPanel extends PruebaIntegracion {

    protected UUID crearRol(String nombre, String... permisos) {
        UUID id = UUID.randomUUID();
        ejecutar("INSERT INTO rol (id, nombre, descripcion) VALUES (?, ?, '')", id, nombre);
        for (String permiso : permisos) {
            ejecutar("INSERT INTO rol_permiso (rol_id, permiso) VALUES (?, ?)", id, permiso);
        }
        return id;
    }

    /**
     * Usuario con un hash de la forma que exige el esquema (no BCrypt real: estas pruebas entran con
     * {@link #tokenDe(UUID)}, no con el login, y así no pagan un cálculo BCrypt por usuario).
     */
    protected UUID crearUsuario(String usuario, UUID rolId) {
        UUID id = UUID.randomUUID();
        ejecutar("""
                INSERT INTO usuario (id, nombre, apellido, correo, telefono, usuario, clave_hash, rol_id)
                VALUES (?, ?, 'Prueba', ?, '300 555 0000', ?, ?, ?)
                """, id, usuario, usuario + "@example.com", usuario, CodificadorFalso.hash("clave-" + usuario), rolId);
        return id;
    }

    /** Usuario con el hash BCrypt real de {@code clave} (para las pruebas que comprueban la clave guardada). */
    protected UUID crearUsuario(String usuario, UUID rolId, String clave) {
        UUID id = UUID.randomUUID();
        ejecutar("""
                INSERT INTO usuario (id, nombre, apellido, correo, telefono, usuario, clave_hash, rol_id)
                VALUES (?, ?, 'Prueba', ?, '300 555 0000', ?, ?, ?)
                """, id, usuario, usuario + "@example.com", usuario, BCRYPT.codificar(clave), rolId);
        return id;
    }

    protected static final Bcrypt BCRYPT = new Bcrypt();

    protected static String hashDe(UUID usuarioId) {
        return consultarTexto("SELECT clave_hash FROM usuario WHERE id = ?", usuarioId);
    }

    protected static Resp eliminar(String ruta, UUID actor) {
        return delete(ruta).conToken(tokenDe(actor)).enviar();
    }

    protected static int contar(String tabla) {
        return consultarInt("SELECT COUNT(*) FROM " + tabla);
    }

    protected static Resp obtener(String ruta, UUID actor) {
        return get(ruta).conToken(tokenDe(actor)).enviar();
    }

    protected static Resp guardar(String ruta, UUID actor, Object cuerpo) {
        return put(ruta).conToken(tokenDe(actor)).cuerpo(cuerpo).enviar();
    }

    /** Exige el estado HTTP; el mensaje de fallo incluye el cuerpo recibido. */
    protected static Resp exigir(int estado, Resp r) {
        assertEquals(estado, r.estado(), () -> "cuerpo: " + r.texto());
        return r;
    }

    /** Exige el estado y el {@code codigo} del JSON de error. */
    protected static Resp exigir(int estado, String codigo, Resp r) {
        exigir(estado, r);
        assertEquals(codigo, r.en("codigo"), () -> "cuerpo: " + r.texto());
        return r;
    }

    /** Compara números por valor (50000 == 50000.00). */
    protected static void igualNumero(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), () -> "esperado " + esperado + ", recibido " + real);
    }
}

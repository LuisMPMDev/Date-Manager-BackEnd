package com.datemanager.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.Map;
import java.util.UUID;

import com.datemanager.PruebaIntegracion;
import com.datemanager.infraestructura.seguridad.ServicioJwt;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

/** Utilidades para probar las rutas del panel: crear roles y usuarios por SQL, emitir tokens y llamar con ellos. */
abstract class PruebaPanel extends PruebaIntegracion {

    @Autowired
    protected PasswordEncoder codificador;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    private ServicioJwt servicioJwt;

    protected UUID crearRol(String nombre, String... permisos) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO rol (id, nombre, descripcion) VALUES (?, ?, '')", id, nombre);
        for (String permiso : permisos) {
            jdbc.update("INSERT INTO rol_permiso (rol_id, permiso) VALUES (?, ?)", id, permiso);
        }
        return id;
    }

    protected UUID crearUsuario(String usuario, UUID rolId, String clave) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuario (id, nombre, apellido, correo, telefono, usuario, clave_hash, rol_id)
                VALUES (?, ?, 'Prueba', ?, '300 555 0000', ?, ?, ?)
                """, id, usuario, usuario + "@example.com", usuario, codificador.encode(clave), rolId);
        return id;
    }

    protected UUID crearUsuario(String usuario, UUID rolId) {
        return crearUsuario(usuario, rolId, "clave-original-" + usuario);
    }

    protected String tokenDe(UUID usuarioId) {
        return servicioJwt.emitir(usuarioId);
    }

    protected String hashDe(UUID usuarioId) {
        return jdbc.queryForObject("SELECT clave_hash FROM usuario WHERE id = ?", String.class, usuarioId);
    }

    protected int contar(String tabla) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
    }

    protected ResultActions obtener(String ruta, UUID actor) throws Exception {
        return mvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(actor)));
    }

    protected ResultActions guardar(String ruta, UUID actor, Map<String, ?> cuerpo) throws Exception {
        return mvc.perform(put(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(actor))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }

    protected ResultActions eliminar(String ruta, UUID actor) throws Exception {
        return mvc.perform(delete(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(actor)));
    }
}

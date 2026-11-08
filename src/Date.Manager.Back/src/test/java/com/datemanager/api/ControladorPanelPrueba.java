package com.datemanager.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controlador SOLO de pruebas (vive en src/test): rutas protegidas para verificar 401 y 403. */
@RestController
@RequestMapping("/api/panel/_prueba")
class ControladorPanelPrueba {

    @GetMapping("/citas")
    @PreAuthorize("@permisos.tiene('ver_citas')")
    String citas() {
        return "ok";
    }

    @GetMapping("/cualquiera")
    @PreAuthorize("@permisos.tiene('gestionar_servicios', 'ver_panel')")
    String cualquiera() {
        return "ok";
    }

    /** Una excepción de autenticación lanzada dentro de un controlador debe ser 401, no 500. */
    @GetMapping("/no-autenticado")
    void noAutenticado() {
        throw new InsufficientAuthenticationException("x");
    }
}

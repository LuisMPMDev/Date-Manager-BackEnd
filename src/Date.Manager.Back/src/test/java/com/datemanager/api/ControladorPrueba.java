package com.datemanager.api;

import com.datemanager.dominio.CodigoError;
import com.datemanager.dominio.ErrorNegocio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controlador SOLO de pruebas (vive en src/test): provoca cada tipo de error para verificar el manejador. */
@RestController
@RequestMapping("/api/publico/_prueba")
class ControladorPrueba {

    record Cuerpo(@NotBlank(message = "es obligatorio") String nombre) {
    }

    @GetMapping("/error-negocio/{codigo}")
    void errorNegocio(@PathVariable CodigoError codigo) {
        throw new ErrorNegocio(codigo, "x");
    }

    @PostMapping("/validacion")
    void validacion(@Valid @RequestBody Cuerpo cuerpo) {
    }

    /** Última línea de defensa: una violación de restricción de la base no puede ser un 500. */
    @GetMapping("/violacion-unica")
    void violacionUnica() {
        throw new org.springframework.dao.DataIntegrityViolationException("Violation of UNIQUE KEY (detalle interno)");
    }

    @GetMapping("/inesperado")
    void inesperado() {
        throw new IllegalStateException("detalle interno que no debe filtrarse");
    }
}

package com.datemanager.api;

import com.datemanager.aplicacion.AutenticacionServicio;
import com.datemanager.aplicacion.AutenticacionServicio.Sesion;
import com.datemanager.infraestructura.seguridad.UsuarioActual;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthControlador {

    /** Petición de login. La clave es exacta (no se recorta); el usuario se normaliza en el servicio. */
    public record LoginPeticion(
            @NotBlank(message = "Escribe el usuario.") String usuario,
            @NotEmpty(message = "Escribe la contraseña.") String clave) {
    }

    /** {@code token} solo viene en el login; en {@code /yo} se omite (nulo). */
    public record SesionDto(String token, UsuarioDto usuario, RolDto rol) {

        static SesionDto de(Sesion sesion) {
            return new SesionDto(sesion.token(), UsuarioDto.de(sesion.usuario()), RolDto.de(sesion.rol()));
        }
    }

    private final AutenticacionServicio autenticacion;

    public AuthControlador(AutenticacionServicio autenticacion) {
        this.autenticacion = autenticacion;
    }

    @PostMapping("/login")
    public SesionDto login(@Valid @RequestBody LoginPeticion peticion) {
        return SesionDto.de(autenticacion.iniciarSesion(peticion.usuario(), peticion.clave()));
    }

    @GetMapping("/yo")
    public SesionDto yo(@AuthenticationPrincipal UsuarioActual actual) {
        return SesionDto.de(autenticacion.yo(actual.id()));
    }
}

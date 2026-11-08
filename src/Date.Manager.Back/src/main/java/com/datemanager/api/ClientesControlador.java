package com.datemanager.api;

import java.util.List;
import java.util.UUID;

import com.datemanager.aplicacion.ClientesServicio;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Clientes del panel (listar y editar; nacen al reservar). Sin reglas: delega en {@link ClientesServicio}. */
@RestController
@RequestMapping("/api/panel/clientes")
public class ClientesControlador {

    private final ClientesServicio clientes;

    public ClientesControlador(ClientesServicio clientes) {
        this.clientes = clientes;
    }

    @GetMapping
    @PreAuthorize("@permisos.tiene('ver_citas', 'gestionar_clientes')")
    public List<ClienteDto> listar() {
        return clientes.listar().stream().map(ClienteDto::de).toList();
    }

    /** El id del cuerpo, si viene, se ignora: manda el de la ruta. */
    @PutMapping("/{id}")
    @PreAuthorize("@permisos.tiene('gestionar_clientes')")
    public ClienteDto guardar(@PathVariable UUID id, @Valid @RequestBody ClientePeticion peticion) {
        return ClienteDto.de(clientes.guardar(id, peticion.aDatos()));
    }
}

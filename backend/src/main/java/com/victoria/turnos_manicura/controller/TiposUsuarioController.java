package com.victoria.turnos_manicura.controller;

import com.victoria.turnos_manicura.model.TiposUsuarioModel;
import com.victoria.turnos_manicura.service.AuthService;
import com.victoria.turnos_manicura.service.TiposUsuarioService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tiposUsuario")
public class TiposUsuarioController {

    private final TiposUsuarioService service;
    private final AuthService authService;

    public TiposUsuarioController(TiposUsuarioService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping
    public List<TiposUsuarioModel> listar() {
        authService.requerirSesion();
        return service.listar();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public TiposUsuarioModel crear(@Valid @RequestBody TiposUsuarioModel tipoUsuario) {
        return service.guardar(tipoUsuario);
    }

}

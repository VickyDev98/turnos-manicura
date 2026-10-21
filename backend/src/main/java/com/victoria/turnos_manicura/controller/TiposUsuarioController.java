package com.victoria.turnos_manicura.controller;

import com.victoria.turnos_manicura.model.TiposUsuarioModel;
import com.victoria.turnos_manicura.service.AuthService;
import com.victoria.turnos_manicura.service.TiposUsuarioService;
import jakarta.validation.Valid;
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
    public List<TiposUsuarioModel> listar(@RequestHeader(value = "Authorization", required = false) String auth) {
        authService.requerirSesion(auth);
        return service.listar();
    }

    @PostMapping
    public TiposUsuarioModel crear(@RequestHeader(value = "Authorization", required = false) String auth,
                                   @Valid @RequestBody TiposUsuarioModel tipoUsuario) {
        authService.requerirAdmin(auth);
        return service.guardar(tipoUsuario);
    }

}

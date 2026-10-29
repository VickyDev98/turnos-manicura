package com.victoria.turnos_manicura.controller;

import com.victoria.turnos_manicura.dto.ActivoRequest;
import com.victoria.turnos_manicura.dto.UsuarioUpdate;
import com.victoria.turnos_manicura.model.UsuariosModel;
import com.victoria.turnos_manicura.service.AuthService;
import com.victoria.turnos_manicura.service.UsuariosService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/usuarios")
public class UsuariosController {

    private final UsuariosService service;
    private final AuthService authService;

    public UsuariosController(UsuariosService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    // El admin ve todo; el resto ve los nombres (y los datos de contacto solo de las manicuristas y los propios)
    @GetMapping
    public List<UsuariosModel> listar() {
        return service.listarPara(authService.requerirSesion());
    }

    // Solo el admin crea usuarios desde el sistema (el resto se registra desde la pantalla de login)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public UsuariosModel crear(@Valid @RequestBody UsuariosModel usuario) {
        return service.guardar(usuario);
    }

    // Edición de un usuario (solo el admin)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public UsuariosModel actualizar(@PathVariable Long id,
                                    @Valid @RequestBody UsuarioUpdate datos) {
        return service.actualizar(id, datos);
    }

    // Baja o reactivación (solo el admin). Al darlo de baja pierde el acceso enseguida: ver AuthService.requerirSesion
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/activo")
    public UsuariosModel cambiarActivo(@PathVariable Long id,
                                       @Valid @RequestBody ActivoRequest pedido) {
        AuthService.Sesion admin = authService.requerirSesion();
        return service.cambiarActivo(id, pedido.getActivo(), admin.getIdUsuario());
    }
}

package com.victoria.turnos_manicura.controller;

import com.victoria.turnos_manicura.dto.ActivoRequest;
import com.victoria.turnos_manicura.dto.UsuarioUpdate;
import com.victoria.turnos_manicura.model.UsuariosModel;
import com.victoria.turnos_manicura.service.AuthService;
import com.victoria.turnos_manicura.service.UsuariosService;
import jakarta.validation.Valid;
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
    public List<UsuariosModel> listar(@RequestHeader(value = "Authorization", required = false) String auth) {
        return service.listarPara(authService.requerirSesion(auth));
    }

    // Solo el admin crea usuarios desde el sistema (el resto se registra desde la pantalla de login)
    @PostMapping
    public UsuariosModel crear(@RequestHeader(value = "Authorization", required = false) String auth,
                               @Valid @RequestBody UsuariosModel usuario) {
        authService.requerirAdmin(auth);
        return service.guardar(usuario);
    }

    // Edición de un usuario (solo el admin)
    @PutMapping("/{id}")
    public UsuariosModel actualizar(@RequestHeader(value = "Authorization", required = false) String auth,
                                    @PathVariable Long id,
                                    @Valid @RequestBody UsuarioUpdate datos) {
        authService.requerirAdmin(auth);
        return service.actualizar(id, datos);
    }

    // Baja o reactivación (solo el admin). Al darlo de baja se cierran sus sesiones abiertas
    @PutMapping("/{id}/activo")
    public UsuariosModel cambiarActivo(@RequestHeader(value = "Authorization", required = false) String auth,
                                       @PathVariable Long id,
                                       @Valid @RequestBody ActivoRequest pedido) {
        AuthService.Sesion admin = authService.requerirAdmin(auth);
        UsuariosModel usuario = service.cambiarActivo(id, pedido.getActivo(), admin.getIdUsuario());
        if (!pedido.getActivo()) {
            authService.cerrarSesionesDe(id);
        }
        return usuario;
    }
}

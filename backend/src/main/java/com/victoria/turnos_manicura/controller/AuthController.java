package com.victoria.turnos_manicura.controller;

import com.victoria.turnos_manicura.dto.LoginRequest;
import com.victoria.turnos_manicura.dto.LoginResponse;
import com.victoria.turnos_manicura.dto.RegistroRequest;
import com.victoria.turnos_manicura.dto.RenovarRequest;
import com.victoria.turnos_manicura.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }

    // Alta de cliente o manicurista desde la pantalla de login; devuelve la sesión ya iniciada
    @PostMapping("/registro")
    public LoginResponse registro(@Valid @RequestBody RegistroRequest request) {
        return service.registrar(request);
    }

    // Pública: se llama justamente cuando el access token ya venció. La credencial es el refresh token del cuerpo
    @PostMapping("/renovar")
    public LoginResponse renovar(@Valid @RequestBody RenovarRequest request) {
        return service.renovar(request.getRefreshToken());
    }

    // El cuerpo es opcional: con el refresh token, también se revoca (si no, solo se cierra el access token)
    @PostMapping("/logout")
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @RequestBody(required = false) RenovarRequest request) {
        service.logout(authorization, request == null ? null : request.getRefreshToken());
    }
}

package com.victoria.turnos_manicura.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// rol: CLIENTE o MANICURISTA (la descripción del tipo de usuario)
@Getter
@AllArgsConstructor
public class LoginResponse {
    private final String token;
    private final Long id;
    private final String nombre;
    private final String apellido;
    private final String email;
    private final String rol;
}

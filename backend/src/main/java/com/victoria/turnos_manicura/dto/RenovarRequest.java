package com.victoria.turnos_manicura.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// Cuerpo de POST /auth/renovar (obligatorio) y de POST /auth/logout (opcional)
@Getter
@Setter
public class RenovarRequest {

    @NotBlank(message = "Falta el refresh token")
    private String refreshToken;
}

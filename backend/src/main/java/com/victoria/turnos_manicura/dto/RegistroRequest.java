package com.victoria.turnos_manicura.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

// Alta desde la pantalla de login: solo se puede elegir CLIENTE o MANICURISTA
@Getter
@Setter
public class RegistroRequest {

    @NotBlank(message = "Elegí si sos cliente o manicurista")
    private String rol;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 50, message = "El nombre no puede superar los 50 caracteres")
    @Pattern(regexp = "^[\\p{L} '.-]+$", message = "El nombre solo puede tener letras")
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 50, message = "El apellido no puede superar los 50 caracteres")
    @Pattern(regexp = "^[\\p{L} '.-]+$", message = "El apellido solo puede tener letras")
    private String apellido;

    // 0 = sin teléfono
    @PositiveOrZero(message = "El teléfono no puede ser negativo")
    private int numeroContacto;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato válido")
    @Size(max = 50, message = "El email no puede superar los 50 caracteres")
    private String email;

    @Size(max = 100, message = "La dirección no puede superar los 100 caracteres")
    private String direccion;

    @NotBlank(message = "La contraseña es obligatoria")
    private String password;
}

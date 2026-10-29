package com.victoria.turnos_manicura.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

// Edición de un usuario por el admin. El tipo no se cambia; la contraseña solo si se manda una nueva
@Getter
@Setter
public class UsuarioUpdate {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 50, message = "El nombre no puede superar los 50 caracteres")
    @Pattern(regexp = "^[\\p{L} '.-]+$", message = "El nombre solo puede tener letras")
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 50, message = "El apellido no puede superar los 50 caracteres")
    @Pattern(regexp = "^[\\p{L} '.-]+$", message = "El apellido solo puede tener letras")
    private String apellido;

    // Vacío o ausente = sin teléfono
    @Size(max = 20, message = "El teléfono no puede superar los 20 caracteres")
    private String numeroContacto;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato válido")
    @Size(max = 50, message = "El email no puede superar los 50 caracteres")
    private String email;

    @Size(max = 100, message = "La dirección no puede superar los 100 caracteres")
    private String direccion;

    // Vacío o ausente = no se cambia
    private String password;
}

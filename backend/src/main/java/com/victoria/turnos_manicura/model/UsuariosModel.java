package com.victoria.turnos_manicura.model;


import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name ="usuarios")
@Getter
@Setter
public class UsuariosModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Positive(message = "El tipo de usuario es obligatorio")
    @Column(nullable = false)
    private int tipo;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 50, message = "El nombre no puede superar los 50 caracteres")
    @Pattern(regexp = "^[\\p{L} '.-]+$", message = "El nombre solo puede tener letras")
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 50, message = "El apellido no puede superar los 50 caracteres")
    @Pattern(regexp = "^[\\p{L} '.-]+$", message = "El apellido solo puede tener letras")
    private String apellido;

    // 0 = sin teléfono. La columna es INT, por eso el máximo es 2147483647
    @PositiveOrZero(message = "El teléfono no puede ser negativo")
    @Column(name="numero_contacto")
    private int numeroContacto;

    @Email(message = "El email no tiene un formato válido")
    @Size(max = 50, message = "El email no puede superar los 50 caracteres")
    @Column(unique = true)
    private String email;

    // Dónde atiende (se muestra en los turnos del cliente)
    @Size(max = 100, message = "La dirección no puede superar los 100 caracteres")
    private String direccion;

    // Se guarda encriptada (BCrypt) y nunca se devuelve en las respuestas
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    // false = dado de baja: no puede iniciar sesión ni recibir turnos
    private boolean activo;


}

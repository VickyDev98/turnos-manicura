package com.victoria.turnos_manicura.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Entity
@Table(name ="servicios")
@Getter
@Setter

public class ServiciosModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 50, message = "El nombre no puede superar los 50 caracteres")
    @Column(nullable = false, unique = true)
    private String nombre;

    @Size(max = 50, message = "La descripción no puede superar los 50 caracteres")
    private String descripcion;

    @NotNull(message = "La duración es obligatoria")
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime duracion;

    // La columna es DECIMAL(10,2): como máximo 8 dígitos enteros
    @NotNull(message = "El precio es obligatorio")
    @Positive(message = "El precio tiene que ser mayor a 0")
    @Max(value = 99999999, message = "El precio es demasiado alto")
    private Integer precio;

    private Boolean activo;

}

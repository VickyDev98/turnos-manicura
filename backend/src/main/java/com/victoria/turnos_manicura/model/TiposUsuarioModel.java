package com.victoria.turnos_manicura.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
// En minúsculas: con "tiposUsuario" Spring busca la tabla "tipos_usuario", que no existe
@Table(name ="tiposusuario")
@Getter
@Setter
public class TiposUsuarioModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "La descripción es obligatoria")
    @Size(max = 50, message = "La descripción no puede superar los 50 caracteres")
    @Column(nullable = false, unique = true)
    private String descripcion;
}

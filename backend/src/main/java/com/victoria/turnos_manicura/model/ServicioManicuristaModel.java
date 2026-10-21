package com.victoria.turnos_manicura.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

// Un servicio del catálogo que ofrece una manicurista, con su propio precio
@Entity
@Table(name = "servicios_manicurista")
@Getter
@Setter
public class ServicioManicuristaModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_usuario_manicurista", nullable = false)
    private int idManicurista;

    @Column(name = "id_servicio", nullable = false)
    private int idServicio;

    @Column(nullable = false)
    private Integer precio;

    // Cuánto tarda ella en este servicio (parte de la duración del catálogo)
    @JsonFormat(pattern = "HH:mm:ss")
    @Column(nullable = false)
    private LocalTime duracion;

    @Column(nullable = false)
    private boolean activo;
}

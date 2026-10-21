package com.victoria.turnos_manicura.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "bloqueos")
@Getter
@Setter
public class BloqueoModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_usuario_manicurista", nullable = false)
    private int idManicurista;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Repeticion repeticion;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Column(nullable = false)
    private LocalDate fecha;

    @JsonFormat(pattern = "HH:mm")
    @Column(name = "hora_desde", nullable = false)
    private LocalTime horaDesde;

    @JsonFormat(pattern = "HH:mm")
    @Column(name = "hora_hasta", nullable = false)
    private LocalTime horaHasta;

    private String motivo;
}

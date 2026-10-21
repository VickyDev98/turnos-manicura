package com.victoria.turnos_manicura.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

// Franja en la que una manicurista atiende un día de la semana (1 = lunes ... 7 = domingo)
@Entity
@Table(name = "franjas_trabajo")
@Getter
@Setter
public class FranjaTrabajoModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_usuario_manicurista", nullable = false)
    private int idManicurista;

    @Column(name = "dia_semana", nullable = false)
    private int diaSemana;

    @JsonFormat(pattern = "HH:mm")
    @Column(name = "hora_desde", nullable = false)
    private LocalTime horaDesde;

    @JsonFormat(pattern = "HH:mm")
    @Column(name = "hora_hasta", nullable = false)
    private LocalTime horaHasta;
}

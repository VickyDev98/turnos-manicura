package com.victoria.turnos_manicura.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

// Reportes básicos del admin para un rango de fechas. Los turnos se ubican por el día en que son
// (el turno no guarda cuándo se canceló)
@Getter
@AllArgsConstructor
public class ReporteTurnos {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private final LocalDate desde;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private final LocalDate hasta;

    // Turnos activos (reservados) del rango
    private final long totalAsignados;
    private final long totalCancelados;

    private final List<ConteoServicio> asignadosPorServicio;
    private final List<ConteoPeriodo> canceladosPorDia;
    // Cada fecha es el lunes de la semana
    private final List<ConteoPeriodo> canceladosPorSemana;

    // Histórico (no depende del rango): clientes con más de LIMITE cancelaciones
    private final int limiteCancelaciones;
    private final List<ClienteCancelaciones> clientesConCancelaciones;
}

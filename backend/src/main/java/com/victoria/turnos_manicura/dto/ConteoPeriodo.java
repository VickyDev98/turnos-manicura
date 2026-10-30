package com.victoria.turnos_manicura.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

// Cantidad de turnos cancelados en un día; en el reporte semanal, "fecha" es el lunes de esa semana
@Getter
@AllArgsConstructor
public class ConteoPeriodo {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private final LocalDate fecha;

    private final long cantidad;
}

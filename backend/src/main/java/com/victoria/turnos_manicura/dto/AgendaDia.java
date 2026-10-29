package com.victoria.turnos_manicura.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

// Un día de la agenda semanal de una manicurista, con sus tramos ocupados
@Getter
@AllArgsConstructor
public class AgendaDia {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private final LocalDate fecha;

    private final List<AgendaBloque> bloques;
}

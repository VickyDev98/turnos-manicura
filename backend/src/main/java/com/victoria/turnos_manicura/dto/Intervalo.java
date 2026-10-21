package com.victoria.turnos_manicura.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalTime;

// Tramo del día en el que una manicurista no atiende (fuera de su horario o bloqueado)
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Intervalo {

    @JsonFormat(pattern = "HH:mm")
    private final LocalTime desde;

    @JsonFormat(pattern = "HH:mm")
    private final LocalTime hasta;

    private final String motivo;

    // null si es por estar fuera del horario de trabajo
    private final Long idBloqueo;
}

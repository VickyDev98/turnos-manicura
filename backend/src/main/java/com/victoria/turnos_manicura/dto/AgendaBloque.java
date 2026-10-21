package com.victoria.turnos_manicura.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

// Un tramo ocupado en la agenda de una manicurista
@Getter
@AllArgsConstructor
public class AgendaBloque {
    private final Long id;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime inicio;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime fin;

    private final String cliente;
    private final String servicio;

    // TURNO o NO_DISPONIBLE (bloqueo o fuera del horario de trabajo; en ese caso "servicio" trae el motivo)
    private final String tipo;
}

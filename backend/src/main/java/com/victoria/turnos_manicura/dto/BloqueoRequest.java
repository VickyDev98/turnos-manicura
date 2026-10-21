package com.victoria.turnos_manicura.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.victoria.turnos_manicura.model.Repeticion;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class BloqueoRequest {

    @NotNull(message = "La fecha es obligatoria")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fecha;

    @NotNull(message = "La hora de inicio es obligatoria")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime horaDesde;

    @NotNull(message = "La hora de fin es obligatoria")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime horaHasta;

    @NotNull(message = "Elegí cada cuánto se repite")
    private Repeticion repeticion;

    @Size(max = 50, message = "El motivo no puede superar los 50 caracteres")
    private String motivo;
}

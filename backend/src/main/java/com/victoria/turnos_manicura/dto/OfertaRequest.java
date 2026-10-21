package com.victoria.turnos_manicura.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

// Agregar un servicio del catálogo a los servicios de la manicurista (sin precio usa el del catálogo)
@Getter
@Setter
public class OfertaRequest {

    @NotNull(message = "Elegí un servicio")
    private Integer idServicio;

    @Positive(message = "El precio tiene que ser mayor a 0")
    @Max(value = 99999999, message = "El precio es demasiado alto")
    private Integer precio;

    // Cuánto tarda ella (formato HH:mm:ss); sin dato se usa la del catálogo
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime duracion;
}

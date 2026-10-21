package com.victoria.turnos_manicura.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActivoRequest {

    @NotNull(message = "Indicá si el usuario queda activo o de baja")
    private Boolean activo;
}

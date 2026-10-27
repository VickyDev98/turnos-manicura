package com.victoria.turnos_manicura.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AsistenciaRequest {

    @NotNull(message = "Indicá si el cliente asistió o no")
    private Boolean noAsistio;
}

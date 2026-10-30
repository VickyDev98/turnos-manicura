package com.victoria.turnos_manicura.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// Cuántos turnos tiene un servicio (fila del reporte "turnos por servicio")
@Getter
@AllArgsConstructor
public class ConteoServicio {
    private final String servicio;
    private final long cantidad;
}

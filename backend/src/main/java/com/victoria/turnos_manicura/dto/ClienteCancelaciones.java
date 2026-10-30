package com.victoria.turnos_manicura.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// Un cliente con muchas cancelaciones (fila del reporte)
@Getter
@AllArgsConstructor
public class ClienteCancelaciones {
    private final Long idCliente;
    private final String cliente;
    private final String email;
    private final long cancelaciones;
}

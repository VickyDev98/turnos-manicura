package com.victoria.turnos_manicura.exception;

import lombok.Getter;

// Error de una regla de negocio asociado a un campo del formulario (ej: email repetido)
@Getter
public class ValidacionException extends RuntimeException {

    private final String campo;

    public ValidacionException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }
}

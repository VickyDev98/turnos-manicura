package com.victoria.turnos_manicura.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorHandlerTest {

    private final ErrorHandler errorHandler = new ErrorHandler();

    @Test
    void baseNoDisponible_conErrorDeAccesoADatos_devuelve503ConMensajeClaro() {
        ResponseEntity<Map<String, Object>> respuesta = errorHandler.baseNoDisponible(
                new DataAccessResourceFailureException("Communications link failure"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, respuesta.getStatusCode());
        assertTrue(respuesta.getBody().get("message").toString().contains("No se pudo conectar con la base de datos"));
    }

    @Test
    void baseNoDisponible_alNoPoderAbrirLaTransaccion_devuelve503() {
        ResponseEntity<Map<String, Object>> respuesta = errorHandler.baseNoDisponible(
                new CannotCreateTransactionException("Could not open JPA EntityManager for transaction"));

        assertEquals(503, respuesta.getBody().get("status"));
    }

    @Test
    void baseNoDisponible_noFiltraDetallesInternosAlCliente() {
        ResponseEntity<Map<String, Object>> respuesta = errorHandler.baseNoDisponible(
                new DataAccessResourceFailureException("jdbc:mysql://mysql:3306/TURNOS root"));

        assertTrue(!respuesta.getBody().get("message").toString().contains("jdbc"));
    }
}

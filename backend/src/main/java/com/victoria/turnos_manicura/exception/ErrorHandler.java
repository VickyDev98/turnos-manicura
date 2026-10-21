package com.victoria.turnos_manicura.exception;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Devuelve todos los errores con el mismo formato:
 * { status, error, message, errores: { campo: mensaje } }
 */
@RestControllerAdvice
public class ErrorHandler {

    // Errores de las anotaciones (@NotBlank, @Email, etc.)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> camposInvalidos(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errores.putIfAbsent(e.getField(), e.getDefaultMessage()));

        return respuesta(HttpStatus.BAD_REQUEST, "Revisá los datos ingresados", errores);
    }

    // Reglas de negocio de los services
    @ExceptionHandler(ValidacionException.class)
    public ResponseEntity<Map<String, Object>> reglaDeNegocio(ValidacionException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getMessage(), Map.of(ex.getCampo(), ex.getMessage()));
    }

    // JSON mal formado o tipos incorrectos (ej: una fecha inválida)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> jsonInvalido(HttpMessageNotReadableException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, "Formato de datos inválido: revisá fechas, horas y números", Map.of());
    }

    // Restricciones de la base (claves foráneas, valores fuera de rango)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> errorDeBase(DataIntegrityViolationException ex) {
        return respuesta(HttpStatus.CONFLICT, "No se pudo guardar: los datos no son compatibles con la base de datos", Map.of());
    }

    // La base de datos no responde (apagada, puerto o credenciales incorrectas, red caída)
    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
    public ResponseEntity<Map<String, Object>> baseNoDisponible(Exception ex) {
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo conectar con la base de datos. Intentá de nuevo en unos minutos", Map.of());
    }

    private ResponseEntity<Map<String, Object>> respuesta(HttpStatus status, String mensaje, Map<String, String> errores) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", mensaje);
        body.put("errores", errores);
        return ResponseEntity.status(status).body(body);
    }
}

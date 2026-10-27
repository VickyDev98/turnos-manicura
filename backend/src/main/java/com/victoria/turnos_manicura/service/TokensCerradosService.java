package com.victoria.turnos_manicura.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tokens cerrados con logout (id del token -> cuándo vence). Un JWT no se puede "borrar" porque el
 * servidor no lo guarda, así que se anota acá hasta que venza solo. Se pierde al reiniciar el backend.
 */
@Service
public class TokensCerradosService {

    private final Map<String, Instant> cerrados = new ConcurrentHashMap<>();

    public void cerrar(String idToken, Instant venceEn) {
        cerrados.values().removeIf(vence -> vence.isBefore(Instant.now()));
        cerrados.put(idToken, venceEn);
    }

    public boolean estaCerrado(String idToken) {
        return cerrados.containsKey(idToken);
    }
}

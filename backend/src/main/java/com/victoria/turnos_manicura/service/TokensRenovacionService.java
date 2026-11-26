package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.model.TokenRenovacionModel;
import com.victoria.turnos_manicura.repository.TokensRenovacionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh tokens: un texto al azar que el navegador guarda junto al access token (el JWT, que dura poco) y
 * usa solo para pedir uno nuevo cuando vence. A diferencia del JWT, este sí se guarda en la base (su hash),
 * así que se puede revocar y sobrevive a un reinicio del backend.
 *
 * Rotación: cada vez que se usa, queda marcado como usado y se entrega otro. Si llega uno ya usado, alguien
 * tiene una copia (lo robaron, o lo usó el dueño y el ladrón después, no se sabe cuál es cuál), así que se
 * borran todos los del usuario y tiene que volver a iniciar sesión.
 */
@Service
public class TokensRenovacionService {

    public static final String SESION_VENCIDA = "Tu sesión venció, volvé a iniciar sesión";

    // 32 bytes al azar = 256 bits: imposible de adivinar, por eso alcanza un hash rápido (no hace falta BCrypt)
    private static final int BYTES_TOKEN = 32;

    private final SecureRandom azar = new SecureRandom();
    private final TokensRenovacionRepository repository;
    private final Duration vigencia;

    public TokensRenovacionService(TokensRenovacionRepository repository,
                                   @Value("${app.jwt.horas-vigencia-renovacion:8}") long horasVigencia) {
        this.repository = repository;
        this.vigencia = Duration.ofHours(horasVigencia);
    }

    @Transactional
    public String emitir(Long idUsuario) {
        LocalDateTime ahora = LocalDateTime.now();
        repository.deleteByIdUsuarioAndVenceBefore(idUsuario.intValue(), ahora);

        byte[] bytes = new byte[BYTES_TOKEN];
        azar.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        TokenRenovacionModel guardado = new TokenRenovacionModel();
        guardado.setIdUsuario(idUsuario.intValue());
        guardado.setHashToken(hash(token));
        guardado.setCreado(ahora);
        guardado.setVence(ahora.plus(vigencia));
        guardado.setUsado(false);
        repository.save(guardado);
        return token;
    }

    /**
     * Gasta el token (queda usado) y devuelve el id de su dueño, para emitirle tokens nuevos.
     * noRollbackFor: si se detecta un reuso, el borrado de los tokens del usuario tiene que quedar guardado
     * aunque después se lance el error (por defecto, una excepción deshace todo lo de la transacción).
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Long usar(String token) {
        TokenRenovacionModel guardado = repository.findByHashTokenForUpdate(hash(token))
                .orElseThrow(this::sesionVencida);
        if (guardado.isUsado()) {
            repository.deleteByIdUsuario(guardado.getIdUsuario());
            throw sesionVencida();
        }
        if (guardado.getVence().isBefore(LocalDateTime.now())) {
            throw sesionVencida();
        }
        guardado.setUsado(true);
        repository.save(guardado);
        return (long) guardado.getIdUsuario();
    }

    // Logout: el token deja de servir (si no existe, no pasa nada)
    @Transactional
    public void revocar(String token) {
        repository.deleteByHashToken(hash(token));
    }

    // SHA-256 en hexadecimal (64 caracteres): es lo que se guarda y lo que se busca
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no tiene SHA-256", e);
        }
    }

    private ResponseStatusException sesionVencida() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, SESION_VENCIDA);
    }
}

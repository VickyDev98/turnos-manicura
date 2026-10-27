package com.victoria.turnos_manicura.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Emite y lee los tokens JWT de la sesión. El token es un texto firmado (HS256) que lleva adentro
 * el id del usuario, su rol y el vencimiento: el servidor no guarda nada, solo verifica la firma.
 */
@Service
public class JwtService {

    public static final String CLAIM_ROL = "rol";

    // HS256 exige una clave de al menos 256 bits (32 bytes)
    private static final int BYTES_MINIMOS_CLAVE = 32;

    private final SecretKey clave;
    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final Duration vigencia;

    public JwtService(@Value("${app.jwt.secret}") String secreto,
                      @Value("${app.jwt.minutos-vigencia:480}") long minutosVigencia) {
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < BYTES_MINIMOS_CLAVE) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos " + BYTES_MINIMOS_CLAVE + " caracteres");
        }
        this.clave = new SecretKeySpec(bytes, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(clave));
        this.decoder = crearDecoder();
        this.vigencia = Duration.ofMinutes(minutosVigencia);
    }

    public String emitir(Long idUsuario, String rol) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(idUsuario))
                .claim(CLAIM_ROL, rol)
                .issuedAt(ahora)
                .expiresAt(ahora.plus(vigencia))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    // Lanza JwtException si el token está mal formado, la firma no coincide o ya venció
    public Jwt leer(String token) {
        return decoder.decode(token);
    }

    // Decoder nuevo con la misma clave: Spring Security lo usa para validar cada pedido (ver SecurityConfig)
    public NimbusJwtDecoder crearDecoder() {
        return NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
    }
}

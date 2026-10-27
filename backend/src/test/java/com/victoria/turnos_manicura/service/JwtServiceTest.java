package com.victoria.turnos_manicura.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String CLAVE = "clave-de-prueba-solo-para-tests-0123456789";

    private final JwtService service = new JwtService(CLAVE, 480);

    @Test
    void emitirYLeer_devuelveElUsuarioElRolYUnVencimientoFuturo() {
        Jwt jwt = service.leer(service.emitir(7L, "ADMIN"));

        assertEquals("7", jwt.getSubject());
        assertEquals("ADMIN", jwt.getClaimAsString(JwtService.CLAIM_ROL));
        assertNotNull(jwt.getId());
        assertTrue(jwt.getExpiresAt().isAfter(Instant.now().plus(Duration.ofMinutes(470))));
    }

    @Test
    void emitir_dosVecesParaElMismoUsuario_daTokensConIdDistinto() {
        String primero = service.leer(service.emitir(1L, "CLIENTE")).getId();
        String segundo = service.leer(service.emitir(1L, "CLIENTE")).getId();

        assertNotEquals(primero, segundo);
    }

    @Test
    void leer_tokenFirmadoConOtraClave_lanzaJwtException() {
        String ajeno = new JwtService("otra-clave-distinta-de-32-caracteres-xx", 480).emitir(1L, "ADMIN");

        assertThrows(JwtException.class, () -> service.leer(ajeno));
    }

    @Test
    void leer_tokenInventado_lanzaJwtException() {
        assertThrows(JwtException.class, () -> service.leer("inventado"));
        assertThrows(JwtException.class, () -> service.leer(""));
    }

    @Test
    void leer_tokenVencido_lanzaJwtException() {
        assertThrows(JwtException.class, () -> service.leer(tokenEmitidoHaceDosHorasConVigenciaDeUna()));
    }

    @Test
    void constructor_conClaveCorta_noArranca() {
        assertThrows(IllegalStateException.class, () -> new JwtService("corta", 480));
    }

    // JwtService siempre emite tokens vigentes, así que el vencido se arma a mano con la misma clave
    private String tokenEmitidoHaceDosHorasConVigenciaDeUna() {
        Instant hace2Horas = Instant.now().minus(Duration.ofHours(2));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id("token-vencido")
                .subject("1")
                .claim(JwtService.CLAIM_ROL, "MANICURISTA")
                .issuedAt(hace2Horas)
                .expiresAt(hace2Horas.plus(Duration.ofHours(1)))
                .build();
        SecretKey clave = new SecretKeySpec(CLAVE.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}

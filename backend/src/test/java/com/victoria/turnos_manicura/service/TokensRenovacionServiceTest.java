package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.model.TokenRenovacionModel;
import com.victoria.turnos_manicura.repository.TokensRenovacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokensRenovacionServiceTest {

    @Mock TokensRenovacionRepository repository;
    private TokensRenovacionService service;

    @BeforeEach
    void preparar() {
        service = new TokensRenovacionService(repository, 8);
    }

    // ---------- emitir ----------

    @Test
    void emitir_guardaSoloElHashConVencimientoEnOchoHoras() {
        String token = service.emitir(7L);

        ArgumentCaptor<TokenRenovacionModel> captor = ArgumentCaptor.forClass(TokenRenovacionModel.class);
        verify(repository).save(captor.capture());
        TokenRenovacionModel guardado = captor.getValue();

        assertEquals(7, guardado.getIdUsuario());
        assertNotEquals(token, guardado.getHashToken());
        assertEquals(TokensRenovacionService.hash(token), guardado.getHashToken());
        assertEquals(64, guardado.getHashToken().length());
        assertFalse(guardado.isUsado());
        assertEquals(8, java.time.Duration.between(guardado.getCreado(), guardado.getVence()).toHours());
    }

    @Test
    void emitir_cadaVezDaUnTokenDistintoYBorraLosVencidosDelUsuario() {
        String uno = service.emitir(7L);
        String otro = service.emitir(7L);

        assertNotEquals(uno, otro);
        verify(repository, org.mockito.Mockito.times(2)).deleteByIdUsuarioAndVenceBefore(org.mockito.ArgumentMatchers.eq(7), any());
    }

    // ---------- usar ----------

    @Test
    void usar_tokenVigente_loMarcaUsadoYDevuelveElUsuario() {
        TokenRenovacionModel guardado = guardado(false, LocalDateTime.now().plusHours(1));
        when(repository.findByHashTokenForUpdate(TokensRenovacionService.hash("abc"))).thenReturn(Optional.of(guardado));

        assertEquals(7L, service.usar("abc"));
        assertTrue(guardado.isUsado());
        verify(repository).save(guardado);
    }

    @Test
    void usar_tokenInexistente_lanzaUnauthorized() {
        when(repository.findByHashTokenForUpdate(any())).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.usar("inventado"));

        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
    }

    @Test
    void usar_tokenVencido_lanzaUnauthorizedSinGastarlo() {
        TokenRenovacionModel guardado = guardado(false, LocalDateTime.now().minusMinutes(1));
        when(repository.findByHashTokenForUpdate(any())).thenReturn(Optional.of(guardado));

        assertThrows(ResponseStatusException.class, () -> service.usar("abc"));
        assertFalse(guardado.isUsado());
        verify(repository, never()).save(any());
    }

    @Test
    void usar_tokenYaUsado_borraTodasLasSesionesDelUsuario() {
        when(repository.findByHashTokenForUpdate(any()))
                .thenReturn(Optional.of(guardado(true, LocalDateTime.now().plusHours(1))));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.usar("copiado"));

        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
        verify(repository).deleteByIdUsuario(7);
        verify(repository, never()).save(any());
    }

    // ---------- revocar ----------

    @Test
    void revocar_borraPorElHash() {
        service.revocar("abc");

        verify(repository).deleteByHashToken(TokensRenovacionService.hash("abc"));
        verify(repository, never()).deleteByIdUsuario(anyInt());
    }

    private TokenRenovacionModel guardado(boolean usado, LocalDateTime vence) {
        TokenRenovacionModel t = new TokenRenovacionModel();
        t.setIdUsuario(7);
        t.setHashToken("hash");
        t.setCreado(LocalDateTime.now().minusHours(1));
        t.setVence(vence);
        t.setUsado(usado);
        return t;
    }
}

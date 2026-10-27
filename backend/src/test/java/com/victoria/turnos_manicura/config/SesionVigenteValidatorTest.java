package com.victoria.turnos_manicura.config;

import com.victoria.turnos_manicura.model.UsuariosModel;
import com.victoria.turnos_manicura.repository.UsuariosRepository;
import com.victoria.turnos_manicura.service.TokensCerradosService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SesionVigenteValidatorTest {

    @Mock UsuariosRepository usuariosRepository;
    @Mock TokensCerradosService tokensCerrados;
    @InjectMocks SesionVigenteValidator validator;

    private UsuariosModel ana;
    private Jwt jwt;

    @BeforeEach
    void preparar() {
        ana = new UsuariosModel();
        ana.setId(1L);
        ana.setActivo(true);
        when(usuariosRepository.findById(1L)).thenReturn(Optional.of(ana));

        jwt = Jwt.withTokenValue("token").header("alg", "HS256").jti("id-del-token").subject("1").build();
    }

    @Test
    void usuarioActivoConTokenAbierto_esValido() {
        assertFalse(validator.validate(jwt).hasErrors());
    }

    @Test
    void usuarioDadoDeBajaConTokenVigente_esInvalido() {
        ana.setActivo(false);

        assertTrue(validator.validate(jwt).hasErrors());
    }

    @Test
    void tokenDeUnUsuarioQueYaNoExiste_esInvalido() {
        when(usuariosRepository.findById(1L)).thenReturn(Optional.empty());

        assertTrue(validator.validate(jwt).hasErrors());
    }

    @Test
    void tokenCerradoConLogout_esInvalido() {
        when(tokensCerrados.estaCerrado("id-del-token")).thenReturn(true);

        assertTrue(validator.validate(jwt).hasErrors());
    }
}

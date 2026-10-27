package com.victoria.turnos_manicura.config;

import com.victoria.turnos_manicura.model.UsuariosModel;
import com.victoria.turnos_manicura.repository.UsuariosRepository;
import com.victoria.turnos_manicura.service.TokensCerradosService;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Se suma a las validaciones normales del token (firma y vencimiento): rechaza el token si se cerró con
 * logout o si su usuario se dio de baja (o ya no existe). Así una baja corta el acceso al instante,
 * aunque el token todavía no haya vencido.
 */
@Component
public class SesionVigenteValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error SESION_CERRADA =
            new OAuth2Error("invalid_token", "Sesión cerrada o usuario dado de baja", null);

    private final UsuariosRepository usuariosRepository;
    private final TokensCerradosService tokensCerrados;

    public SesionVigenteValidator(UsuariosRepository usuariosRepository, TokensCerradosService tokensCerrados) {
        this.usuariosRepository = usuariosRepository;
        this.tokensCerrados = tokensCerrados;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        boolean activo = usuariosRepository.findById(Long.valueOf(jwt.getSubject()))
                .map(UsuariosModel::isActivo)
                .orElse(false);
        if (!activo || tokensCerrados.estaCerrado(jwt.getId())) {
            return OAuth2TokenValidatorResult.failure(SESION_CERRADA);
        }
        return OAuth2TokenValidatorResult.success();
    }
}

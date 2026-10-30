package com.victoria.turnos_manicura.config;

import com.victoria.turnos_manicura.service.AuthService;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

// Rellena los parámetros AuthService.Sesion de los controllers: quién hace el pedido, leído del token.
// Spring Security ya validó el token antes de llegar acá (firma, vencimiento, usuario activo, no cerrado)
@Component
public class SesionArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parametro) {
        return AuthService.Sesion.class.equals(parametro.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer contenedor,
                                  NativeWebRequest pedido, WebDataBinderFactory fabrica) {
        if (!(pedido.getUserPrincipal() instanceof JwtAuthenticationToken tokenAutenticado)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Iniciá sesión para continuar");
        }
        return AuthService.Sesion.desde(tokenAutenticado.getToken());
    }
}

package com.victoria.turnos_manicura.config;

import com.victoria.turnos_manicura.service.AuthService;
import com.victoria.turnos_manicura.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SesionArgumentResolverTest {

    private final SesionArgumentResolver resolver = new SesionArgumentResolver();

    // Métodos de ejemplo: uno pide la sesión y el otro no
    @SuppressWarnings("unused")
    private void conSesion(AuthService.Sesion sesion) { }

    @SuppressWarnings("unused")
    private void sinSesion(String otro) { }

    private MethodParameter parametro(String metodo, Class<?> tipo) throws NoSuchMethodException {
        return new MethodParameter(getClass().getDeclaredMethod(metodo, tipo), 0);
    }

    @Test
    void soloResuelveLosParametrosDeTipoSesion() throws Exception {
        assertTrue(resolver.supportsParameter(parametro("conSesion", AuthService.Sesion.class)));
        assertFalse(resolver.supportsParameter(parametro("sinSesion", String.class)));
    }

    @Test
    void conUnTokenAutenticado_devuelveLaSesionConIdYRol() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256")
                .subject("7").claim(JwtService.CLAIM_ROL, "MANICURISTA").build();
        MockHttpServletRequest pedido = new MockHttpServletRequest();
        pedido.setUserPrincipal(new JwtAuthenticationToken(jwt));

        AuthService.Sesion sesion = (AuthService.Sesion) resolver.resolveArgument(
                parametro("conSesion", AuthService.Sesion.class), null, new ServletWebRequest(pedido), null);

        assertEquals(7L, sesion.getIdUsuario());
        assertEquals("MANICURISTA", sesion.getRol());
    }

    @Test
    void sinAutenticacion_lanzaUnauthorized() {
        ServletWebRequest pedido = new ServletWebRequest(new MockHttpServletRequest());

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () ->
                resolver.resolveArgument(parametro("conSesion", AuthService.Sesion.class), null, pedido, null));

        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
    }
}

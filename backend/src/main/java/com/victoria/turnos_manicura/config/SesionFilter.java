package com.victoria.turnos_manicura.config;

import com.victoria.turnos_manicura.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Exige sesión válida en TODA la API salvo las rutas públicas de abajo.
 * Así un endpoint nuevo queda protegido por defecto; los controllers siguen
 * validando el rol (requerirAdmin, requerirManicurista, etc.).
 */
@Component
public class SesionFilter extends OncePerRequestFilter {

    private static final Set<String> RUTAS_PUBLICAS = Set.of("/auth/login", "/auth/registro", "/auth/logout");

    private final AuthService authService;

    public SesionFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Los preflight de CORS no llevan el header Authorization
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || RUTAS_PUBLICAS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            authService.requerirSesion(request.getHeader(HttpHeaders.AUTHORIZATION));
        } catch (ResponseStatusException e) {
            // Mismo formato que ErrorHandler para que el frontend muestre el mensaje
            response.setStatus(e.getStatusCode().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"status\":" + e.getStatusCode().value()
                    + ",\"message\":\"" + e.getReason() + "\",\"errores\":{}}");
            return;
        }
        chain.doFilter(request, response);
    }
}

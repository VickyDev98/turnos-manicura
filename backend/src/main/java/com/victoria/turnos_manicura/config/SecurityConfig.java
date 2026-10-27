package com.victoria.turnos_manicura.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.victoria.turnos_manicura.service.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

@Configuration
public class SecurityConfig {

    private static final Set<String> RUTAS_PUBLICAS = Set.of("/auth/login", "/auth/registro", "/auth/logout");

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Valida el token de cada pedido: firma y vencimiento (las de siempre) + que la sesión siga vigente
    @Bean
    public JwtDecoder jwtDecoder(JwtService jwtService, SesionVigenteValidator sesionVigente) {
        NimbusJwtDecoder decoder = jwtService.crearDecoder();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), sesionVigente));
        return decoder;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationEntryPoint sinSesion,
                                           BearerTokenResolver tokenResolver) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                // Sin cookies ni sesión de servidor: cada pedido trae su token
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Default-deny: una ruta nueva queda protegida sola. Los controllers siguen validando el rol
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(RUTAS_PUBLICAS.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(Customizer.withDefaults())
                        .bearerTokenResolver(tokenResolver)
                        .authenticationEntryPoint(sinSesion)
                );

        return http.build();
    }

    // En las rutas públicas se ignora el token: un token viejo en el navegador no debe impedir iniciar sesión
    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver porDefecto = new DefaultBearerTokenResolver();
        return request -> RUTAS_PUBLICAS.contains(request.getRequestURI()) ? null : porDefecto.resolve(request);
    }

    // Respuesta 401 con el mismo formato que ErrorHandler, para que el frontend muestre el mensaje
    @Bean
    public AuthenticationEntryPoint sinSesion(ObjectMapper objectMapper) {
        return (request, response, excepcion) -> {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(),
                    Map.of("status", 401, "message", "Iniciá sesión para continuar", "errores", Map.of()));
        };
    }
}

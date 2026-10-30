package com.victoria.turnos_manicura.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SesionArgumentResolver sesionResolver;

    public WebConfig(SesionArgumentResolver sesionResolver) {
        this.sesionResolver = sesionResolver;
    }

    // Así los controllers pueden declarar un parámetro AuthService.Sesion y Spring lo completa desde el token
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(sesionResolver);
    }
}

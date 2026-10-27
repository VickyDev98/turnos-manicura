package com.victoria.turnos_manicura.integration;

import com.victoria.turnos_manicura.model.UsuariosModel;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Cada test usa su propio email: el contador de intentos fallidos vive en el AuthService,
// que se comparte entre tests (el rollback no lo deshace)
class AuthIntegrationTest extends IntegrationTestBase {

    private String json(Map<String, ?> datos) throws Exception {
        return objectMapper.writeValueAsString(datos);
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String clave) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", clave))));
    }

    @Test
    void loginCorrectoDevuelveTokenYRol() throws Exception {
        crearUsuario("CLIENTE", "login.ok@test.com");

        login("login.ok@test.com", CLAVE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.rol").value("CLIENTE"))
                .andExpect(jsonPath("$.email").value("login.ok@test.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void loginConClaveIncorrectaDa401() throws Exception {
        crearUsuario("CLIENTE", "clave.mala@test.com");

        login("clave.mala@test.com", "otra-clave").andExpect(status().isUnauthorized());
    }

    @Test
    void loginDeUsuarioDadoDeBajaDa401() throws Exception {
        UsuariosModel usuario = crearUsuario("CLIENTE", "de.baja@test.com");
        usuario.setActivo(false);
        usuariosRepository.save(usuario);

        login("de.baja@test.com", CLAVE).andExpect(status().isUnauthorized());
    }

    @Test
    void rutaProtegidaSinTokenDa401ConMensaje() throws Exception {
        mockMvc.perform(get("/usuarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Iniciá sesión para continuar"));
    }

    @Test
    void rutaProtegidaConTokenInvalidoDa401() throws Exception {
        mockMvc.perform(get("/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer token-inventado"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rutaProtegidaConTokenValidoDa200() throws Exception {
        crearUsuario("CLIENTE", "con.token@test.com");
        String auth = iniciarSesion("con.token@test.com");

        mockMvc.perform(get("/usuarios").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());
    }

    @Test
    void logoutCierraLaSesion() throws Exception {
        crearUsuario("CLIENTE", "logout@test.com");
        String auth = iniciarSesion("logout@test.com");

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());

        mockMvc.perform(get("/usuarios").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registroCreaElClienteYDejaLaSesionIniciada() throws Exception {
        tipo("CLIENTE");

        mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "rol", "CLIENTE",
                                "nombre", "Laura",
                                "apellido", "Diaz",
                                "email", "registro@test.com",
                                "password", CLAVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.rol").value("CLIENTE"));

        assertTrue(usuariosRepository.existsByEmailIgnoreCase("registro@test.com"));
    }

    @Test
    void registroNoPermiteElegirAdmin() throws Exception {
        tipo("ADMIN");

        mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "rol", "ADMIN",
                                "nombre", "Laura",
                                "apellido", "Diaz",
                                "email", "falso.admin@test.com",
                                "password", CLAVE))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.rol").value("Elegí si sos cliente o manicurista"));
    }

    @Test
    void cincoFallosBloqueanElLoginAunqueLaClaveSeaCorrecta() throws Exception {
        crearUsuario("CLIENTE", "bloqueo@test.com");

        for (int i = 0; i < 5; i++) {
            login("bloqueo@test.com", "mala").andExpect(status().isUnauthorized());
        }

        login("bloqueo@test.com", CLAVE).andExpect(status().isTooManyRequests());
    }

    @Test
    void clienteNoPuedeUsarUnaRutaDeAdmin() throws Exception {
        UsuariosModel cliente = crearUsuario("CLIENTE", "cliente.403@test.com");
        String auth = iniciarSesion("cliente.403@test.com");

        mockMvc.perform(put("/usuarios/" + cliente.getId() + "/activo")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("activo", false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSiPuedeUsarLaRutaDeAdmin() throws Exception {
        UsuariosModel cliente = crearUsuario("CLIENTE", "cliente.baja@test.com");
        crearUsuario("ADMIN", "admin.ok@test.com");
        String auth = iniciarSesion("admin.ok@test.com");

        mockMvc.perform(put("/usuarios/" + cliente.getId() + "/activo")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("activo", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void unTokenViejoEnElHeaderNoImpideIniciarSesion() throws Exception {
        crearUsuario("CLIENTE", "token.viejo@test.com");

        mockMvc.perform(post("/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-que-ya-no-vale")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "token.viejo@test.com", "password", CLAVE))))
                .andExpect(status().isOk());
    }

    @Test
    void elPreflightDeCorsNoPideSesion() throws Exception {
        mockMvc.perform(options("/usuarios")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioDadoDeBajaPierdeElAccesoAunqueSuTokenSigaVigente() throws Exception {
        UsuariosModel cliente = crearUsuario("CLIENTE", "cliente.vigente@test.com");
        crearUsuario("ADMIN", "admin.baja@test.com");
        String authCliente = iniciarSesion("cliente.vigente@test.com");
        String authAdmin = iniciarSesion("admin.baja@test.com");

        mockMvc.perform(get("/usuarios").header(HttpHeaders.AUTHORIZATION, authCliente))
                .andExpect(status().isOk());

        mockMvc.perform(put("/usuarios/" + cliente.getId() + "/activo")
                        .header(HttpHeaders.AUTHORIZATION, authAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("activo", false))))
                .andExpect(status().isOk());

        // El token todavía no venció, pero el usuario ya no está activo
        mockMvc.perform(get("/usuarios").header(HttpHeaders.AUTHORIZATION, authCliente))
                .andExpect(status().isUnauthorized());
    }
}

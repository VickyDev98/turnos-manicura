package com.victoria.turnos_manicura.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.victoria.turnos_manicura.model.TiposUsuarioModel;
import com.victoria.turnos_manicura.model.UsuariosModel;
import com.victoria.turnos_manicura.repository.TiposUsuarioRepository;
import com.victoria.turnos_manicura.repository.UsuariosRepository;
import com.victoria.turnos_manicura.service.NotificacionesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de los tests de integración: levanta la app completa (filtro, controllers, services,
 * repositorios) contra H2 en memoria y manda pedidos HTTP simulados con MockMvc.
 *
 * Cada test corre dentro de una transacción que se deshace al terminar (@Transactional),
 * así que los datos que crea no pasan al test siguiente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestBase {

    protected static final String CLAVE = "123456";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected UsuariosRepository usuariosRepository;
    @Autowired
    protected TiposUsuarioRepository tiposRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    // Los mails son un borde externo: en los tests no se manda ninguno
    @MockBean
    protected NotificacionesService notificacionesService;

    // Devuelve el tipo de usuario (ADMIN, MANICURISTA, CLIENTE); si no existe, lo crea
    protected TiposUsuarioModel tipo(String rol) {
        return tiposRepository.findByDescripcionIgnoreCase(rol).orElseGet(() -> {
            TiposUsuarioModel nuevo = new TiposUsuarioModel();
            nuevo.setDescripcion(rol);
            return tiposRepository.save(nuevo);
        });
    }

    // Crea un usuario activo con la clave CLAVE ya encriptada, como queda en la base real
    protected UsuariosModel crearUsuario(String rol, String email) {
        UsuariosModel usuario = new UsuariosModel();
        usuario.setTipo(tipo(rol).getId().intValue());
        usuario.setNombre("Test");
        usuario.setApellido(rol);
        usuario.setEmail(email);
        usuario.setPassword(passwordEncoder.encode(CLAVE));
        usuario.setActivo(true);
        return usuariosRepository.save(usuario);
    }

    // Inicia sesión por /auth/login (pasa por el flujo real) y devuelve el valor para el header Authorization
    protected String iniciarSesion(String email) throws Exception {
        String respuesta = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", CLAVE))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(respuesta).get("token").asText();
    }
}

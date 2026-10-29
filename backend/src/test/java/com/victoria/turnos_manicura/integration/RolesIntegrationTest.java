package com.victoria.turnos_manicura.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los @PreAuthorize de los controllers: cada endpoint protegido se prueba con los tres roles.
 * El rol permitido no tiene que recibir 403 (la respuesta puede ser 200, 400 o 404 según los datos:
 * lo que importa acá es que pasó el control de rol); los demás tienen que recibir 403 en JSON.
 *
 * Los cuerpos son válidos a propósito: Spring valida el cuerpo (@Valid) antes de entrar al método,
 * así que con un cuerpo inválido la respuesta sería 400 y no se llegaría a ver el 403.
 */
class RolesIntegrationTest extends IntegrationTestBase {

    private static final String ADMIN = "ADMIN";
    private static final String MANICURISTA = "MANICURISTA";
    private static final String CLIENTE = "CLIENTE";
    private static final List<String> ROLES = List.of(ADMIN, MANICURISTA, CLIENTE);

    // Un endpoint, su cuerpo (null = sin cuerpo) y los roles que lo pueden usar
    private record Endpoint(HttpMethod metodo, String url, String cuerpo, Set<String> permitidos) {
        @Override
        public String toString() {
            return metodo + " " + url + " (permitido: " + permitidos + ")";
        }
    }

    private static Endpoint solo(String rol, HttpMethod metodo, String url, String cuerpo) {
        return new Endpoint(metodo, url, cuerpo, Set.of(rol));
    }

    private static final String USUARIO = "{\"tipo\":1,\"nombre\":\"Ana\",\"apellido\":\"Gomez\","
            + "\"email\":\"roles@test.com\",\"password\":\"123456\"}";
    private static final String USUARIO_EDITADO = "{\"nombre\":\"Ana\",\"apellido\":\"Gomez\",\"email\":\"roles@test.com\"}";
    private static final String SERVICIO = "{\"nombre\":\"Servicio de prueba\",\"duracion\":\"00:40:00\",\"precio\":5000}";
    private static final String BLOQUEO = "{\"fecha\":\"2030-01-01\",\"horaDesde\":\"10:00\",\"horaHasta\":\"11:00\","
            + "\"repeticion\":\"UNICA\"}";
    private static final String FRANJAS = "[{\"diaSemana\":1,\"horaDesde\":\"09:00\",\"horaHasta\":\"18:00\"}]";

    static Stream<Endpoint> endpointsProtegidos() {
        return Stream.of(
                // Solo el admin
                solo(ADMIN, HttpMethod.POST, "/usuarios", USUARIO),
                solo(ADMIN, HttpMethod.PUT, "/usuarios/999999", USUARIO_EDITADO),
                solo(ADMIN, HttpMethod.PUT, "/usuarios/999999/activo", "{\"activo\":true}"),
                solo(ADMIN, HttpMethod.PUT, "/servicios/999999", SERVICIO),
                solo(ADMIN, HttpMethod.POST, "/tiposUsuario", "{\"descripcion\":\"OTRO\"}"),
                // Manicuristas y admin
                new Endpoint(HttpMethod.POST, "/servicios", SERVICIO, Set.of(ADMIN, MANICURISTA)),
                new Endpoint(HttpMethod.PUT, "/turnos/999999/asistencia", "{\"noAsistio\":true}", Set.of(ADMIN, MANICURISTA)),
                // Solo manicuristas
                solo(MANICURISTA, HttpMethod.POST, "/servicios/ofertas", "{\"idServicio\":999999}"),
                solo(MANICURISTA, HttpMethod.PUT, "/servicios/ofertas/999999", "{\"precio\":6000}"),
                solo(MANICURISTA, HttpMethod.GET, "/turnos/agenda?fecha=2030-01-01", null),
                solo(MANICURISTA, HttpMethod.GET, "/disponibilidad/franjas", null),
                solo(MANICURISTA, HttpMethod.PUT, "/disponibilidad/franjas", FRANJAS),
                solo(MANICURISTA, HttpMethod.GET, "/disponibilidad/bloqueos", null),
                solo(MANICURISTA, HttpMethod.POST, "/disponibilidad/bloqueos", BLOQUEO),
                solo(MANICURISTA, HttpMethod.PUT, "/disponibilidad/bloqueos/999999", BLOQUEO),
                solo(MANICURISTA, HttpMethod.DELETE, "/disponibilidad/bloqueos/999999", null)
        );
    }

    @BeforeEach
    void crearUsuarios() {
        crearUsuario(ADMIN, "admin@roles.com");
        crearUsuario(MANICURISTA, "mani@roles.com");
        crearUsuario(CLIENTE, "cli@roles.com");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpointsProtegidos")
    void cadaRolSoloPasaDondeCorresponde(Endpoint endpoint) throws Exception {
        for (String rol : ROLES) {
            ResultActions resultado = pedir(endpoint, rol);
            if (endpoint.permitidos().contains(rol)) {
                int estado = resultado.andReturn().getResponse().getStatus();
                assertNotEquals(403, estado, rol + " tendría que poder usar " + endpoint);
            } else {
                resultado.andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.message").value("No tenés permiso para esta opción"));
            }
        }
    }

    private ResultActions pedir(Endpoint endpoint, String rol) throws Exception {
        var pedido = request(endpoint.metodo(), endpoint.url())
                .header(HttpHeaders.AUTHORIZATION, iniciarSesion(emailDe(rol)));
        if (endpoint.cuerpo() != null) {
            pedido.contentType(MediaType.APPLICATION_JSON).content(endpoint.cuerpo());
        }
        return mockMvc.perform(pedido);
    }

    private static String emailDe(String rol) {
        return switch (rol) {
            case ADMIN -> "admin@roles.com";
            case MANICURISTA -> "mani@roles.com";
            default -> "cli@roles.com";
        };
    }
}

package com.victoria.turnos_manicura.integration;

import com.victoria.turnos_manicura.model.TurnosModel;
import com.victoria.turnos_manicura.model.UsuariosModel;
import com.victoria.turnos_manicura.repository.TurnosRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TurnosIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TurnosRepository turnosRepository;

    private UsuariosModel manicurista1;
    private UsuariosModel manicurista2;
    private UsuariosModel cliente1;
    private UsuariosModel cliente2;

    @BeforeEach
    void crearUsuarios() {
        manicurista1 = crearUsuario("MANICURISTA", "mani1@turnos.com");
        manicurista2 = crearUsuario("MANICURISTA", "mani2@turnos.com");
        cliente1 = crearUsuario("CLIENTE", "cli1@turnos.com");
        cliente2 = crearUsuario("CLIENTE", "cli2@turnos.com");
        crearUsuario("ADMIN", "admin@turnos.com");
    }

    // Guarda el turno directo en la base, sin pasar por las reglas de reserva (así se pueden crear turnos pasados)
    private TurnosModel crearTurno(UsuariosModel manicurista, UsuariosModel cliente, LocalDateTime horario, boolean activo) {
        TurnosModel turno = new TurnosModel();
        turno.setIdManicurista(manicurista.getId().intValue());
        turno.setIdCliente(cliente.getId().intValue());
        turno.setIdServicio(1);
        turno.setDiaHorario(horario);
        turno.setActivo(activo);
        return turnosRepository.save(turno);
    }

    private ResultActions pagina(String email, String parametros) throws Exception {
        return mockMvc.perform(get("/turnos/pagina" + parametros)
                .header(HttpHeaders.AUTHORIZATION, iniciarSesion(email)));
    }

    // jsonPath devuelve Integer para los ids chicos: se comparan como int
    private int id(TurnosModel turno) {
        return turno.getId().intValue();
    }

    // ---------- Listado por rol ----------

    @Test
    void sinTokenDa401() throws Exception {
        mockMvc.perform(get("/turnos/pagina")).andExpect(status().isUnauthorized());
    }

    @Test
    void clienteVeSoloSusTurnos() throws Exception {
        TurnosModel propio = crearTurno(manicurista1, cliente1, LocalDateTime.now().plusDays(2), true);
        crearTurno(manicurista1, cliente2, LocalDateTime.now().plusDays(3), true);

        pagina("cli1@turnos.com", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id(propio)));
    }

    @Test
    void manicuristaVeSoloSusTurnosAunqueMandeOtroIdManicurista() throws Exception {
        TurnosModel propio = crearTurno(manicurista1, cliente1, LocalDateTime.now().plusDays(2), true);
        crearTurno(manicurista2, cliente1, LocalDateTime.now().plusDays(3), true);

        pagina("mani1@turnos.com", "?idManicurista=" + manicurista2.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(id(propio))));
    }

    @Test
    void adminVeTodosYPuedeFiltrarPorManicurista() throws Exception {
        TurnosModel deMani1 = crearTurno(manicurista1, cliente1, LocalDateTime.now().plusDays(2), true);
        TurnosModel deMani2 = crearTurno(manicurista2, cliente2, LocalDateTime.now().plusDays(3), true);

        pagina("admin@turnos.com", "")
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(id(deMani1), id(deMani2))));

        pagina("admin@turnos.com", "?idManicurista=" + manicurista2.getId())
                .andExpect(jsonPath("$.content[*].id", contains(id(deMani2))));
    }

    // ---------- Filtro por momento y orden ----------

    @Test
    void proximosSoloActivosFuturosDelMasCercanoAlMasLejano() throws Exception {
        LocalDateTime ahora = LocalDateTime.now();
        TurnosModel lejano = crearTurno(manicurista1, cliente1, ahora.plusDays(5), true);
        TurnosModel cercano = crearTurno(manicurista1, cliente1, ahora.plusDays(1), true);
        crearTurno(manicurista1, cliente1, ahora.minusDays(1), true);       // ya pasó
        crearTurno(manicurista1, cliente1, ahora.plusDays(2), false);       // cancelado

        pagina("cli1@turnos.com", "?momento=PROXIMOS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(id(cercano), id(lejano))))
                .andExpect(jsonPath("$.content[0].estado").value("PROXIMO"));
    }

    @Test
    void historialTraePasadosYCanceladosDelMasRecienteAlMasAntiguo() throws Exception {
        LocalDateTime ahora = LocalDateTime.now();
        crearTurno(manicurista1, cliente1, ahora.plusDays(3), true);        // próximo: no va
        TurnosModel pasadoViejo = crearTurno(manicurista1, cliente1, ahora.minusDays(10), true);
        TurnosModel cancelado = crearTurno(manicurista1, cliente1, ahora.plusDays(1), false);
        TurnosModel pasadoReciente = crearTurno(manicurista1, cliente1, ahora.minusDays(1), true);

        pagina("cli1@turnos.com", "?momento=HISTORIAL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id",
                        contains(id(cancelado), id(pasadoReciente), id(pasadoViejo))))
                .andExpect(jsonPath("$.content[0].estado").value("CANCELADO"))
                .andExpect(jsonPath("$.content[1].estado").value("REALIZADO"));
    }

    @Test
    void todosTraeTodoDelMasRecienteAlMasAntiguo() throws Exception {
        LocalDateTime ahora = LocalDateTime.now();
        TurnosModel pasado = crearTurno(manicurista1, cliente1, ahora.minusDays(2), true);
        TurnosModel futuro = crearTurno(manicurista1, cliente1, ahora.plusDays(2), true);
        TurnosModel cancelado = crearTurno(manicurista1, cliente1, ahora.plusDays(1), false);

        pagina("cli1@turnos.com", "?momento=TODOS")
                .andExpect(jsonPath("$.content[*].id", contains(id(futuro), id(cancelado), id(pasado))));
    }

    @Test
    void momentoInvalidoDa400() throws Exception {
        pagina("cli1@turnos.com", "?momento=XYZ").andExpect(status().isBadRequest());
    }

    // ---------- Paginado ----------

    @Test
    void paginaDeAUnoCortaLosResultadosYCuentaElTotal() throws Exception {
        LocalDateTime ahora = LocalDateTime.now();
        TurnosModel t3 = crearTurno(manicurista1, cliente1, ahora.plusDays(3), true);
        TurnosModel t2 = crearTurno(manicurista1, cliente1, ahora.plusDays(2), true);
        TurnosModel t1 = crearTurno(manicurista1, cliente1, ahora.plusDays(1), true);

        pagina("cli1@turnos.com", "?size=2&page=0")
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].id", contains(id(t3), id(t2))))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        pagina("cli1@turnos.com", "?size=2&page=1")
                .andExpect(jsonPath("$.content[*].id", contains(id(t1))));
    }

    // ---------- Cancelar ----------

    private ResultActions cancelar(String email, TurnosModel turno) throws Exception {
        return mockMvc.perform(put("/turnos/" + turno.getId() + "/cancelar")
                .header(HttpHeaders.AUTHORIZATION, iniciarSesion(email)));
    }

    @Test
    void clienteNoPuedeCancelarConMenosDe24Horas() throws Exception {
        TurnosModel turno = crearTurno(manicurista1, cliente1, LocalDateTime.now().plusHours(2), true);

        cancelar("cli1@turnos.com", turno).andExpect(status().isBadRequest());

        // Sigue activo y no se avisó a nadie
        assertTrue(turnosRepository.findById(turno.getId()).orElseThrow().isActivo());
        verifyNoInteractions(notificacionesService);
    }

    @Test
    void clienteCancelaConAnticipacionYSeAvisa() throws Exception {
        TurnosModel turno = crearTurno(manicurista1, cliente1, LocalDateTime.now().plusDays(3), true);

        cancelar("cli1@turnos.com", turno)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));

        verify(notificacionesService).turnoCancelado(any(TurnosModel.class), eq(cliente1.getId()));
    }

    @Test
    void manicuristaPuedeCancelarSobreLaHora() throws Exception {
        TurnosModel turno = crearTurno(manicurista1, cliente1, LocalDateTime.now().plusHours(2), true);

        cancelar("mani1@turnos.com", turno)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));
    }

    @Test
    void nadiePuedeCancelarElTurnoDeOtraPersona() throws Exception {
        TurnosModel turno = crearTurno(manicurista1, cliente1, LocalDateTime.now().plusDays(3), true);

        cancelar("cli2@turnos.com", turno).andExpect(status().isForbidden());
        cancelar("mani2@turnos.com", turno).andExpect(status().isForbidden());
    }

    // ---------- Asistencia ----------

    private ResultActions marcarAsistencia(String email, TurnosModel turno, String body) throws Exception {
        return mockMvc.perform(put("/turnos/" + turno.getId() + "/asistencia")
                .header(HttpHeaders.AUTHORIZATION, iniciarSesion(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void manicuristaMarcaNoAsistioYElTurnoQuedaEnElHistorial() throws Exception {
        TurnosModel turno = crearTurno(manicurista1, cliente1, LocalDateTime.now().minusHours(3), true);

        marcarAsistencia("mani1@turnos.com", turno, "{\"noAsistio\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("NO_ASISTIO"));

        assertTrue(turnosRepository.findById(turno.getId()).orElseThrow().isNoAsistio());
        pagina("cli1@turnos.com", "?momento=HISTORIAL")
                .andExpect(jsonPath("$.content[0].estado").value("NO_ASISTIO"));
        pagina("cli1@turnos.com", "?momento=PROXIMOS")
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void manicuristaPuedeCorregirUnNoAsistioMarcadoPorError() throws Exception {
        TurnosModel turno = crearTurno(manicurista1, cliente1, LocalDateTime.now().minusHours(3), true);
        marcarAsistencia("mani1@turnos.com", turno, "{\"noAsistio\":true}").andExpect(status().isOk());

        marcarAsistencia("mani1@turnos.com", turno, "{\"noAsistio\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REALIZADO"));
    }

    @Test
    void elClienteNoPuedeMarcarAsistenciaNiDeSuPropioTurno() throws Exception {
        TurnosModel turno = crearTurno(manicurista1, cliente1, LocalDateTime.now().minusHours(3), true);

        marcarAsistencia("cli1@turnos.com", turno, "{\"noAsistio\":true}").andExpect(status().isForbidden());
        assertFalse(turnosRepository.findById(turno.getId()).orElseThrow().isNoAsistio());
    }

    @Test
    void otraManicuristaNoPuedeMarcarAsistenciaDelTurnoAjeno() throws Exception {
        TurnosModel turno = crearTurno(manicurista1, cliente1, LocalDateTime.now().minusHours(3), true);

        marcarAsistencia("mani2@turnos.com", turno, "{\"noAsistio\":true}").andExpect(status().isForbidden());
    }

    @Test
    void noSePuedeMarcarAsistenciaDeUnTurnoQueTodaviaNoEmpezo() throws Exception {
        TurnosModel turno = crearTurno(manicurista1, cliente1, LocalDateTime.now().plusHours(3), true);

        marcarAsistencia("mani1@turnos.com", turno, "{\"noAsistio\":true}").andExpect(status().isBadRequest());
    }

    @Test
    void marcarAsistenciaSinElCampoDa400ConMensaje() throws Exception {
        TurnosModel turno = crearTurno(manicurista1, cliente1, LocalDateTime.now().minusHours(3), true);

        marcarAsistencia("mani1@turnos.com", turno, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.noAsistio").value("Indicá si el cliente asistió o no"));
    }

    @Test
    void cancelarUnTurnoQueNoExisteDa404() throws Exception {
        TurnosModel turno = crearTurno(manicurista1, cliente1, LocalDateTime.now().plusDays(3), true);
        turnosRepository.delete(turno);

        cancelar("cli1@turnos.com", turno).andExpect(status().isNotFound());
    }
}

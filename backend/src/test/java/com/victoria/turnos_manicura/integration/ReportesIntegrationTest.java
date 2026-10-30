package com.victoria.turnos_manicura.integration;

import com.victoria.turnos_manicura.model.TurnosModel;
import com.victoria.turnos_manicura.model.UsuariosModel;
import com.victoria.turnos_manicura.repository.TurnosRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportesIntegrationTest extends IntegrationTestBase {

    // 2030-01-07 es lunes
    private static final LocalDate LUNES = LocalDate.of(2030, 1, 7);

    @Autowired
    private TurnosRepository turnosRepository;

    private UsuariosModel manicurista;
    private UsuariosModel clienteHabitual;
    private UsuariosModel clienteCumplidor;

    @BeforeEach
    void crearUsuarios() {
        manicurista = crearUsuario("MANICURISTA", "mani@reportes.com");
        clienteHabitual = crearUsuario("CLIENTE", "cancela@reportes.com");
        clienteCumplidor = crearUsuario("CLIENTE", "cumple@reportes.com");
        crearUsuario("ADMIN", "admin@reportes.com");
    }

    private void crearTurno(UsuariosModel cliente, LocalDate dia, boolean activo) {
        TurnosModel turno = new TurnosModel();
        turno.setIdManicurista(manicurista.getId().intValue());
        turno.setIdCliente(cliente.getId().intValue());
        turno.setIdServicio(1);
        turno.setDiaHorario(dia.atTime(10, 0));
        turno.setActivo(activo);
        turnosRepository.save(turno);
    }

    private ResultActions reporte(String email, String parametros) throws Exception {
        return mockMvc.perform(get("/reportes/turnos" + parametros).header(HttpHeaders.AUTHORIZATION, iniciarSesion(email)));
    }

    @Test
    void sinTokenDa401() throws Exception {
        mockMvc.perform(get("/reportes/turnos")).andExpect(status().isUnauthorized());
    }

    @Test
    void cuentaAsignadosYCanceladosDelRango() throws Exception {
        crearTurno(clienteCumplidor, LUNES, true);
        crearTurno(clienteCumplidor, LUNES.plusDays(1), true);
        crearTurno(clienteCumplidor, LUNES.plusDays(2), false);
        crearTurno(clienteCumplidor, LUNES.plusDays(30), true); // fuera del rango

        reporte("admin@reportes.com", "?desde=" + LUNES + "&hasta=" + LUNES.plusDays(6))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAsignados").value(2))
                .andExpect(jsonPath("$.totalCancelados").value(1))
                .andExpect(jsonPath("$.canceladosPorDia", hasSize(7)))
                .andExpect(jsonPath("$.canceladosPorSemana", hasSize(1)));
    }

    @Test
    void soloMarcaALosClientesConMasDeTresCancelaciones() throws Exception {
        for (int i = 0; i < 4; i++) {
            crearTurno(clienteHabitual, LUNES.plusDays(i), false);
        }
        for (int i = 0; i < 3; i++) { // justo 3: no se marca
            crearTurno(clienteCumplidor, LUNES.plusDays(i), false);
        }

        reporte("admin@reportes.com", "?desde=" + LUNES + "&hasta=" + LUNES.plusDays(6))
                .andExpect(jsonPath("$.clientesConCancelaciones", hasSize(1)))
                .andExpect(jsonPath("$.clientesConCancelaciones[0].idCliente").value(clienteHabitual.getId().intValue()))
                .andExpect(jsonPath("$.clientesConCancelaciones[0].cancelaciones").value(4));
    }

    @Test
    void rangoInvertidoDa400() throws Exception {
        reporte("admin@reportes.com", "?desde=" + LUNES + "&hasta=" + LUNES.minusDays(1))
                .andExpect(status().isBadRequest());
    }
}

package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.dto.ReporteTurnos;
import com.victoria.turnos_manicura.exception.ValidacionException;
import com.victoria.turnos_manicura.model.ServiciosModel;
import com.victoria.turnos_manicura.model.TurnosModel;
import com.victoria.turnos_manicura.model.UsuariosModel;
import com.victoria.turnos_manicura.repository.ServiciosRepository;
import com.victoria.turnos_manicura.repository.TurnosRepository;
import com.victoria.turnos_manicura.repository.UsuariosRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportesServiceTest {

    // 2030-01-07 es lunes
    private static final LocalDate LUNES = LocalDate.of(2030, 1, 7);

    @Mock TurnosRepository turnosRepository;
    @Mock ServiciosRepository serviciosRepository;
    @Mock UsuariosRepository usuariosRepository;
    @InjectMocks ReportesService service;

    private static TurnosModel turno(int idServicio, LocalDate dia, boolean activo) {
        TurnosModel t = new TurnosModel();
        t.setIdServicio(idServicio);
        t.setIdCliente(1);
        t.setDiaHorario(dia.atTime(10, 0));
        t.setActivo(activo);
        return t;
    }

    private static ServiciosModel servicio(long id, String nombre) {
        ServiciosModel s = new ServiciosModel();
        s.setId(id);
        s.setNombre(nombre);
        return s;
    }

    private void turnosEnLaBase(TurnosModel... turnos) {
        when(turnosRepository.findByDiaHorarioGreaterThanEqualAndDiaHorarioLessThan(
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of(turnos));
        when(serviciosRepository.findAll()).thenReturn(List.of(servicio(1, "Semipermanente"), servicio(2, "Esculpidas")));
    }

    @Test
    void cuentaSoloLosTurnosActivosPorServicioDeMayorAMenor() {
        turnosEnLaBase(
                turno(2, LUNES, true),
                turno(1, LUNES, true), turno(1, LUNES.plusDays(1), true),
                turno(1, LUNES, false));

        ReporteTurnos r = service.turnos(LUNES, LUNES.plusDays(6));

        assertEquals(3, r.getTotalAsignados());
        assertEquals(1, r.getTotalCancelados());
        assertEquals("Semipermanente", r.getAsignadosPorServicio().get(0).getServicio());
        assertEquals(2, r.getAsignadosPorServicio().get(0).getCantidad());
        assertEquals("Esculpidas", r.getAsignadosPorServicio().get(1).getServicio());
    }

    @Test
    void cancelacionesPorDiaIncluyeLosDiasSinCancelaciones() {
        turnosEnLaBase(turno(1, LUNES.plusDays(2), false), turno(1, LUNES.plusDays(2), false));

        ReporteTurnos r = service.turnos(LUNES, LUNES.plusDays(6));

        assertEquals(7, r.getCanceladosPorDia().size());
        assertEquals(0, r.getCanceladosPorDia().get(0).getCantidad());
        assertEquals(2, r.getCanceladosPorDia().get(2).getCantidad());
    }

    @Test
    void cancelacionesPorSemanaSeAgrupanPorLunes() {
        // miércoles de la primera semana y lunes de la segunda
        turnosEnLaBase(turno(1, LUNES.plusDays(2), false), turno(1, LUNES.plusWeeks(1), false));

        ReporteTurnos r = service.turnos(LUNES.plusDays(1), LUNES.plusWeeks(1).plusDays(3));

        assertEquals(2, r.getCanceladosPorSemana().size());
        assertEquals(LUNES, r.getCanceladosPorSemana().get(0).getFecha());
        assertEquals(1, r.getCanceladosPorSemana().get(0).getCantidad());
        assertEquals(LUNES.plusWeeks(1), r.getCanceladosPorSemana().get(1).getFecha());
        assertEquals(1, r.getCanceladosPorSemana().get(1).getCantidad());
    }

    @Test
    void clientesConMuchasCancelacionesTraenNombreYCantidad() {
        turnosEnLaBase();
        var fila = mock(TurnosRepository.CancelacionesPorCliente.class);
        when(fila.getIdCliente()).thenReturn(1);
        when(fila.getCantidad()).thenReturn(5L);
        when(turnosRepository.clientesConMasCancelacionesQue(ReportesService.LIMITE_CANCELACIONES)).thenReturn(List.of(fila));
        UsuariosModel cliente = new UsuariosModel();
        cliente.setId(1L);
        cliente.setNombre("Lucía");
        cliente.setApellido("Paz");
        cliente.setEmail("lucia@mail.com");
        when(usuariosRepository.findAllById(any())).thenReturn(List.of(cliente));

        ReporteTurnos r = service.turnos(LUNES, LUNES.plusDays(6));

        assertEquals(1, r.getClientesConCancelaciones().size());
        assertEquals("Lucía Paz", r.getClientesConCancelaciones().get(0).getCliente());
        assertEquals(5, r.getClientesConCancelaciones().get(0).getCancelaciones());
        assertEquals(3, r.getLimiteCancelaciones());
    }

    @Test
    void rangoInvertidoSeRechaza() {
        assertThrows(ValidacionException.class, () -> service.turnos(LUNES, LUNES.minusDays(1)));
    }

    @Test
    void rangoMayorAUnAnioSeRechaza() {
        assertThrows(ValidacionException.class, () -> service.turnos(LUNES, LUNES.plusDays(366)));
    }
}

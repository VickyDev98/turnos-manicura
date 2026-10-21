package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.dto.BloqueoRequest;
import com.victoria.turnos_manicura.dto.FranjaRequest;
import com.victoria.turnos_manicura.dto.Intervalo;
import com.victoria.turnos_manicura.exception.ValidacionException;
import com.victoria.turnos_manicura.model.BloqueoModel;
import com.victoria.turnos_manicura.model.FranjaTrabajoModel;
import com.victoria.turnos_manicura.model.Repeticion;
import com.victoria.turnos_manicura.repository.BloqueosRepository;
import com.victoria.turnos_manicura.repository.FranjasTrabajoRepository;
import com.victoria.turnos_manicura.repository.ServiciosRepository;
import com.victoria.turnos_manicura.repository.TurnosRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisponibilidadServiceTest {

    private static final int MANICURISTA = 1;
    // 7 de enero de 2030: es lunes (dia_semana = 1)
    private static final LocalDate LUNES = LocalDate.of(2030, 1, 7);

    @Mock FranjasTrabajoRepository franjasRepository;
    @Mock BloqueosRepository bloqueosRepository;
    @Mock TurnosRepository turnosRepository;
    @Mock ServiciosRepository serviciosRepository;
    @InjectMocks DisponibilidadService service;

    @BeforeEach
    void preparar() {
        when(bloqueosRepository.save(any(BloqueoModel.class))).thenAnswer(i -> i.getArgument(0));
        when(franjasRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
    }

    // ---------- noDisponible ----------

    @Test
    void noDisponible_sinFranjasNiBloqueos_atiendeTodoElDia() {
        assertTrue(service.noDisponible(MANICURISTA, LUNES).isEmpty());
    }

    @Test
    void noDisponible_conUnaFranja_marcaLoQueQuedaAntesYDespues() {
        when(franjasRepository.findByIdManicurista(MANICURISTA)).thenReturn(List.of(franja(1, "08:00", "20:00")));

        List<Intervalo> libres = service.noDisponible(MANICURISTA, LUNES);

        assertEquals(2, libres.size());
        assertEquals(LocalTime.of(6, 0), libres.get(0).getDesde());
        assertEquals(LocalTime.of(8, 0), libres.get(0).getHasta());
        assertEquals(LocalTime.of(20, 0), libres.get(1).getDesde());
        assertEquals(LocalTime.of(23, 0), libres.get(1).getHasta());
    }

    @Test
    void noDisponible_conDosFranjas_marcaElHuecoDelMedio() {
        when(franjasRepository.findByIdManicurista(MANICURISTA)).thenReturn(List.of(
                franja(1, "08:00", "12:00"), franja(1, "14:00", "20:00")));

        List<Intervalo> libres = service.noDisponible(MANICURISTA, LUNES);

        assertEquals(3, libres.size());
        assertEquals(LocalTime.of(12, 0), libres.get(1).getDesde());
        assertEquals(LocalTime.of(14, 0), libres.get(1).getHasta());
    }

    @Test
    void noDisponible_diaSinFranja_noAtiendeNada() {
        // Solo tiene franja los martes (2) y consultamos un lunes
        when(franjasRepository.findByIdManicurista(MANICURISTA)).thenReturn(List.of(franja(2, "08:00", "20:00")));

        List<Intervalo> libres = service.noDisponible(MANICURISTA, LUNES);

        assertEquals(1, libres.size());
        assertEquals(LocalTime.of(6, 0), libres.get(0).getDesde());
        assertEquals(LocalTime.of(23, 0), libres.get(0).getHasta());
    }

    @Test
    void noDisponible_bloqueoUnicoSoloAplicaEseDia() {
        when(bloqueosRepository.findByIdManicurista(MANICURISTA)).thenReturn(List.of(
                bloqueo(1L, Repeticion.UNICA, LUNES, "13:00", "14:00", "Almuerzo")));

        List<Intervalo> eseDia = service.noDisponible(MANICURISTA, LUNES);

        assertEquals(1, eseDia.size());
        assertEquals("Almuerzo", eseDia.get(0).getMotivo());
        assertEquals(1L, eseDia.get(0).getIdBloqueo());
        assertTrue(service.noDisponible(MANICURISTA, LUNES.plusDays(7)).isEmpty());
    }

    @Test
    void noDisponible_bloqueoSinMotivoDiceBloqueado() {
        when(bloqueosRepository.findByIdManicurista(MANICURISTA)).thenReturn(List.of(
                bloqueo(1L, Repeticion.UNICA, LUNES, "13:00", "14:00", null)));

        assertEquals("Bloqueado", service.noDisponible(MANICURISTA, LUNES).get(0).getMotivo());
    }

    @Test
    void noDisponible_bloqueoMesAplicaLosMismosDiasDeLaSemanaDeEseMesNomas() {
        when(bloqueosRepository.findByIdManicurista(MANICURISTA)).thenReturn(List.of(
                bloqueo(1L, Repeticion.MES, LUNES, "12:00", "13:00", "Tramites")));

        assertEquals(1, service.noDisponible(MANICURISTA, LUNES.plusDays(7)).size());   // otro lunes de enero
        assertTrue(service.noDisponible(MANICURISTA, LUNES.plusDays(1)).isEmpty());     // martes
        assertTrue(service.noDisponible(MANICURISTA, LUNES.plusDays(28)).isEmpty());    // lunes de febrero
    }

    @Test
    void noDisponible_bloqueoSiempreAplicaDesdeSuFechaEnAdelante() {
        when(bloqueosRepository.findByIdManicurista(MANICURISTA)).thenReturn(List.of(
                bloqueo(1L, Repeticion.SIEMPRE, LUNES, "13:00", "14:00", "Almuerzo")));

        assertEquals(1, service.noDisponible(MANICURISTA, LUNES.plusDays(70)).size());
        assertTrue(service.noDisponible(MANICURISTA, LUNES.minusDays(7)).isEmpty());
    }

    // ---------- validarTurno ----------

    @Test
    void validarTurno_enHorarioDeTrabajo_noLanzaNada() {
        when(franjasRepository.findByIdManicurista(MANICURISTA)).thenReturn(List.of(franja(1, "08:00", "20:00")));

        assertDoesNotThrow(() -> service.validarTurno(MANICURISTA, LUNES.atTime(10, 0), LUNES.atTime(11, 0)));
    }

    @Test
    void validarTurno_tocandoUnBloqueo_lanzaValidacion() {
        when(bloqueosRepository.findByIdManicurista(MANICURISTA)).thenReturn(List.of(
                bloqueo(1L, Repeticion.UNICA, LUNES, "13:00", "14:00", "Almuerzo")));

        ValidacionException e = assertThrows(ValidacionException.class,
                () -> service.validarTurno(MANICURISTA, LUNES.atTime(12, 30), LUNES.atTime(13, 30)));

        assertEquals("diaHorario", e.getCampo());
    }

    @Test
    void validarTurno_justoAntesDelBloqueo_esValido() {
        when(bloqueosRepository.findByIdManicurista(MANICURISTA)).thenReturn(List.of(
                bloqueo(1L, Repeticion.UNICA, LUNES, "13:00", "14:00", "Almuerzo")));

        assertDoesNotThrow(() -> service.validarTurno(MANICURISTA, LUNES.atTime(12, 0), LUNES.atTime(13, 0)));
    }

    @Test
    void validarTurno_fueraDeLaFranja_lanzaValidacion() {
        when(franjasRepository.findByIdManicurista(MANICURISTA)).thenReturn(List.of(franja(1, "08:00", "20:00")));

        assertThrows(ValidacionException.class,
                () -> service.validarTurno(MANICURISTA, LUNES.atTime(19, 30), LUNES.atTime(20, 30)));
    }

    // ---------- guardarFranjas ----------

    @Test
    void guardarFranjas_valida_reemplazaElHorarioAnterior() {
        List<FranjaTrabajoModel> guardadas = service.guardarFranjas(MANICURISTA, List.of(
                pedidaFranja(1, "08:00", "12:00"), pedidaFranja(1, "14:00", "20:00")));

        assertEquals(2, guardadas.size());
        verify(franjasRepository).deleteByIdManicurista(MANICURISTA);
    }

    @Test
    void guardarFranjas_listaVacia_vuelveAlHorarioCompleto() {
        assertTrue(service.guardarFranjas(MANICURISTA, List.of()).isEmpty());
        verify(franjasRepository).deleteByIdManicurista(MANICURISTA);
    }

    @Test
    void guardarFranjas_superpuestasElMismoDia_lanzaValidacion() {
        ValidacionException e = assertThrows(ValidacionException.class, () -> service.guardarFranjas(MANICURISTA,
                List.of(pedidaFranja(1, "08:00", "12:00"), pedidaFranja(1, "11:00", "14:00"))));

        assertEquals("franjas", e.getCampo());
        verify(franjasRepository, never()).deleteByIdManicurista(MANICURISTA);
    }

    @Test
    void guardarFranjas_mismasHorasEnDiasDistintos_esValido() {
        assertDoesNotThrow(() -> service.guardarFranjas(MANICURISTA,
                List.of(pedidaFranja(1, "08:00", "12:00"), pedidaFranja(2, "08:00", "12:00"))));
    }

    @Test
    void guardarFranjas_inicioPosteriorAlFin_lanzaValidacion() {
        assertThrows(ValidacionException.class,
                () -> service.guardarFranjas(MANICURISTA, List.of(pedidaFranja(1, "12:00", "08:00"))));
    }

    @Test
    void guardarFranjas_fueraDelHorarioDeAtencion_lanzaValidacion() {
        assertThrows(ValidacionException.class,
                () -> service.guardarFranjas(MANICURISTA, List.of(pedidaFranja(1, "05:00", "10:00"))));
        assertThrows(ValidacionException.class,
                () -> service.guardarFranjas(MANICURISTA, List.of(pedidaFranja(1, "20:00", "23:30"))));
    }

    // ---------- bloqueos ----------

    @Test
    void crearBloqueo_valido_loGuardaConSuManicuristaYMotivoSinEspacios() {
        BloqueoModel creado = service.crearBloqueo(MANICURISTA,
                pedidoBloqueo(Repeticion.UNICA, LocalDate.now().plusDays(2), "10:00", "11:00", "  Dentista "));

        assertEquals(MANICURISTA, creado.getIdManicurista());
        assertEquals("Dentista", creado.getMotivo());
    }

    @Test
    void crearBloqueo_motivoVacioQuedaNull() {
        BloqueoModel creado = service.crearBloqueo(MANICURISTA,
                pedidoBloqueo(Repeticion.UNICA, LocalDate.now().plusDays(2), "10:00", "11:00", "   "));

        assertNull(creado.getMotivo());
    }

    @Test
    void crearBloqueo_fechaPasada_lanzaValidacion() {
        ValidacionException e = assertThrows(ValidacionException.class, () -> service.crearBloqueo(MANICURISTA,
                pedidoBloqueo(Repeticion.UNICA, LocalDate.now().minusDays(1), "10:00", "11:00", "x")));

        assertEquals("fecha", e.getCampo());
    }

    @Test
    void crearBloqueo_horaInicioPosteriorALaFin_lanzaValidacion() {
        assertThrows(ValidacionException.class, () -> service.crearBloqueo(MANICURISTA,
                pedidoBloqueo(Repeticion.UNICA, LocalDate.now().plusDays(2), "12:00", "10:00", "x")));
    }

    @Test
    void crearBloqueo_encimaDeUnTurnoFuturo_lanzaValidacion() {
        LocalDate fecha = LocalDate.now().plusDays(2);
        com.victoria.turnos_manicura.model.TurnosModel turno = new com.victoria.turnos_manicura.model.TurnosModel();
        turno.setIdManicurista(MANICURISTA);
        turno.setDiaHorario(LocalDateTime.of(fecha, LocalTime.of(10, 30)));
        turno.setDuracion(LocalTime.of(1, 0));
        turno.setActivo(true);
        when(turnosRepository.findByIdManicuristaAndActivoTrueAndDiaHorarioGreaterThanEqual(
                org.mockito.ArgumentMatchers.eq(MANICURISTA), any())).thenReturn(List.of(turno));

        ValidacionException e = assertThrows(ValidacionException.class, () -> service.crearBloqueo(MANICURISTA,
                pedidoBloqueo(Repeticion.UNICA, fecha, "10:00", "11:00", "x")));

        assertTrue(e.getMessage().contains("cancelalo primero"));
        verify(bloqueosRepository, never()).save(any());
    }

    @Test
    void eliminarBloqueo_deOtraManicurista_lanzaForbidden() {
        when(bloqueosRepository.findById(5L)).thenReturn(Optional.of(
                bloqueo(5L, Repeticion.UNICA, LUNES, "10:00", "11:00", null)));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.eliminarBloqueo(99, 5L));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void eliminarBloqueo_inexistente_lanzaNotFound() {
        when(bloqueosRepository.findById(5L)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.eliminarBloqueo(MANICURISTA, 5L));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void eliminarBloqueo_propio_loBorra() {
        BloqueoModel propio = bloqueo(5L, Repeticion.UNICA, LUNES, "10:00", "11:00", null);
        when(bloqueosRepository.findById(5L)).thenReturn(Optional.of(propio));

        service.eliminarBloqueo(MANICURISTA, 5L);

        verify(bloqueosRepository).delete(propio);
    }

    // ---------- listarBloqueos ----------

    @Test
    void listarBloqueos_ocultaLosQueYaNoPuedenAfectar() {
        LocalDate hoy = LocalDate.now();
        BloqueoModel unicaPasada = bloqueo(1L, Repeticion.UNICA, hoy.minusDays(1), "10:00", "11:00", null);
        BloqueoModel unicaFutura = bloqueo(2L, Repeticion.UNICA, hoy.plusDays(1), "10:00", "11:00", null);
        BloqueoModel mesPasado = bloqueo(3L, Repeticion.MES, hoy.minusMonths(2), "10:00", "11:00", null);
        BloqueoModel siempre = bloqueo(4L, Repeticion.SIEMPRE, hoy.minusYears(1), "10:00", "11:00", null);
        when(bloqueosRepository.findByIdManicurista(MANICURISTA))
                .thenReturn(List.of(unicaPasada, unicaFutura, mesPasado, siempre));

        List<BloqueoModel> visibles = service.listarBloqueos(MANICURISTA);

        assertEquals(List.of(siempre, unicaFutura), visibles);
    }

    // ---------- helpers ----------

    private FranjaTrabajoModel franja(int dia, String desde, String hasta) {
        FranjaTrabajoModel f = new FranjaTrabajoModel();
        f.setIdManicurista(MANICURISTA);
        f.setDiaSemana(dia);
        f.setHoraDesde(LocalTime.parse(desde));
        f.setHoraHasta(LocalTime.parse(hasta));
        return f;
    }

    private FranjaRequest pedidaFranja(int dia, String desde, String hasta) {
        FranjaRequest p = new FranjaRequest();
        p.setDiaSemana(dia);
        p.setHoraDesde(LocalTime.parse(desde));
        p.setHoraHasta(LocalTime.parse(hasta));
        return p;
    }

    private BloqueoModel bloqueo(Long id, Repeticion repeticion, LocalDate fecha, String desde, String hasta, String motivo) {
        BloqueoModel b = new BloqueoModel();
        b.setId(id);
        b.setIdManicurista(MANICURISTA);
        b.setRepeticion(repeticion);
        b.setFecha(fecha);
        b.setHoraDesde(LocalTime.parse(desde));
        b.setHoraHasta(LocalTime.parse(hasta));
        b.setMotivo(motivo);
        return b;
    }

    private BloqueoRequest pedidoBloqueo(Repeticion repeticion, LocalDate fecha, String desde, String hasta, String motivo) {
        BloqueoRequest p = new BloqueoRequest();
        p.setRepeticion(repeticion);
        p.setFecha(fecha);
        p.setHoraDesde(LocalTime.parse(desde));
        p.setHoraHasta(LocalTime.parse(hasta));
        p.setMotivo(motivo);
        return p;
    }
}

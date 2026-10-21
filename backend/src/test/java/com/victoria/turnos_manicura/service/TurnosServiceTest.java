package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.dto.AgendaBloque;
import com.victoria.turnos_manicura.dto.Intervalo;
import com.victoria.turnos_manicura.exception.ValidacionException;
import com.victoria.turnos_manicura.model.ServicioManicuristaModel;
import com.victoria.turnos_manicura.model.ServiciosModel;
import com.victoria.turnos_manicura.model.TiposUsuarioModel;
import com.victoria.turnos_manicura.model.TurnosModel;
import com.victoria.turnos_manicura.model.UsuariosModel;
import com.victoria.turnos_manicura.repository.ServiciosRepository;
import com.victoria.turnos_manicura.repository.TiposUsuarioRepository;
import com.victoria.turnos_manicura.repository.TurnosRepository;
import com.victoria.turnos_manicura.repository.UsuariosRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurnosServiceTest {

    private static final int MANICURISTA = 1;
    private static final int CLIENTE = 2;
    private static final int SERVICIO = 1;

    @Mock TurnosRepository repository;
    @Mock UsuariosRepository usuariosRepository;
    @Mock TiposUsuarioRepository tiposRepository;
    @Mock ServiciosRepository serviciosRepository;
    @Mock DisponibilidadService disponibilidadService;
    @Mock OfertaService ofertaService;
    @Mock NotificacionesService notificaciones;
    @InjectMocks TurnosService service;

    private ServiciosModel servicio;

    @BeforeEach
    void preparar() {
        // Tipos de usuario: 1 = CLIENTE, 2 = MANICURISTA
        when(tiposRepository.findById(1L)).thenReturn(Optional.of(tipo("CLIENTE")));
        when(tiposRepository.findById(2L)).thenReturn(Optional.of(tipo("MANICURISTA")));
        when(usuariosRepository.findById((long) MANICURISTA)).thenReturn(Optional.of(usuario(MANICURISTA, 2, true)));
        when(usuariosRepository.findById((long) CLIENTE)).thenReturn(Optional.of(usuario(CLIENTE, 1, true)));

        servicio = new ServiciosModel();
        servicio.setId((long) SERVICIO);
        servicio.setNombre("Manicura clasica");
        servicio.setDuracion(LocalTime.of(1, 0));
        servicio.setPrecio(5000);
        servicio.setActivo(true);
        when(serviciosRepository.findById((long) SERVICIO)).thenReturn(Optional.of(servicio));

        ServicioManicuristaModel oferta = new ServicioManicuristaModel();
        oferta.setIdManicurista(MANICURISTA);
        oferta.setIdServicio(SERVICIO);
        oferta.setPrecio(5500);
        oferta.setDuracion(LocalTime.of(1, 0));
        oferta.setActivo(true);
        when(ofertaService.activaDe(MANICURISTA, SERVICIO)).thenReturn(oferta);

        when(repository.save(any(TurnosModel.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ---------- guardar ----------

    @Test
    void guardar_turnoValido_loGuardaActivoConPrecioYDuracionDeLaOferta() {
        TurnosModel guardado = service.guardar(turnoNuevo(horario(3, 10, 0)));

        assertTrue(guardado.isActivo());
        assertEquals(5500, guardado.getPrecio());
        assertEquals(LocalTime.of(1, 0), guardado.getDuracion());
        verify(repository).save(guardado);
        verify(notificaciones).turnoReservado(guardado);
    }

    @Test
    void guardar_turnoInvalido_noAvisaANadie() {
        assertThrows(ValidacionException.class, () -> service.guardar(turnoNuevo(horario(-1, 10, 0))));

        verify(notificaciones, never()).turnoReservado(any());
    }

    @Test
    void guardar_bloqueaLaAgendaDeManicuristaYClienteAntesDeValidarElChoque() {
        service.guardar(turnoNuevo(horario(3, 10, 0)));

        // Se bloquean en orden de id, para que dos reservas cruzadas no se traben entre sí
        org.mockito.InOrder orden = org.mockito.Mockito.inOrder(usuariosRepository, repository);
        orden.verify(usuariosRepository).findByIdForUpdate((long) MANICURISTA);
        orden.verify(usuariosRepository).findByIdForUpdate((long) CLIENTE);
        orden.verify(repository).save(any(TurnosModel.class));
    }

    @Test
    void guardar_ignoraLosSegundosDelHorario() {
        TurnosModel guardado = service.guardar(turnoNuevo(horario(3, 10, 0).withSecond(30)));

        assertEquals(0, guardado.getDiaHorario().getSecond());
    }

    @Test
    void guardar_manicuristaInexistente_lanzaValidacion() {
        when(usuariosRepository.findById((long) MANICURISTA)).thenReturn(Optional.empty());

        ValidacionException e = assertThrows(ValidacionException.class,
                () -> service.guardar(turnoNuevo(horario(3, 10, 0))));

        assertEquals("idManicurista", e.getCampo());
        verify(repository, never()).save(any());
    }

    @Test
    void guardar_usuarioQueNoEsManicurista_lanzaValidacion() {
        // El "manicurista" del turno es en realidad un cliente (tipo 1)
        when(usuariosRepository.findById((long) MANICURISTA)).thenReturn(Optional.of(usuario(MANICURISTA, 1, true)));

        ValidacionException e = assertThrows(ValidacionException.class,
                () -> service.guardar(turnoNuevo(horario(3, 10, 0))));

        assertEquals("idManicurista", e.getCampo());
    }

    @Test
    void guardar_clienteDadoDeBaja_lanzaValidacion() {
        when(usuariosRepository.findById((long) CLIENTE)).thenReturn(Optional.of(usuario(CLIENTE, 1, false)));

        ValidacionException e = assertThrows(ValidacionException.class,
                () -> service.guardar(turnoNuevo(horario(3, 10, 0))));

        assertEquals("idCliente", e.getCampo());
        assertTrue(e.getMessage().contains("dado de baja"));
    }

    @Test
    void guardar_servicioInactivo_lanzaValidacion() {
        servicio.setActivo(false);

        ValidacionException e = assertThrows(ValidacionException.class,
                () -> service.guardar(turnoNuevo(horario(3, 10, 0))));

        assertEquals("idServicio", e.getCampo());
    }

    @Test
    void guardar_enElPasado_lanzaValidacion() {
        ValidacionException e = assertThrows(ValidacionException.class,
                () -> service.guardar(turnoNuevo(LocalDateTime.now().minusDays(1))));

        assertEquals("diaHorario", e.getCampo());
        assertTrue(e.getMessage().contains("pasado"));
    }

    @Test
    void guardar_aMasDeSeisMeses_lanzaValidacion() {
        ValidacionException e = assertThrows(ValidacionException.class,
                () -> service.guardar(turnoNuevo(LocalDateTime.now().plusMonths(7))));

        assertTrue(e.getMessage().contains("6 meses"));
    }

    @Test
    void guardar_antesDeLaApertura_lanzaValidacion() {
        assertThrows(ValidacionException.class, () -> service.guardar(turnoNuevo(horario(3, 5, 0))));
    }

    @Test
    void guardar_terminandoDespuesDelCierre_lanzaValidacion() {
        // 22:30 + 1 hora = 23:30, pasa el cierre de las 23:00
        assertThrows(ValidacionException.class, () -> service.guardar(turnoNuevo(horario(3, 22, 30))));
    }

    @Test
    void guardar_terminandoJustoAlCierre_esValido() {
        assertDoesNotThrow(() -> service.guardar(turnoNuevo(horario(3, 22, 0))));
    }

    @Test
    void guardar_superpuestoConOtroTurnoDeLaManicurista_lanzaValidacion() {
        LocalDateTime diaHorario = horario(3, 10, 0);
        TurnosModel existente = turnoExistente(10L, MANICURISTA, 99, diaHorario.plusMinutes(30), LocalTime.of(1, 0));
        when(repository.findByIdManicuristaAndActivoTrueAndDiaHorarioBetween(eq(MANICURISTA), any(), any()))
                .thenReturn(List.of(existente));

        ValidacionException e = assertThrows(ValidacionException.class,
                () -> service.guardar(turnoNuevo(diaHorario)));

        assertTrue(e.getMessage().contains("La manicurista ya tiene un turno"));
    }

    @Test
    void guardar_turnoContiguoNoSePisa() {
        LocalDateTime diaHorario = horario(3, 10, 0);
        // El existente termina justo a las 10:00, cuando empieza el nuevo
        TurnosModel existente = turnoExistente(10L, MANICURISTA, 99, diaHorario.minusHours(1), LocalTime.of(1, 0));
        when(repository.findByIdManicuristaAndActivoTrueAndDiaHorarioBetween(eq(MANICURISTA), any(), any()))
                .thenReturn(List.of(existente));

        assertDoesNotThrow(() -> service.guardar(turnoNuevo(diaHorario)));
    }

    @Test
    void guardar_clienteConOtroTurnoSuperpuesto_lanzaValidacion() {
        LocalDateTime diaHorario = horario(3, 10, 0);
        TurnosModel existente = turnoExistente(10L, 7, CLIENTE, diaHorario.minusMinutes(30), LocalTime.of(1, 0));
        when(repository.findByIdClienteAndActivoTrueAndDiaHorarioBetween(eq(CLIENTE), any(), any()))
                .thenReturn(List.of(existente));

        ValidacionException e = assertThrows(ValidacionException.class,
                () -> service.guardar(turnoNuevo(diaHorario)));

        assertTrue(e.getMessage().contains("El cliente ya tiene otro turno"));
    }

    // ---------- cancelar ----------

    @Test
    void cancelar_turnoPropioDelCliente_loDesactiva() {
        TurnosModel turno = turnoExistente(10L, MANICURISTA, CLIENTE, horario(3, 10, 0), LocalTime.of(1, 0));
        when(repository.findById(10L)).thenReturn(Optional.of(turno));

        TurnosModel cancelado = service.cancelar(10L, sesion(CLIENTE, AuthService.ROL_CLIENTE));

        assertFalse(cancelado.isActivo());
        verify(notificaciones).turnoCancelado(cancelado, (long) CLIENTE);
    }

    @Test
    void cancelar_turnoDeOtraPersona_lanzaForbidden() {
        TurnosModel turno = turnoExistente(10L, MANICURISTA, CLIENTE, horario(3, 10, 0), LocalTime.of(1, 0));
        when(repository.findById(10L)).thenReturn(Optional.of(turno));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.cancelar(10L, sesion(50, AuthService.ROL_CLIENTE)));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        assertTrue(turno.isActivo());
        verify(notificaciones, never()).turnoCancelado(any(), any());
    }

    @Test
    void cancelar_elAdminPuedeCancelarCualquierTurno() {
        TurnosModel turno = turnoExistente(10L, MANICURISTA, CLIENTE, horario(3, 10, 0), LocalTime.of(1, 0));
        when(repository.findById(10L)).thenReturn(Optional.of(turno));

        assertFalse(service.cancelar(10L, sesion(50, AuthService.ROL_ADMIN)).isActivo());
    }

    @Test
    void cancelar_turnoInexistente_lanzaNotFound() {
        when(repository.findById(10L)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.cancelar(10L, sesion(CLIENTE, AuthService.ROL_CLIENTE)));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void cancelar_turnoYaCancelado_lanzaBadRequest() {
        TurnosModel turno = turnoExistente(10L, MANICURISTA, CLIENTE, horario(3, 10, 0), LocalTime.of(1, 0));
        turno.setActivo(false);
        when(repository.findById(10L)).thenReturn(Optional.of(turno));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.cancelar(10L, sesion(CLIENTE, AuthService.ROL_CLIENTE)));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void cancelar_turnoQueYaPaso_lanzaBadRequest() {
        TurnosModel turno = turnoExistente(10L, MANICURISTA, CLIENTE, LocalDateTime.now().minusDays(1), LocalTime.of(1, 0));
        when(repository.findById(10L)).thenReturn(Optional.of(turno));

        assertThrows(ResponseStatusException.class,
                () -> service.cancelar(10L, sesion(CLIENTE, AuthService.ROL_CLIENTE)));
    }

    // ---------- reprogramar ----------

    @Test
    void reprogramar_aUnHorarioLibre_mueveElTurno() {
        TurnosModel turno = turnoExistente(10L, MANICURISTA, CLIENTE, horario(3, 10, 0), LocalTime.of(1, 0));
        when(repository.findById(10L)).thenReturn(Optional.of(turno));

        TurnosModel movido = service.reprogramar(10L, horario(4, 15, 0), sesion(CLIENTE, AuthService.ROL_CLIENTE));

        assertEquals(horario(4, 15, 0), movido.getDiaHorario());
        verify(notificaciones).turnoReprogramado(movido, horario(3, 10, 0), (long) CLIENTE);
    }

    @Test
    void reprogramar_vuelveAPedirElRecordatorio() {
        TurnosModel turno = turnoExistente(10L, MANICURISTA, CLIENTE, horario(3, 10, 0), LocalTime.of(1, 0));
        turno.setRecordatorioEnviado(true);
        when(repository.findById(10L)).thenReturn(Optional.of(turno));

        service.reprogramar(10L, horario(4, 15, 0), sesion(CLIENTE, AuthService.ROL_CLIENTE));

        assertFalse(turno.isRecordatorioEnviado());
    }

    @Test
    void reprogramar_noSeChocaConSiMismo() {
        TurnosModel turno = turnoExistente(10L, MANICURISTA, CLIENTE, horario(3, 10, 0), LocalTime.of(1, 0));
        when(repository.findById(10L)).thenReturn(Optional.of(turno));
        // La búsqueda devuelve al propio turno: tiene que ignorarlo
        when(repository.findByIdManicuristaAndActivoTrueAndDiaHorarioBetween(eq(MANICURISTA), any(), any()))
                .thenReturn(List.of(turno));

        assertDoesNotThrow(() -> service.reprogramar(10L, horario(3, 10, 30), sesion(CLIENTE, AuthService.ROL_CLIENTE)));
    }

    @Test
    void reprogramar_turnoCancelado_lanzaBadRequest() {
        TurnosModel turno = turnoExistente(10L, MANICURISTA, CLIENTE, horario(3, 10, 0), LocalTime.of(1, 0));
        turno.setActivo(false);
        when(repository.findById(10L)).thenReturn(Optional.of(turno));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.reprogramar(10L, horario(4, 15, 0), sesion(CLIENTE, AuthService.ROL_CLIENTE)));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void reprogramar_enHorarioOcupado_lanzaValidacion() {
        TurnosModel turno = turnoExistente(10L, MANICURISTA, CLIENTE, horario(3, 10, 0), LocalTime.of(1, 0));
        when(repository.findById(10L)).thenReturn(Optional.of(turno));
        TurnosModel otro = turnoExistente(11L, MANICURISTA, 99, horario(4, 15, 0), LocalTime.of(1, 0));
        when(repository.findByIdManicuristaAndActivoTrueAndDiaHorarioBetween(eq(MANICURISTA), any(), any()))
                .thenReturn(List.of(otro));

        assertThrows(ValidacionException.class,
                () -> service.reprogramar(10L, horario(4, 15, 30), sesion(CLIENTE, AuthService.ROL_CLIENTE)));
    }

    // ---------- listarPara ----------

    @Test
    void listarPara_cadaRolVeSoloLoQueLeCorresponde() {
        TurnosModel a = turnoExistente(1L, 1, 2, horario(3, 10, 0), LocalTime.of(1, 0));
        TurnosModel b = turnoExistente(2L, 1, 3, horario(3, 12, 0), LocalTime.of(1, 0));
        TurnosModel c = turnoExistente(3L, 4, 2, horario(3, 14, 0), LocalTime.of(1, 0));
        when(repository.findAll()).thenReturn(List.of(a, b, c));

        assertEquals(3, service.listarPara(sesion(50, AuthService.ROL_ADMIN)).size());
        assertEquals(List.of(a, b), service.listarPara(sesion(1, AuthService.ROL_MANICURISTA)));
        assertEquals(List.of(a, c), service.listarPara(sesion(2, AuthService.ROL_CLIENTE)));
    }

    // ---------- paginaPara ----------

    @Test
    void paginaPara_cadaRolConsultaSoloLoSuyo() {
        service.paginaPara(sesion(50, AuthService.ROL_ADMIN), null, 0, 20);
        verify(repository).findAll(any(Pageable.class));

        service.paginaPara(sesion(50, AuthService.ROL_ADMIN), 1, 0, 20);
        verify(repository).findByIdManicurista(eq(1), any(Pageable.class));

        // La manicurista ignora el filtro: siempre ve los suyos
        service.paginaPara(sesion(4, AuthService.ROL_MANICURISTA), 1, 0, 20);
        verify(repository).findByIdManicurista(eq(4), any(Pageable.class));

        service.paginaPara(sesion(2, AuthService.ROL_CLIENTE), null, 0, 20);
        verify(repository).findByIdCliente(eq(2), any(Pageable.class));

        service.paginaPara(sesion(2, AuthService.ROL_CLIENTE), 1, 0, 20);
        verify(repository).findByIdClienteAndIdManicurista(eq(2), eq(1), any(Pageable.class));
    }

    @Test
    void paginaPara_acotaElTamanioYLaPagina() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        service.paginaPara(sesion(50, AuthService.ROL_ADMIN), null, -3, 100000);

        verify(repository).findAll(captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(100, captor.getValue().getPageSize());
    }

    // ---------- finDe ----------

    @Test
    void finDe_usaLaDuracionDelTurno() {
        TurnosModel turno = turnoExistente(1L, 1, 2, horario(3, 10, 0), LocalTime.of(1, 30));

        assertEquals(horario(3, 11, 30), TurnosService.finDe(turno, servicio));
    }

    @Test
    void finDe_sinDuracionUsaLaDelCatalogo() {
        TurnosModel turno = turnoExistente(1L, 1, 2, horario(3, 10, 0), null);

        assertEquals(horario(3, 11, 0), TurnosService.finDe(turno, servicio));
    }

    @Test
    void finDe_sinNingunaDuracionTerminaCuandoEmpieza() {
        TurnosModel turno = turnoExistente(1L, 1, 2, horario(3, 10, 0), null);

        assertEquals(horario(3, 10, 0), TurnosService.finDe(turno, null));
    }

    // ---------- agenda ----------

    @Test
    void agenda_juntaTurnosYNoDisponibleOrdenadosPorHora() {
        LocalDate fecha = LocalDate.now().plusDays(3);
        TurnosModel turno = turnoExistente(10L, MANICURISTA, CLIENTE, fecha.atTime(10, 0), LocalTime.of(1, 0));
        UsuariosModel cliente = usuario(CLIENTE, 1, true);
        cliente.setNombre("Maria");
        cliente.setApellido("Perez");
        when(usuariosRepository.findById((long) CLIENTE)).thenReturn(Optional.of(cliente));
        when(repository.findByIdManicuristaAndActivoTrueAndDiaHorarioBetween(eq(MANICURISTA), any(), any()))
                .thenReturn(List.of(turno));
        when(disponibilidadService.noDisponible(MANICURISTA, fecha))
                .thenReturn(List.of(new Intervalo(LocalTime.of(6, 0), LocalTime.of(8, 0), "Fuera de horario", null)));

        List<AgendaBloque> agenda = service.agenda(MANICURISTA, fecha);

        assertEquals(2, agenda.size());
        assertEquals("NO_DISPONIBLE", agenda.get(0).getTipo());
        assertEquals("TURNO", agenda.get(1).getTipo());
        assertEquals("Maria Perez", agenda.get(1).getCliente());
        assertEquals(fecha.atTime(11, 0), agenda.get(1).getFin());
    }

    @Test
    void agenda_ignoraTurnosDeLaNocheAnteriorQueYaTerminaron() {
        LocalDate fecha = LocalDate.now().plusDays(3);
        TurnosModel anterior = turnoExistente(10L, MANICURISTA, CLIENTE, fecha.minusDays(1).atTime(20, 0), LocalTime.of(1, 0));
        when(repository.findByIdManicuristaAndActivoTrueAndDiaHorarioBetween(eq(MANICURISTA), any(), any()))
                .thenReturn(List.of(anterior));
        when(disponibilidadService.noDisponible(anyInt(), any())).thenReturn(List.of());

        assertTrue(service.agenda(MANICURISTA, fecha).isEmpty());
    }

    // ---------- helpers ----------

    // Hoy + dias, a la hora indicada (siempre a futuro para no chocar con "no puede quedar en el pasado")
    private LocalDateTime horario(int dias, int hora, int minuto) {
        return LocalDate.now().plusDays(dias).atTime(hora, minuto);
    }

    private TurnosModel turnoNuevo(LocalDateTime diaHorario) {
        TurnosModel t = new TurnosModel();
        t.setIdManicurista(MANICURISTA);
        t.setIdCliente(CLIENTE);
        t.setIdServicio(SERVICIO);
        t.setDiaHorario(diaHorario);
        return t;
    }

    private TurnosModel turnoExistente(Long id, int manicurista, int cliente, LocalDateTime diaHorario, LocalTime duracion) {
        TurnosModel t = new TurnosModel();
        t.setId(id);
        t.setIdManicurista(manicurista);
        t.setIdCliente(cliente);
        t.setIdServicio(SERVICIO);
        t.setDiaHorario(diaHorario);
        t.setDuracion(duracion);
        t.setActivo(true);
        return t;
    }

    private UsuariosModel usuario(int id, int tipo, boolean activo) {
        UsuariosModel u = new UsuariosModel();
        u.setId((long) id);
        u.setTipo(tipo);
        u.setNombre("Usuario" + id);
        u.setApellido("Prueba");
        u.setActivo(activo);
        return u;
    }

    private TiposUsuarioModel tipo(String descripcion) {
        TiposUsuarioModel t = new TiposUsuarioModel();
        t.setDescripcion(descripcion);
        return t;
    }

    private AuthService.Sesion sesion(int idUsuario, String rol) {
        return new AuthService.Sesion((long) idUsuario, rol);
    }
}

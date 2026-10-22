package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.model.TurnosModel;
import com.victoria.turnos_manicura.repository.TurnosRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecordatoriosServiceTest {

    @Mock TurnosRepository repository;
    @Mock NotificacionesService notificaciones;

    private RecordatoriosService service;

    @BeforeEach
    void preparar() {
        service = new RecordatoriosService(repository, notificaciones, 24);
        when(notificaciones.disponible()).thenReturn(true);
    }

    @Test
    void marcaComoEnviadosSoloLosQueSeMandaron() {
        TurnosModel enviado = turno(1L);
        TurnosModel fallido = turno(2L);
        when(repository.findByActivoTrueAndRecordatorioEnviadoFalseAndDiaHorarioBetween(any(), any()))
                .thenReturn(List.of(enviado, fallido));
        when(notificaciones.recordatorio(enviado)).thenReturn(true);
        when(notificaciones.recordatorio(fallido)).thenReturn(false);

        service.enviarPendientes();

        assertTrue(enviado.isRecordatorioEnviado());
        assertFalse(fallido.isRecordatorioEnviado());
        verify(repository).save(enviado);
        verify(repository, never()).save(fallido);
    }

    @Test
    void buscaLosTurnosDeLasProximas24Horas() {
        service.enviarPendientes();

        ArgumentCaptor<LocalDateTime> desde = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> hasta = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).findByActivoTrueAndRecordatorioEnviadoFalseAndDiaHorarioBetween(desde.capture(), hasta.capture());
        assertEquals(24, Duration.between(desde.getValue(), hasta.getValue()).toHours());
    }

    @Test
    void sinCorreoConfigurado_noConsultaNiMarcaNada() {
        when(notificaciones.disponible()).thenReturn(false);

        service.enviarPendientes();

        verify(repository, never()).findByActivoTrueAndRecordatorioEnviadoFalseAndDiaHorarioBetween(any(), any());
    }

    private TurnosModel turno(Long id) {
        TurnosModel t = new TurnosModel();
        t.setId(id);
        return t;
    }
}

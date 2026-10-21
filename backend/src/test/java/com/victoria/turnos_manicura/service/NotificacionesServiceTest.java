package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.model.ServiciosModel;
import com.victoria.turnos_manicura.model.TurnosModel;
import com.victoria.turnos_manicura.model.UsuariosModel;
import com.victoria.turnos_manicura.repository.ServiciosRepository;
import com.victoria.turnos_manicura.repository.UsuariosRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificacionesServiceTest {

    private static final long MANICURISTA = 1;
    private static final long CLIENTE = 2;

    @Mock ObjectProvider<JavaMailSender> mailSender;
    @Mock JavaMailSender sender;
    @Mock UsuariosRepository usuariosRepository;
    @Mock ServiciosRepository serviciosRepository;

    private NotificacionesService service;
    private TurnosModel turno;

    @BeforeEach
    void preparar() {
        service = new NotificacionesService(mailSender, usuariosRepository, serviciosRepository, "turnos@test.com");
        when(mailSender.getIfAvailable()).thenReturn(sender);

        when(usuariosRepository.findById(MANICURISTA)).thenReturn(Optional.of(usuario(MANICURISTA, "Ana", "ana@mail.com")));
        when(usuariosRepository.findById(CLIENTE)).thenReturn(Optional.of(usuario(CLIENTE, "Maria", "maria@mail.com")));

        ServiciosModel servicio = new ServiciosModel();
        servicio.setNombre("Soft gel");
        when(serviciosRepository.findById(5L)).thenReturn(Optional.of(servicio));

        turno = new TurnosModel();
        turno.setIdManicurista((int) MANICURISTA);
        turno.setIdCliente((int) CLIENTE);
        turno.setIdServicio(5);
        turno.setDiaHorario(LocalDateTime.of(2026, 12, 1, 15, 0));
    }

    @Test
    void turnoReservado_avisaALosDos() {
        service.turnoReservado(turno);

        assertEquals(List.of("maria@mail.com", "ana@mail.com"), destinatarios(2));
    }

    @Test
    void turnoCancelado_noAvisaAQuienCancelo() {
        service.turnoCancelado(turno, CLIENTE);

        assertEquals(List.of("ana@mail.com"), destinatarios(1));
    }

    @Test
    void turnoCancelado_elAdminNoEsParticipante_avisaALosDos() {
        service.turnoCancelado(turno, 50L);

        assertEquals(2, destinatarios(2).size());
    }

    @Test
    void turnoReprogramado_incluyeElHorarioAnterior() {
        service.turnoReprogramado(turno, LocalDateTime.of(2026, 11, 20, 10, 0), MANICURISTA);

        ArgumentCaptor<SimpleMailMessage> mensaje = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(mensaje.capture());
        assertEquals("Turno reprogramado", mensaje.getValue().getSubject());
        assertTrue(mensaje.getValue().getText().contains("Horario anterior"));
        assertTrue(mensaje.getValue().getText().contains("Soft gel"));
        assertEquals("turnos@test.com", mensaje.getValue().getFrom());
    }

    @Test
    void recordatorio_vaSoloAlCliente() {
        assertTrue(service.recordatorio(turno));

        assertEquals(List.of("maria@mail.com"), destinatarios(1));
    }

    @Test
    void usuarioSinEmail_noRecibeNada() {
        when(usuariosRepository.findById(CLIENTE)).thenReturn(Optional.of(usuario(CLIENTE, "Maria", null)));

        assertFalse(service.recordatorio(turno));

        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sinCorreoConfigurado_noMandaNiFalla() {
        when(mailSender.getIfAvailable()).thenReturn(null);

        assertFalse(service.disponible());
        assertFalse(service.recordatorio(turno));
        assertDoesNotThrow(() -> service.turnoReservado(turno));
    }

    @Test
    void siElServidorDeCorreoFalla_nadaSeRompe() {
        doThrow(new RuntimeException("SMTP caído")).when(sender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> service.turnoReservado(turno));
        assertFalse(service.recordatorio(turno));
    }

    private List<String> destinatarios(int esperados) {
        ArgumentCaptor<SimpleMailMessage> mensaje = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, times(esperados)).send(mensaje.capture());
        return mensaje.getAllValues().stream().map(m -> m.getTo()[0]).toList();
    }

    private UsuariosModel usuario(long id, String nombre, String email) {
        UsuariosModel u = new UsuariosModel();
        u.setId(id);
        u.setNombre(nombre);
        u.setApellido("Test");
        u.setEmail(email);
        return u;
    }
}

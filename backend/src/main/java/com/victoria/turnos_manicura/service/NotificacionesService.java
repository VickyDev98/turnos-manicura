package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.model.ServiciosModel;
import com.victoria.turnos_manicura.model.TurnosModel;
import com.victoria.turnos_manicura.model.UsuariosModel;
import com.victoria.turnos_manicura.repository.ServiciosRepository;
import com.victoria.turnos_manicura.repository.UsuariosRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Avisos por mail sobre los turnos. Si no hay un servidor de correo configurado (spring.mail.host)
 * no se manda nada: solo queda un renglón en el log. Un mail que falla nunca rompe la operación del turno.
 */
@Service
public class NotificacionesService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionesService.class);
    private static final DateTimeFormatter DIA_Y_HORA =
            DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy 'a las' HH:mm", Locale.forLanguageTag("es-AR"));

    private final ObjectProvider<JavaMailSender> mailSender;
    private final UsuariosRepository usuariosRepository;
    private final ServiciosRepository serviciosRepository;
    private final String remitente;

    public NotificacionesService(ObjectProvider<JavaMailSender> mailSender, UsuariosRepository usuariosRepository,
                                 ServiciosRepository serviciosRepository,
                                 @Value("${app.notificaciones.remitente:turnos@manicura.local}") String remitente) {
        this.mailSender = mailSender;
        this.usuariosRepository = usuariosRepository;
        this.serviciosRepository = serviciosRepository;
        this.remitente = remitente;
    }

    // ¿Hay un servidor de correo configurado? Sin él no tiene sentido marcar los recordatorios como enviados
    public boolean disponible() {
        return mailSender.getIfAvailable() != null;
    }

    // Los avisos de reserva, cancelación y reprogramación van a la manicurista y al cliente,
    // salvo a quien hizo la acción (idQuienLoHizo; null = a los dos)

    @Async
    public void turnoReservado(TurnosModel turno) {
        avisarALosDosLados(turno, null, "Turno reservado", "Se reservó el siguiente turno:", null);
    }

    @Async
    public void turnoCancelado(TurnosModel turno, Long idQuienLoHizo) {
        avisarALosDosLados(turno, idQuienLoHizo, "Turno cancelado", "Se canceló el siguiente turno:", null);
    }

    @Async
    public void turnoReprogramado(TurnosModel turno, LocalDateTime horarioAnterior, Long idQuienLoHizo) {
        avisarALosDosLados(turno, idQuienLoHizo, "Turno reprogramado", "Se cambió el horario del siguiente turno:",
                "Horario anterior: " + DIA_Y_HORA.format(horarioAnterior));
    }

    // Recordatorio al cliente. Devuelve true si se mandó, para que el que llama lo marque como enviado
    public boolean recordatorio(TurnosModel turno) {
        UsuariosModel cliente = usuariosRepository.findById((long) turno.getIdCliente()).orElse(null);
        if (cliente == null) {
            return false;
        }
        return enviar(cliente, "Recordatorio de tu turno", "Te recordamos que tenés este turno próximamente:", turno, null);
    }

    private void avisarALosDosLados(TurnosModel turno, Long idQuienLoHizo, String asunto, String intro, String extra) {
        Stream.of((long) turno.getIdCliente(), (long) turno.getIdManicurista())
                .filter(id -> !id.equals(idQuienLoHizo))
                .distinct()
                .map(id -> usuariosRepository.findById(id).orElse(null))
                .filter(u -> u != null)
                .forEach(u -> enviar(u, asunto, intro, turno, extra));
    }

    private boolean enviar(UsuariosModel destino, String asunto, String intro, TurnosModel turno, String extra) {
        if (destino.getEmail() == null || destino.getEmail().isBlank()) {
            return false;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("Correo no configurado: no se avisa a {} ({})", destino.getEmail(), asunto);
            return false;
        }
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setFrom(remitente);
            mensaje.setTo(destino.getEmail());
            mensaje.setSubject(asunto);
            mensaje.setText("Hola " + destino.getNombre() + ",\n\n" + intro + "\n\n" + detalle(turno, extra)
                    + "\n\nTurnos Manicura");
            sender.send(mensaje);
            return true;
        } catch (Exception e) {
            log.warn("No se pudo mandar el mail \"{}\" a {}: {}", asunto, destino.getEmail(), e.getMessage());
            return false;
        }
    }

    private String detalle(TurnosModel turno, String extra) {
        ServiciosModel servicio = serviciosRepository.findById((long) turno.getIdServicio()).orElse(null);
        UsuariosModel manicurista = usuariosRepository.findById((long) turno.getIdManicurista()).orElse(null);
        UsuariosModel cliente = usuariosRepository.findById((long) turno.getIdCliente()).orElse(null);

        StringBuilder texto = new StringBuilder();
        texto.append("Servicio: ").append(servicio == null ? "-" : servicio.getNombre()).append('\n');
        texto.append("Día y hora: ").append(DIA_Y_HORA.format(turno.getDiaHorario())).append('\n');
        if (extra != null) {
            texto.append(extra).append('\n');
        }
        texto.append("Manicurista: ").append(nombre(manicurista));
        if (manicurista != null && manicurista.getDireccion() != null && !manicurista.getDireccion().isBlank()) {
            texto.append(" (").append(manicurista.getDireccion()).append(')');
        }
        texto.append('\n').append("Cliente: ").append(nombre(cliente));
        return texto.toString();
    }

    private String nombre(UsuariosModel usuario) {
        return usuario == null ? "-" : usuario.getNombre() + " " + usuario.getApellido();
    }
}

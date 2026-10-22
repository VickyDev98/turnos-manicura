package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.model.TurnosModel;
import com.victoria.turnos_manicura.repository.TurnosRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Manda el recordatorio a los clientes que tienen un turno dentro de las próximas N horas
 * (app.recordatorios.horas-antes, 24 por defecto). Cada turno recibe uno solo: queda marcado al enviarlo.
 */
@Service
public class RecordatoriosService {

    private final TurnosRepository repository;
    private final NotificacionesService notificaciones;
    private final long horasAntes;

    public RecordatoriosService(TurnosRepository repository, NotificacionesService notificaciones,
                                @Value("${app.recordatorios.horas-antes:24}") long horasAntes) {
        this.repository = repository;
        this.notificaciones = notificaciones;
        this.horasAntes = horasAntes;
    }

    @Scheduled(initialDelay = 1, fixedDelayString = "${app.recordatorios.intervalo-minutos:15}", timeUnit = TimeUnit.MINUTES)
    public void enviarPendientes() {
        // Sin correo configurado no se marca nada: cuando se configure, los recordatorios salen
        if (!notificaciones.disponible()) {
            return;
        }
        LocalDateTime ahora = LocalDateTime.now();
        for (TurnosModel turno : repository.findByActivoTrueAndRecordatorioEnviadoFalseAndDiaHorarioBetween(
                ahora, ahora.plusHours(horasAntes))) {
            if (notificaciones.recordatorio(turno)) {
                turno.setRecordatorioEnviado(true);
                repository.save(turno);
            }
        }
    }
}

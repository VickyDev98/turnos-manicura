package com.victoria.turnos_manicura.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// @Async: los avisos por mail no demoran la respuesta al reservar o cancelar
// @Scheduled: el recordatorio periódico de los turnos próximos
@Configuration
@EnableAsync
@EnableScheduling
public class TareasConfig {
}

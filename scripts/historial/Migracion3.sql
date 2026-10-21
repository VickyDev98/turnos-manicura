-- Horario de trabajo y bloqueos de las manicuristas. Ejecutar una sola vez.
USE TURNOS;

-- Franjas en las que trabaja cada manicurista (dia_semana: 1 = lunes ... 7 = domingo).
-- Si una manicurista no tiene ninguna franja cargada se considera que atiende de 06:00 a 23:00 todos los días.
CREATE TABLE franjas_trabajo(
	id INT AUTO_INCREMENT,
	id_usuario_manicurista INT NOT NULL,
	dia_semana INT NOT NULL,
	hora_desde TIME NOT NULL,
	hora_hasta TIME NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT fkFranjaManicurista FOREIGN KEY (id_usuario_manicurista) REFERENCES usuarios(id)
)ENGINE=InnoDB;

-- Horarios bloqueados. repeticion:
--   UNICA   = solo el día de "fecha"
--   MES     = todos los mismos días de la semana del mes de "fecha"
--   SIEMPRE = todos los mismos días de la semana desde "fecha" en adelante
CREATE TABLE bloqueos(
	id INT AUTO_INCREMENT,
	id_usuario_manicurista INT NOT NULL,
	repeticion VARCHAR(10) NOT NULL,
	fecha DATE NOT NULL,
	hora_desde TIME NOT NULL,
	hora_hasta TIME NOT NULL,
	motivo VARCHAR(50),
	PRIMARY KEY (id),
	CONSTRAINT fkBloqueoManicurista FOREIGN KEY (id_usuario_manicurista) REFERENCES usuarios(id)
)ENGINE=InnoDB;

-- Ana (id 1) trabaja de lunes a sábado de 08:00 a 20:00
INSERT INTO franjas_trabajo (id_usuario_manicurista,dia_semana,hora_desde,hora_hasta) VALUES
(1,1,'08:00:00','20:00:00'),
(1,2,'08:00:00','20:00:00'),
(1,3,'08:00:00','20:00:00'),
(1,4,'08:00:00','20:00:00'),
(1,5,'08:00:00','20:00:00'),
(1,6,'08:00:00','20:00:00');

-- Bloqueos de ejemplo de Ana, uno de cada tipo:
--   todos los jueves de 13 a 14 (almuerzo), desde el próximo jueves
--   todos los martes de este mes de 12 a 13
--   solo el día de hoy + 3 de 15 a 16
INSERT INTO bloqueos (id_usuario_manicurista,repeticion,fecha,hora_desde,hora_hasta,motivo) VALUES
(1,'SIEMPRE',CURDATE() + INTERVAL ((3 - WEEKDAY(CURDATE()) + 7) % 7) DAY,'13:00:00','14:00:00','Almuerzo'),
(1,'MES',CURDATE() + INTERVAL ((1 - WEEKDAY(CURDATE()) + 7) % 7) DAY,'12:00:00','13:00:00','Trámites'),
(1,'UNICA',CURDATE() + INTERVAL 3 DAY,'15:00:00','16:00:00','Turno médico');

-- El turno de prueba de más adelante cae lunes (Ana no trabaja los domingos)
UPDATE turnos SET dia_horario = TIMESTAMP(CURDATE() + INTERVAL 41 DAY,'15:00:00') WHERE id = 24;

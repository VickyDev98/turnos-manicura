-- Datos de prueba: solo se cargan donde se agregue classpath:db/datos-prueba a spring.flyway.locations
-- (docker-compose lo hace). Nunca en producción. Se aplican justo después de la V1, en una base vacía,
-- así que solo pueden usar el esquema de la V1.

-- Todos los usuarios de prueba tienen la contraseña: 123456 (guardada encriptada con BCrypt)
INSERT INTO usuarios (tipo,nombre,apellido,numero_contacto,email,password) VALUES
(2,'Ana','Gomez',1134567890,'ana.gomez@mail.com','$2a$10$fpHvu7aIB3jtxiwHBzpJIugsMOiDD.HhCpf7x45z4f1FiNLlGRvpC'),
(2,'Lucia','Fernandez',1134567891,'lucia.fernandez@mail.com','$2a$10$fpHvu7aIB3jtxiwHBzpJIugsMOiDD.HhCpf7x45z4f1FiNLlGRvpC'),
(2,'Carla','Suarez',1134567892,'carla.suarez@mail.com','$2a$10$fpHvu7aIB3jtxiwHBzpJIugsMOiDD.HhCpf7x45z4f1FiNLlGRvpC'),
(2,'Valeria','Mendez',1134567893,'valeria.mendez@mail.com','$2a$10$fpHvu7aIB3jtxiwHBzpJIugsMOiDD.HhCpf7x45z4f1FiNLlGRvpC'),
(2,'Sofia','Lopez',1134567894,'sofia.lopez@mail.com','$2a$10$fpHvu7aIB3jtxiwHBzpJIugsMOiDD.HhCpf7x45z4f1FiNLlGRvpC'),
(1,'Maria','Perez',1134567801,'maria.perez@mail.com','$2a$10$fpHvu7aIB3jtxiwHBzpJIugsMOiDD.HhCpf7x45z4f1FiNLlGRvpC'),
(1,'Laura','Rodriguez',1134567802,'laura.rodriguez@mail.com','$2a$10$fpHvu7aIB3jtxiwHBzpJIugsMOiDD.HhCpf7x45z4f1FiNLlGRvpC'),
(1,'Paula','Martinez',1134567803,'paula.martinez@mail.com','$2a$10$fpHvu7aIB3jtxiwHBzpJIugsMOiDD.HhCpf7x45z4f1FiNLlGRvpC'),
(1,'Julieta','Sanchez',1134567804,'julieta.sanchez@mail.com','$2a$10$fpHvu7aIB3jtxiwHBzpJIugsMOiDD.HhCpf7x45z4f1FiNLlGRvpC'),
(1,'Camila','Diaz',1134567805,'camila.diaz@mail.com','$2a$10$fpHvu7aIB3jtxiwHBzpJIugsMOiDD.HhCpf7x45z4f1FiNLlGRvpC');

INSERT INTO servicios (nombre,descripcion,duracion,precio,activo) VALUES
('Manicura clasica','Limpieza y esmalte','00:40:00',5000,1),
('Manicura semipermanente','Esmalte semi','01:00:00',8000,1),
('Kapping gel','Refuerzo gel','01:20:00',10000,1),
('Soft gel','Extensiones soft gel','01:30:00',12000,1),
('Esculpidas gel','Uñas esculpidas','02:00:00',15000,1),
('Nail art simple','Diseño basico','00:20:00',2000,1),
('Nail art avanzado','Diseño complejo','00:40:00',4000,1),
('Retiro semi','Retiro esmalte semi','00:20:00',2500,1),
('Belleza de manos','Tratamiento manos','00:30:00',4500,1),
('Spa de manos','Hidratacion profunda','00:40:00',6000,1);


INSERT INTO turnos (id_usuario_manicurista,id_usuario_cliente,id_servicio,dia_horario,activo) VALUES
(1,6,1,'2026-03-10 10:00:00',1),
(2,7,2,'2026-03-10 11:00:00',1),
(3,8,3,'2026-03-10 12:00:00',1),
(4,9,4,'2026-03-10 13:00:00',1),
(5,10,5,'2026-03-10 14:00:00',1),
(1,6,1,'2026-03-11 10:00:00',1),
(2,7,2,'2026-03-11 11:00:00',1),
(3,8,3,'2026-03-11 12:00:00',1),
(4,9,4,'2026-03-11 13:00:00',1),
(5,10,5,'2026-03-11 14:00:00',1);

-- Turnos de HOY (CURDATE) y de mañana para ver la agenda: rojo = ocupado, verde = libre
-- Ana (id 1):   09:00-09:40, 11:00-12:00, 14:30-16:30, 18:00-19:30
-- Lucia (id 2): 10:00-11:20, 15:00-15:30
INSERT INTO turnos (id_usuario_manicurista,id_usuario_cliente,id_servicio,dia_horario,activo) VALUES
(1,6,1,TIMESTAMP(CURDATE(),'09:00:00'),1),
(1,7,2,TIMESTAMP(CURDATE(),'11:00:00'),1),
(1,8,5,TIMESTAMP(CURDATE(),'14:30:00'),1),
(1,9,4,TIMESTAMP(CURDATE(),'18:00:00'),1),
(2,10,3,TIMESTAMP(CURDATE(),'10:00:00'),1),
(2,6,9,TIMESTAMP(CURDATE(),'15:00:00'),1),
(1,6,2,TIMESTAMP(CURDATE() + INTERVAL 1 DAY,'10:00:00'),1),
(1,7,7,TIMESTAMP(CURDATE() + INTERVAL 1 DAY,'13:00:00'),1);

-- Dirección de las manicuristas y turnos de los próximos 2 meses (para el calendario del cliente)
UPDATE usuarios SET direccion = 'Av. Corrientes 1234, CABA'    WHERE id = 1;
UPDATE usuarios SET direccion = 'Calle Mitre 567, Avellaneda'  WHERE id = 2;
UPDATE usuarios SET direccion = 'Av. Rivadavia 8900, CABA'     WHERE id = 3;
UPDATE usuarios SET direccion = 'San Martin 320, Quilmes'      WHERE id = 4;
UPDATE usuarios SET direccion = 'Belgrano 45, Lomas de Zamora' WHERE id = 5;

INSERT INTO turnos (id_usuario_manicurista,id_usuario_cliente,id_servicio,dia_horario,activo) VALUES
(2,6,3,TIMESTAMP(CURDATE() + INTERVAL 5 DAY,'16:00:00'),1),
(3,6,6,TIMESTAMP(CURDATE() + INTERVAL 12 DAY,'11:30:00'),1),
(1,6,4,TIMESTAMP(CURDATE() + INTERVAL 20 DAY,'17:00:00'),1),
(4,6,10,TIMESTAMP(CURDATE() + INTERVAL 33 DAY,'10:00:00'),1),
(1,6,2,TIMESTAMP(CURDATE() + INTERVAL 41 DAY,'15:00:00'),1),
(5,7,5,TIMESTAMP(CURDATE() + INTERVAL 8 DAY,'09:00:00'),1);

-- Horario de trabajo y bloqueos de ejemplo (Ana)
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

-- Administrador (único que crea usuarios desde el sistema). Contraseña: 123456
INSERT INTO usuarios (tipo,nombre,apellido,numero_contacto,email,password)
SELECT id,'Admin','Sistema',1100000000,'admin@mail.com','$2a$10$fpHvu7aIB3jtxiwHBzpJIugsMOiDD.HhCpf7x45z4f1FiNLlGRvpC' FROM tiposusuario WHERE descripcion = 'ADMIN';

-- Servicios por manicurista (cada una con su precio) y precio guardado en cada turno
-- Al principio todas las manicuristas ofrecen todo el catálogo al precio del catálogo
INSERT INTO servicios_manicurista (id_usuario_manicurista, id_servicio, precio, duracion, activo)
SELECT u.id, s.id, s.precio, s.duracion, 1
FROM usuarios u
JOIN tiposusuario t ON t.id = u.tipo AND t.descripcion = 'MANICURISTA'
CROSS JOIN servicios s;

UPDATE turnos t JOIN servicios s ON s.id = t.id_servicio SET t.precio = s.precio WHERE t.precio IS NULL;

-- Ejemplos de diferencias: Lucia (id 2) cobra 10% más y Valeria (id 4) no hace esculpidas gel
UPDATE servicios_manicurista SET precio = ROUND(precio * 1.1, -2) WHERE id_usuario_manicurista = 2;
UPDATE servicios_manicurista SET activo = 0 WHERE id_usuario_manicurista = 4 AND id_servicio = 5;

-- Duración guardada en cada turno
UPDATE turnos t JOIN servicios s ON s.id = t.id_servicio SET t.duracion = s.duracion WHERE t.duracion IS NULL;

-- Ejemplo: Lucia (id 2) tarda 50 minutos en la manicura clásica y Sofia (id 5) 100 en el soft gel
UPDATE servicios_manicurista SET duracion = '00:50:00' WHERE id_usuario_manicurista = 2 AND id_servicio = 1;
UPDATE servicios_manicurista SET duracion = '01:40:00' WHERE id_usuario_manicurista = 5 AND id_servicio = 4;

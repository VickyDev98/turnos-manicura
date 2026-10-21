-- Dirección de atención de las manicuristas (se muestra en las tarjetas de turnos del cliente)
-- y turnos de prueba para los próximos 2 meses (clientes 6 y 7). Ejecutar una sola vez.
USE TURNOS;

ALTER TABLE usuarios ADD COLUMN direccion VARCHAR(100) AFTER email;

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
(1,6,2,TIMESTAMP(CURDATE() + INTERVAL 40 DAY,'15:00:00'),1),
(5,7,5,TIMESTAMP(CURDATE() + INTERVAL 8 DAY,'09:00:00'),1);

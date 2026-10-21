-- Actualiza una base TURNOS ya creada (sin borrar datos) para:
--   * login (usuarios.password)
--   * servicio en cada turno (turnos.id_servicio) para validar que no se pisen los horarios
-- Ejecutar una sola vez.
USE TURNOS;

ALTER TABLE usuarios ADD COLUMN password VARCHAR(100) AFTER email;

-- Contraseña de los usuarios existentes: 123456 (BCrypt)
UPDATE usuarios SET password = '$2a$10$fpHvu7aIB3jtxiwHBzpJIugsMOiDD.HhCpf7x45z4f1FiNLlGRvpC';

ALTER TABLE turnos ADD COLUMN id_servicio INT NULL AFTER id_usuario_cliente;

-- Los turnos que ya existían se asignan al servicio 1 (Manicura clasica)
UPDATE turnos SET id_servicio = 1;

ALTER TABLE turnos
    MODIFY id_servicio INT NOT NULL,
    ADD CONSTRAINT fkTurnoServicio FOREIGN KEY (id_servicio) REFERENCES servicios(id);

-- Turnos de HOY y de mañana para ver la agenda: rojo = ocupado, verde = libre
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

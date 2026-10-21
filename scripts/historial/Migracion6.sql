-- Duración propia por manicurista, baja/edición de usuarios y turnos que guardan su duración. Ejecutar una sola vez.
USE TURNOS;

-- Cada manicurista tarda lo que ella define en cada servicio (al principio, lo del catálogo)
ALTER TABLE servicios_manicurista ADD COLUMN duracion TIME NULL AFTER precio;
UPDATE servicios_manicurista sm JOIN servicios s ON s.id = sm.id_servicio SET sm.duracion = s.duracion;
ALTER TABLE servicios_manicurista MODIFY duracion TIME NOT NULL;

-- El turno guarda la duración con la que se reservó: cambiarla después no mueve los turnos viejos
ALTER TABLE turnos ADD COLUMN duracion TIME NULL AFTER precio;
UPDATE turnos t JOIN servicios s ON s.id = t.id_servicio SET t.duracion = s.duracion WHERE t.duracion IS NULL;

-- Usuarios dados de baja no pueden iniciar sesión ni recibir turnos
ALTER TABLE usuarios ADD COLUMN activo BOOLEAN NOT NULL DEFAULT 1;

-- Ejemplo: Lucia (id 2) tarda 50 minutos en la manicura clásica y Sofia (id 5) 100 en el soft gel
UPDATE servicios_manicurista SET duracion = '00:50:00' WHERE id_usuario_manicurista = 2 AND id_servicio = 1;
UPDATE servicios_manicurista SET duracion = '01:40:00' WHERE id_usuario_manicurista = 5 AND id_servicio = 4;

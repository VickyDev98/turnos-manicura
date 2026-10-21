-- Servicios por manicurista: cada una ofrece los servicios del catálogo con su propio precio.
-- El precio de la tabla "servicios" pasa a ser el precio sugerido al agregarlo. Ejecutar una sola vez.
USE TURNOS;

CREATE TABLE servicios_manicurista(
	id INT AUTO_INCREMENT,
	id_usuario_manicurista INT NOT NULL,
	id_servicio INT NOT NULL,
	precio DECIMAL(10,2) NOT NULL,
	activo BOOLEAN NOT NULL DEFAULT 1,
	PRIMARY KEY (id),
	UNIQUE KEY uqManicuristaServicio (id_usuario_manicurista, id_servicio),
	CONSTRAINT fkOfertaManicurista FOREIGN KEY (id_usuario_manicurista) REFERENCES usuarios(id),
	CONSTRAINT fkOfertaServicio FOREIGN KEY (id_servicio) REFERENCES servicios(id)
)ENGINE=InnoDB;

-- El turno guarda el precio con el que se reservó, así un cambio posterior no altera los turnos viejos
ALTER TABLE turnos ADD COLUMN precio DECIMAL(10,2) NULL AFTER id_servicio;

-- Al principio todas las manicuristas ofrecen todo el catálogo al precio del catálogo
INSERT INTO servicios_manicurista (id_usuario_manicurista, id_servicio, precio, activo)
SELECT u.id, s.id, s.precio, 1
FROM usuarios u
JOIN tiposUsuario t ON t.id = u.tipo AND t.descripcion = 'MANICURISTA'
CROSS JOIN servicios s;

UPDATE turnos t JOIN servicios s ON s.id = t.id_servicio SET t.precio = s.precio WHERE t.precio IS NULL;

-- Ejemplos de diferencias: Lucia (id 2) cobra 10% más y Valeria (id 4) no hace esculpidas gel
UPDATE servicios_manicurista SET precio = ROUND(precio * 1.1, -2) WHERE id_usuario_manicurista = 2;
UPDATE servicios_manicurista SET activo = 0 WHERE id_usuario_manicurista = 4 AND id_servicio = 5;

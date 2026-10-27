-- Esquema inicial. Flyway lo aplica una sola vez; una vez aplicado NO se edita:
-- cualquier cambio posterior va en un archivo nuevo (V2__..., V3__...).

CREATE TABLE tiposusuario(
	id INT AUTO_INCREMENT,
	descripcion VARCHAR(50),
	PRIMARY KEY (id)
)ENGINE=InnoDB;

CREATE TABLE usuarios(
	id INT AUTO_INCREMENT,
	tipo INT,
	nombre VARCHAR(50),
	apellido VARCHAR(50),
	numero_contacto INT,
	email VARCHAR(50),
	password VARCHAR(100),
	activo BOOLEAN NOT NULL DEFAULT 1,
	direccion VARCHAR(100),
	PRIMARY KEY (id),
	FOREIGN KEY (tipo) REFERENCES tiposusuario(id)
)ENGINE=InnoDB;

CREATE TABLE servicios(
	id INT AUTO_INCREMENT,
	nombre VARCHAR(50),
	descripcion VARCHAR(50),
	duracion time,
	precio DECIMAL(10,2),
	activo BOOLEAN,
	PRIMARY KEY (id)
)ENGINE=InnoDB;

CREATE TABLE turnos(
	id INT AUTO_INCREMENT,
	id_usuario_manicurista INT,
	id_usuario_cliente INT,
	id_servicio INT NOT NULL,
	precio DECIMAL(10,2),
	duracion TIME,
	dia_horario DATETIME,
	activo BOOLEAN,
	recordatorio_enviado BOOLEAN NOT NULL DEFAULT FALSE,
	no_asistio BOOLEAN NOT NULL DEFAULT FALSE,
	PRIMARY KEY(id),
	CONSTRAINT fkTurnoManicurista FOREIGN KEY(id_usuario_manicurista) REFERENCES usuarios(id),
	CONSTRAINT fkTurnoCliente FOREIGN KEY(id_usuario_cliente) REFERENCES usuarios(id),
	CONSTRAINT fkTurnoServicio FOREIGN KEY(id_servicio) REFERENCES servicios(id)
)ENGINE=InnoDB;

CREATE TABLE franjas_trabajo(
	id INT AUTO_INCREMENT,
	id_usuario_manicurista INT NOT NULL,
	dia_semana INT NOT NULL,
	hora_desde TIME NOT NULL,
	hora_hasta TIME NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT fkFranjaManicurista FOREIGN KEY (id_usuario_manicurista) REFERENCES usuarios(id)
)ENGINE=InnoDB;

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

CREATE TABLE servicios_manicurista(
	id INT AUTO_INCREMENT,
	id_usuario_manicurista INT NOT NULL,
	id_servicio INT NOT NULL,
	precio DECIMAL(10,2) NOT NULL,
	duracion TIME NOT NULL,
	activo BOOLEAN NOT NULL DEFAULT 1,
	PRIMARY KEY (id),
	UNIQUE KEY uqManicuristaServicio (id_usuario_manicurista, id_servicio),
	CONSTRAINT fkOfertaManicurista FOREIGN KEY (id_usuario_manicurista) REFERENCES usuarios(id),
	CONSTRAINT fkOfertaServicio FOREIGN KEY (id_servicio) REFERENCES servicios(id)
)ENGINE=InnoDB;

-- Datos de referencia: la app los necesita siempre (no son datos de prueba)
INSERT INTO tiposusuario (descripcion) VALUES
('CLIENTE'),
('MANICURISTA'),
('ADMIN');

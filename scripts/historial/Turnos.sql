CREATE DATABASE TURNOS;
 
 USE TURNOS;
 
 CREATE TABLE tipoUsuario(
	id INT AUTO_INCREMENT,
	descripcion VARCHAR(50),
	PRIMARY KEY (id)
	)ENGINE=InnoDB;
	
CREATE TABLE usuario(
	id INT AUTO_INCREMENT,
	nombre VARCHAR(50),
	tipo INT,
	PRIMARY KEY (id),
	FOREIGN KEY (tipo) REFERENCES tipoUsuario(id)
	)ENGINE=InnoDB;
	

CREATE TABLE servicios(
	id INT AUTO_INCREMENT,
	nombre VARCHAR(50),
	descripcion VARCHAR(50),
	precio DECIMAL(10,2),
	activo BOOLEAN,
	fechaCreacion DATE, 
	PRIMARY KEY (id)
)ENGINE=InnoDB; 
 
 CREATE TABLE clientes(
	id INT AUTO_INCREMENT,
	tipoUsuario INT,
	nombre VARCHAR(50),
	apellido VARCHAR(50),
	numeroContacto INT,
	email VARCHAR(50),
	PRIMARY KEY(id),
	CONSTRAINT fk_Clientes FOREIGN KEY (tipoUsuario) REFERENCES tipoUsuario(id)
	)ENGINE=InnoDB;
	
 CREATE TABLE manicurista(
	id INT AUTO_INCREMENT,
	tipoUsuario INT,
	nombre VARCHAR(50),
	apellido VARCHAR(50),
	numeroContacto INT,
	PRIMARY KEY(id),
	CONSTRAINT fkManicuristaUsuario FOREIGN KEY (tipoUsuario) REFERENCES tipoUsuario(id)
	)ENGINE=InnoDB;

CREATE TABLE turno(
	
 
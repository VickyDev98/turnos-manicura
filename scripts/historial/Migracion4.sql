-- Tipo de usuario ADMIN y un administrador de prueba. Ejecutar una sola vez.
-- El admin es el único que crea usuarios desde el sistema y ve todos los turnos.
USE TURNOS;

INSERT INTO tiposUsuario (descripcion) VALUES ('ADMIN');

-- Contraseña: 123456 (BCrypt)
INSERT INTO usuarios (tipo,nombre,apellido,numero_contacto,email,password)
SELECT id,'Admin','Sistema',1100000000,'admin@mail.com','$2a$10$fpHvu7aIB3jtxiwHBzpJIugsMOiDD.HhCpf7x45z4f1FiNLlGRvpC' FROM tiposUsuario WHERE descripcion = 'ADMIN';

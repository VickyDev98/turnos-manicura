-- Refresh tokens (tokens de renovación): con uno vigente se pide un access token nuevo sin volver a loguearse.
-- Se guarda el hash SHA-256 del token, nunca el token: quien lea la tabla no puede usarlos.
-- usado = TRUE: ya se cambió por uno nuevo (rotación). Si alguien vuelve a presentarlo, es una copia robada
-- y se borran todos los del usuario.
CREATE TABLE tokens_renovacion(
	id INT AUTO_INCREMENT,
	id_usuario INT NOT NULL,
	hash_token CHAR(64) NOT NULL,
	creado DATETIME NOT NULL,
	vence DATETIME NOT NULL,
	usado BOOLEAN NOT NULL DEFAULT FALSE,
	PRIMARY KEY (id),
	CONSTRAINT uqTokenRenovacionHash UNIQUE (hash_token),
	CONSTRAINT fkTokenRenovacionUsuario FOREIGN KEY (id_usuario) REFERENCES usuarios(id)
)ENGINE=InnoDB;

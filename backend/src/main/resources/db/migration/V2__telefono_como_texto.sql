-- El teléfono pasa de INT a texto: un INT no entra un celular con código de país (WhatsApp lo necesita)
-- y los números de más de 2147483647 (p. ej. 3515551234) no se podían guardar.
-- NULL = sin teléfono (antes se usaba 0).
ALTER TABLE usuarios MODIFY numero_contacto VARCHAR(20) NULL;

UPDATE usuarios SET numero_contacto = NULL WHERE numero_contacto = '0';

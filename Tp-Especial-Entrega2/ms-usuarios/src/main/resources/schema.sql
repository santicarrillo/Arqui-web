-- Entrega 2: usuarios CON credenciales. Guardamos el hash de la password (BCrypt), nunca texto plano.
CREATE TABLE IF NOT EXISTS usuario (
    id            BIGSERIAL    PRIMARY KEY,
    nombre        VARCHAR(200) NOT NULL,
    email         VARCHAR(200) NOT NULL UNIQUE,
    rol           VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL
);
-- Migracion por si la tabla ya existia de la Entrega 1 (se agrega como nullable).
-- Los usuarios viejos sin hash no podran loguear: recrealos con password.
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS password_hash VARCHAR(100);

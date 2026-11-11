-- Entrega 1: usuarios SIN autenticacion (el JWT y la password van en la Entrega 2).
-- Solo guardamos la identidad minima y el rol, para poder identificar por X-Usuario-Id.
CREATE TABLE IF NOT EXISTS usuario (
    id     BIGSERIAL    PRIMARY KEY,
    nombre VARCHAR(200) NOT NULL,
    email  VARCHAR(200) NOT NULL UNIQUE,
    rol    VARCHAR(20)  NOT NULL
);

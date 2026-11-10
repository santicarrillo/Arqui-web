-- lugares_db: esquema de lugares-service (PostgreSQL)

CREATE TABLE IF NOT EXISTS lugar (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(200)     NOT NULL,
    direccion   VARCHAR(250)     NOT NULL,
    latitud     DOUBLE PRECISION NOT NULL,
    longitud    DOUBLE PRECISION NOT NULL,
    ciudad      VARCHAR(120)     NOT NULL
    );

CREATE TABLE IF NOT EXISTS sector (
    id          BIGSERIAL PRIMARY KEY,
    lugar_id    BIGINT       NOT NULL REFERENCES lugar(id) ON DELETE CASCADE,
    nombre      VARCHAR(100) NOT NULL,
    numerado    BOOLEAN      NOT NULL DEFAULT FALSE,
    capacidad   INT          NOT NULL CHECK (capacidad >= 0)
    );

CREATE TABLE IF NOT EXISTS asiento (
    id          BIGSERIAL PRIMARY KEY,
    sector_id   BIGINT      NOT NULL REFERENCES sector(id) ON DELETE CASCADE,
    fila        VARCHAR(10) NOT NULL,
    numero      VARCHAR(10) NOT NULL,
    -- Restricción clave: en un mismo sector no puede haber dos asientos con la misma fila y número
    CONSTRAINT uq_asiento_sector_fila_numero UNIQUE (sector_id, fila, numero)
    );

-- Índices para que las consultas por id de lugar o de sector vuelen
CREATE INDEX IF NOT EXISTS idx_sector_lugar ON sector(lugar_id);
CREATE INDEX IF NOT EXISTS idx_asiento_sector ON asiento(sector_id);
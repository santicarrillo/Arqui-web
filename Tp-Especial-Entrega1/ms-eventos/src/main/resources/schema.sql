CREATE TABLE IF NOT EXISTS evento (
    id                 BIGSERIAL PRIMARY KEY,
    nombre             VARCHAR(200) NOT NULL,
    descripcion        TEXT,
    tipo               VARCHAR(20)  NOT NULL,
    fecha_hora         TIMESTAMP    NOT NULL,
    estado             VARCHAR(20)  NOT NULL DEFAULT 'BORRADOR',
    lugar_id           BIGINT       NOT NULL,
    ciudad             VARCHAR(120),
    organizador_id     BIGINT       NOT NULL,
    limite_por_cliente INT          NOT NULL DEFAULT 6,
    motivo_cancelacion TEXT
);

CREATE TABLE IF NOT EXISTS participante (
    id     BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(200) NOT NULL,
    tipo   VARCHAR(20)  NOT NULL
);

CREATE TABLE IF NOT EXISTS evento_participante (
    evento_id       BIGINT NOT NULL REFERENCES evento(id),
    participante_id BIGINT NOT NULL REFERENCES participante(id),
    PRIMARY KEY (evento_id, participante_id)
);

CREATE TABLE IF NOT EXISTS evento_sector (
    evento_id BIGINT        NOT NULL REFERENCES evento(id),
    sector_id BIGINT        NOT NULL,
    numerado  BOOLEAN       NOT NULL,
    precio    NUMERIC(12,2) NOT NULL,
    PRIMARY KEY (evento_id, sector_id)
);

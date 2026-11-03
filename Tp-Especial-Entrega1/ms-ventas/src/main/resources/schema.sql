-- ventas_db: esquema del ventas-service (PostgreSQL)
-- Los ids marcados "ref" apuntan a tablas de otros servicios: se guardan como numero, sin clave foranea.

CREATE TABLE IF NOT EXISTS compra (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cliente_id          BIGINT        NOT NULL,                       -- ref usuarios
    evento_id           BIGINT        NOT NULL,                       -- ref eventos
    evento_fecha_hora   TIMESTAMP     NOT NULL,                       -- copia, para la regla de 72 horas
    estado              VARCHAR(10)   NOT NULL
        CHECK (estado IN ('PENDIENTE', 'PAGADA', 'RECHAZADA', 'VENCIDA')),
    medio_pago          VARCHAR(20),
    total               NUMERIC(12,2) NOT NULL CHECK (total >= 0),
    clave_idempotencia  VARCHAR(100)  NOT NULL UNIQUE,
    fecha_compra        TIMESTAMP     NOT NULL,
    vence_en            TIMESTAMP     NOT NULL
);

CREATE INDEX IF NOT EXISTS compra_cliente_evento_idx ON compra (cliente_id, evento_id);
CREATE INDEX IF NOT EXISTS compra_pendientes_idx ON compra (vence_en) WHERE estado = 'PENDIENTE';

CREATE TABLE IF NOT EXISTS detalle_compra (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    compra_id        BIGINT        NOT NULL REFERENCES compra (id),
    sector_id        BIGINT        NOT NULL,                          -- ref lugares
    asiento_id       BIGINT,                                          -- ref lugares; nulo en zonas sin numerar
    precio_unitario  NUMERIC(12,2) NOT NULL CHECK (precio_unitario >= 0),
    codigo_qr        VARCHAR(80)   UNIQUE,                            -- nulo hasta que se paga
    estado           VARCHAR(10)   NOT NULL DEFAULT 'VALIDA'
        CHECK (estado IN ('VALIDA', 'USADA', 'DEVUELTA'))
);

CREATE INDEX IF NOT EXISTS detalle_compra_compra_idx ON detalle_compra (compra_id);

CREATE TABLE IF NOT EXISTS devolucion (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    detalle_compra_id BIGINT      NOT NULL REFERENCES detalle_compra (id),
    fecha_solicitud   TIMESTAMP   NOT NULL,
    estado            VARCHAR(10) NOT NULL
        CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA')),
    admin_id          BIGINT,                                         -- ref usuarios; nulo hasta resolver
    fecha_resolucion  TIMESTAMP,
    motivo            VARCHAR(20) NOT NULL
        CHECK (motivo IN ('SOLICITUD_CLIENTE', 'EVENTO_CANCELADO'))
);

-- Una entrada no puede tener dos devoluciones activas a la vez
CREATE UNIQUE INDEX IF NOT EXISTS devolucion_activa_uk
    ON devolucion (detalle_compra_id) WHERE estado IN ('PENDIENTE', 'APROBADA');

-- Un registro por asiento numerado de cada evento: aca se frena la doble venta
CREATE TABLE IF NOT EXISTS evento_asiento (
    evento_id   BIGINT      NOT NULL,                                 -- ref eventos
    asiento_id  BIGINT      NOT NULL,                                 -- ref lugares
    sector_id   BIGINT      NOT NULL,                                 -- ref lugares
    estado      VARCHAR(10) NOT NULL DEFAULT 'LIBRE'
        CHECK (estado IN ('LIBRE', 'RESERVADO', 'VENDIDO')),
    compra_id   BIGINT      REFERENCES compra (id),
    PRIMARY KEY (evento_id, asiento_id),
    CHECK ((estado = 'LIBRE') = (compra_id IS NULL))
);

CREATE INDEX IF NOT EXISTS evento_asiento_sector_idx ON evento_asiento (evento_id, sector_id, estado);
CREATE INDEX IF NOT EXISTS evento_asiento_compra_idx ON evento_asiento (compra_id);

-- Un contador por zona sin numerar de cada evento
CREATE TABLE IF NOT EXISTS cupo_sector (
    evento_id  BIGINT  NOT NULL,                                      -- ref eventos
    sector_id  BIGINT  NOT NULL,                                      -- ref lugares
    capacidad  INTEGER NOT NULL CHECK (capacidad > 0),                -- copia
    ocupados   INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (evento_id, sector_id),
    CHECK (ocupados >= 0 AND ocupados <= capacidad)
);

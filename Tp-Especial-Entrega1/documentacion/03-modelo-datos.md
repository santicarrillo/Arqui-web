# TicketYa — Modelo de datos (Entrega 1)

**Una base por microservicio** (esquemas separados). No hay claves foráneas entre servicios: los ids
de otras bases se guardan como número (marcados *ref*). Ver `diagramas/diagrama-er-*.svg`.

## usuarios_db (`ms-usuarios`)
- **usuario**(`id` PK, `nombre`, `email` UNIQUE, `rol`) — rol ∈ {CLIENTE, ORGANIZADOR, ADMINISTRADOR, ACCESO}.

## eventos_db (`ms-eventos`)
- **evento**(`id` PK, `nombre`, `descripcion`, `tipo`, `fecha_hora`, `estado`, `lugar_id`*, `ciudad` [copia], `organizador_id`*, `limite_por_cliente`, `motivo_cancelacion`)
  — estado ∈ {BORRADOR, PUBLICADO, AGOTADO, CANCELADO, FINALIZADO}.
- **participante**(`id` PK, `nombre`, `tipo`)
- **evento_participante**(`evento_id` FK, `participante_id` FK) — PK compuesta (N:M).
- **evento_sector**(`evento_id` FK, `sector_id`*, `numerado`, `precio`) — PK (evento, sector). Precio del sector para ese evento.

## lugares_db (`ms-lugares`)
- **lugar**(`id` PK, `nombre`, `direccion`, `latitud`, `longitud`, `ciudad`)
- **sector**(`id` PK, `lugar_id` FK, `nombre`, `numerado`, `capacidad`)
- **asiento**(`id` PK, `sector_id` FK, `fila`, `numero`) — UNIQUE(sector, fila, numero).

## ventas_db (`ms-ventas`)
- **compra**(`id` PK, `cliente_id`*, `evento_id`*, `evento_fecha_hora` [copia], `estado`, `medio_pago`, `total`, `clave_idempotencia` UNIQUE, `fecha_compra`, `vence_en`)
  — estado ∈ {PENDIENTE, PAGADA, RECHAZADA, VENCIDA}.
- **detalle_compra**(`id` PK, `compra_id` FK, `sector_id`*, `asiento_id`* [nulo en zonas], `precio_unitario`, `codigo_qr` UNIQUE [nulo hasta pagar], `estado`) — **cada fila es una entrada**.
- **devolucion**(`id` PK, `detalle_compra_id` FK, `fecha_solicitud`, `estado`, `admin_id`*, `fecha_resolucion`, `motivo`)
  — índice único parcial: una entrada no puede tener dos devoluciones activas.
- **evento_asiento**(`evento_id`*, `asiento_id`*, `sector_id`*, `estado`, `compra_id` FK) — **PK (evento, asiento)**. Acá se frena la doble venta (numerados).
- **cupo_sector**(`evento_id`*, `sector_id`*, `capacidad` [copia], `ocupados`) — PK (evento, sector). Contador para zonas.

## pagos_db (`ms-pagos`, JPA)
- **pago** y **reintegro** — registro del cobro simulado y su eventual reintegro.

## Entidades solapadas (referenciadas por id entre bases)
| Entidad (dueño) | La referencian |
|---|---|
| **usuario** (usuarios) | eventos (`organizador_id`); ventas (`compra.cliente_id`, `devolucion.admin_id`) |
| **evento** (eventos) | ventas (`compra`, `evento_asiento`, `cupo_sector`); `evento_fecha_hora` copiada en compra (regla 72 h) |
| **lugar** (lugares) | eventos (`evento.lugar_id`); `ciudad` copiada en evento (para la búsqueda) |
| **sector** (lugares) | eventos (`evento_sector`); ventas (`detalle_compra`, `evento_asiento`, `cupo_sector`) |
| **asiento** (lugares) | ventas (`detalle_compra`, `evento_asiento`) |

**Copias deliberadas** (desnormalización entre servicios): `ciudad` en evento, `evento_fecha_hora` en
compra, `capacidad` en cupo_sector, `numerado`/`precio` en evento_sector. Evitan llamadas en el
camino caliente.

# TicketYa — Modelo de datos (Entrega 1)

**Una base por microservicio** (esquemas separados). No hay claves foráneas entre servicios: los ids
de otras bases se guardan como número (marcados *ref*). Ver `diagramas/diagrama-er-*.svg`.

## usuarios_db (`ms-usuarios`)
- **usuario**(`id` PK, `nombre`, `email` UNIQUE, `rol`) — rol ∈ {CLIENTE, ORGANIZADOR, ADMINISTRADOR, ACCESO}.

## eventos (`ms-eventos`, MongoDB)
- Colección **eventos**: un documento por evento con `_id` (numérico), `nombre`, `descripcion`, `tipo`,
  `fechaHora`, `estado`, `lugarId`*, `ciudad` [copia], `organizadorId`*, `limitePorCliente`,
  `motivoCancelacion` y dos arreglos embebidos:
  - `sectores`: `{sectorId*, numerado, precio}` — precio del sector para ese evento (antes `evento_sector`).
  - `participantes`: `{nombre, tipo}` — artistas, equipos o disertantes (antes `participante` + `evento_participante`).
  — estado ∈ {BORRADOR, PUBLICADO, AGOTADO, CANCELADO, FINALIZADO}.
- Colección **contadores**: `{_id: "eventos", valor}` — genera los ids numéricos que usan los demás servicios.
- Detalle y justificación en [05-mongodb-eventos.md](05-mongodb-eventos.md).

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
| **sector** (lugares) | eventos (`sectores` del documento); ventas (`detalle_compra`, `evento_asiento`, `cupo_sector`) |
| **asiento** (lugares) | ventas (`detalle_compra`, `evento_asiento`) |

**Copias deliberadas** (desnormalización entre servicios): `ciudad` en evento, `evento_fecha_hora` en
compra, `capacidad` en cupo_sector, `numerado`/`precio` en los sectores del evento. Evitan llamadas en el
camino caliente.

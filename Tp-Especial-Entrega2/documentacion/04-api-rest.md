# TicketYa — Diseño de API REST (Entrega 1)

Todos los endpoints se acceden por el **gateway** (`http://localhost:8080`). El detalle completo con
esquemas y ejemplos vive en el **Swagger UI** de cada micro (`/swagger-ui.html`). La **colección de
Postman** (`TicketYa.postman_collection.json`) ejecuta el flujo completo.

> Identificación del usuario (E1, sin auth): headers **`X-Usuario-Id`** y, para administración, **`X-Rol`**.

## usuarios
| Método | Ruta | Body / params | Respuestas |
|---|---|---|---|
| POST | `/usuarios` | `{nombre, email, rol}` | 201 `{id}` · 400 · 409 (email dup) |
| GET | `/usuarios/{id}` | — | 200 · 404 |
| GET | `/usuarios` | `?rol=` | 200 |

## eventos
| Método | Ruta | Body / params | Respuestas |
|---|---|---|---|
| POST | `/eventos` | `{nombre, descripcion, tipo, fechaHora, lugarId, ciudad, organizadorId, limitePorCliente}` | 201 `{id}` · 400 |
| POST | `/eventos/{id}/sectores` | `[{sectorId, numerado, precio}]` | 200 · 404 · 409 |
| POST | `/eventos/{id}/participantes` | `[{nombre, tipo}]` | 200 · 404 |
| PUT | `/eventos/{id}/publicacion` | — | 200 · 404 · 409 (`SIN_SECTORES`) |
| PUT | `/eventos/{id}/cancelacion` | `{motivo}` | 200 · 404 · 409 |
| GET | `/eventos` | `?tipo=&organizadorId=&participante=&ciudad=&desde=&hasta=&orden=` | 200 |
| GET | `/eventos/{id}` | — | 200 (contrato que usa ventas) · 404 |

## lugares
| Método | Ruta | Body / params | Respuestas |
|---|---|---|---|
| POST | `/lugares` | `{nombre, direccion, latitud, longitud, ciudad}` | 201 `{id}` |
| GET | `/lugares/{id}` | — | 200 · 404 |
| POST | `/lugares/{id}/sectores` | `{nombre, numerado, capacidad?, totalFilas?, asientosPorFila?}` | 201 `{id}` |
| GET | `/lugares/{id}/sectores` | — | 200 |
| GET | `/sectores/{id}/asientos` | — | 200 |

## ventas (compra, entradas, devoluciones)
| Método | Ruta | Headers / body | Respuestas |
|---|---|---|---|
| POST | `/compras` | `X-Usuario-Id`, `Idempotency-Key` · `{eventoId, lugares:[{sectorId, asientoId?, cantidad?}]}` | 201 compra · 409 (sin disponibilidad / límite) |
| POST | `/compras/{id}/pagar` | `X-Usuario-Id` · `{medioPago}` | 200 (entradas con QR) · 402 (rechazado) · 409 |
| GET | `/compras/{id}` | `X-Usuario-Id` | 200 · 404 |
| DELETE | `/compras/{id}` | `X-Usuario-Id` | 204 |
| GET | `/disponibilidad` | `?eventoId=&sectorId=` | 200 · 404 |
| GET | `/entradas` | `X-Usuario-Id` | 200 |
| GET | `/entradas/{id}` | `X-Usuario-Id` | 200 (QR) · 404 |
| POST | `/entradas/{id}/devolucion` | `X-Usuario-Id` | 201 |
| PUT | `/entradas/{id}/devolucion` | `X-Usuario-Id`, `X-Rol` · `{aprobar}` | 200 |
| GET | `/devoluciones` | `X-Rol` | 200 |

## Ejemplo — crear y pagar una compra
```http
POST /compras            X-Usuario-Id: 2   Idempotency-Key: 9f1c-...
{ "eventoId": 1, "lugares": [ { "sectorId": 1, "asientoId": 5 } ] }
--> 201 { "compraId": 10, "estado": "PENDIENTE", "total": 15000.00, "venceEn": "..." }

POST /compras/10/pagar   X-Usuario-Id: 2
{ "medioPago": "TARJETA" }
--> 200 { "estado": "PAGADA", "detalle": [ { "entradaId": 20, "codigoQr": "QR-...", "precio": 15000.00 } ] }
```

## Errores
Formato uniforme: `{ "error": "<CODIGO>", "mensaje": "<texto>" }`. Códigos de ejemplo:
`DATOS_INVALIDOS`, `ROL_INVALIDO`, `EMAIL_DUPLICADO`, `EVENTO_NO_ENCONTRADO`, `SIN_SECTORES`,
`ESTADO_INVALIDO`, `DISPONIBILIDAD_NO_ENCONTRADA`, `PAGO_RECHAZADO`.

## OpenAPI
Cada micro genera su documento OpenAPI 3 con **springdoc**: `GET /v3/api-docs` (JSON) y la UI en
`/swagger-ui.html`. Es la documentación OpenAPI/Swagger de la entrega.

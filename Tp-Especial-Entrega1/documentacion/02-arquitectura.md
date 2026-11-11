# TicketYa — Arquitectura (Entrega 1)

## Estilo
Microservicios **Spring Boot** independientes, cada uno con su propia base de datos (esquema
separado), comunicados por **REST síncrono**. Un **API Gateway** es el único punto de entrada de los
clientes externos. Persistencia relacional (PostgreSQL) con **JDBC** (`JdbcTemplate`) en todos los
micros salvo `ms-pagos`, que usa JPA.

> La base NoSQL, la autenticación con JWT, la cache, la resiliencia y la bitácora centralizada son
> parte de la **Entrega 2**.

## Componentes, responsabilidades y API pública

| Componente | Puerto | Responsabilidad | API pública (vía gateway) |
|---|:--:|---|---|
| **api-gateway** | 8080 | Único punto de entrada; rutea a los micros | — |
| **ms-usuarios** | 8081 | Usuarios con rol (sin auth en E1) | `POST /usuarios`, `GET /usuarios/{id}`, `GET /usuarios` |
| **ms-eventos** | 8082 | Eventos, precios por sector, publicación, cancelación, búsqueda | `POST /eventos`, `POST /eventos/{id}/sectores`, `POST /eventos/{id}/participantes`, `PUT /eventos/{id}/publicacion`, `PUT /eventos/{id}/cancelacion`, `GET /eventos`, `GET /eventos/{id}` |
| **ms-lugares** | 8084 | Lugares, sectores (numerado/zona), asientos | `POST /lugares`, `GET /lugares/{id}`, `POST /lugares/{id}/sectores`, `GET /lugares/{id}/sectores`, `GET /sectores/{id}/asientos` |
| **ms-ventas** | 8083 | Compra, reserva, no-doble-venta, pago, entradas/QR, devoluciones | `POST /compras`, `POST /compras/{id}/pagar`, `GET /compras/{id}`, `DELETE /compras/{id}`, `GET /disponibilidad`, `GET /entradas`, `GET /entradas/{id}`, `POST /entradas/{id}/devolucion`, `PUT /entradas/{id}/devolucion`, `GET /devoluciones` |
| **ms-pagos** | 8085 | Pasarela de pagos simulada (cobro/reintegro). Proyecto Maven independiente, Java 17 | interno (lo llama ventas) |

## Diagrama de módulos
Ver `diagramas/diagrama-modulos.svg`. El gateway enruta por path (`/usuarios`, `/eventos`, `/compras`,
`/entradas`, `/disponibilidad`, `/devoluciones`, `/lugares`, `/sectores`) hacia el micro dueño de
cada recurso.

## Llamadas entre servicios
- **ventas → eventos** (`GET /eventos/{id}`): obtiene estado, fecha, límite por cliente y precio de cada sector.
- **ventas → lugares**: obtiene los asientos de un sector numerado (para crear la disponibilidad).
- **ventas → pagos**: cobra y pide reintegros.
- La identidad del usuario viaja como header `X-Usuario-Id` (y `X-Rol`), no se valida contra usuarios en E1.

## Diagrama de secuencia — flujo de compra de una entrada numerada
Ver `diagramas/diagrama-secuencia.svg`. Resumen:
1. `POST /compras`: valida el límite por cliente, crea la compra PENDIENTE (con vencimiento) y
   **reserva** cada lugar (LIBRE→RESERVADO de forma atómica). Si algún lugar ya no está libre →
   rollback y **409**. Idempotente por `Idempotency-Key`.
2. `POST /compras/{id}/pagar`: llama a pagos.
   - **Aprobado:** asientos→VENDIDO, compra→PAGADA, cada entrada recibe su QR → 200.
   - **Rechazado:** libera los lugares, compra→RECHAZADA → **402**.
   - **Reserva vencida:** una compra PENDIENTE vencida no se puede pagar → 409 *(el scheduler de
     vencimiento que libera automáticamente es E2)*.

Ninguna transacción abarca ventas y pagos a la vez: la consistencia se sostiene por compensaciones
(liberar al rechazar), tendiendo a **consistencia eventual** (se profundiza en la Entrega 2).

## Nota de integración pendiente (walking skeleton)
`ms-ventas` inicializa la disponibilidad de un evento vía `POST /interno/disponibilidad` (crea un
registro por asiento numerado y el cupo de cada zona, tomando los asientos de lugares). En el estado
actual **ese llamado no está disparado automáticamente por la publicación del evento**: para el flujo
de prueba se invoca de forma explícita (ver la colección de Postman). La integración automática
(que la publicación de eventos lo dispare, o que ventas lo resuelva de forma perezosa) queda como
punto de coordinación del equipo.

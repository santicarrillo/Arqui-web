# TicketYa — Features y User Stories (Entrega 1)

Historias de todo el sistema con sus criterios de aceptación. Las de la **Entrega 1** están
implementadas; las marcadas *(E2)* quedan para la segunda entrega. Los criterios reflejan el
comportamiento real del código (códigos HTTP incluidos).

> **Seguridad (E1):** sin autenticación. El usuario se identifica por el encabezado `X-Usuario-Id`
> (y `X-Rol` para acciones de administración). JWT y roles reales → Entrega 2.

## Feature U — Gestión de usuarios (`ms-usuarios`)

**US-U1 — Alta de usuario con rol.** *Como administrador, quiero registrar usuarios con su rol para
poder identificarlos en el sistema.*
- `POST /usuarios` con nombre, email y rol → **201** con el id.
- Rol válido: CLIENTE, ORGANIZADOR, ADMINISTRADOR, ACCESO; otro valor → **400**.
- Email repetido → **409**.

**US-U2 — Consulta de usuarios.** *Como sistema, quiero consultar usuarios por id o por rol.*
- `GET /usuarios/{id}` → 200 / **404** si no existe. `GET /usuarios?rol=` lista (filtra por rol).

## Feature A — Gestión y publicación de eventos (`ms-eventos`)

**US-01 — Crear evento en borrador.** *Como organizador, quiero crear un evento y dejarlo en
borrador para prepararlo antes de publicarlo.*
- `POST /eventos` → **201**; nace en estado **BORRADOR**. Faltan datos obligatorios → **400**.

**US-02 — Configurar sectores y precios.** *Como organizador, quiero fijar el precio de cada sector.*
- `POST /eventos/{id}/sectores` guarda `(sector, numerado, precio)`. Solo en BORRADOR (si no, **409**).

**US-03 — Publicar un evento.** *Como organizador, quiero publicar el evento para que sea visible y
comprable.*
- `PUT /eventos/{id}/publicacion` → pasa a **PUBLICADO**. Sin al menos un sector con precio → **409**
  (`SIN_SECTORES`). Un evento que no está en BORRADOR no se re-publica → **409**.

**US-04 — Cancelar un evento.** *Como administrador, quiero cancelar un evento.*
- `PUT /eventos/{id}/cancelacion` con motivo → **CANCELADO**. Ya cancelado → **409**. Al cancelar, las
  entradas vendidas pasan a reintegro (ver US-14).

**US-05 — Buscar eventos por múltiples criterios.** *Como cliente, quiero buscar eventos.*
- `GET /eventos?tipo=&organizadorId=&participante=&ciudad=&desde=&hasta=&orden=` → lista de eventos
  **PUBLICADOS**, ordenable por fecha o precio.

## Feature L — Catálogo de lugares (`ms-lugares`)

**US-L1 — Cargar lugares, sectores y asientos.** *Como administrador, quiero registrar lugares con sus
sectores (numerados o zona) y asientos.*
- `POST /lugares`, `POST /lugares/{id}/sectores` (genera los asientos de los sectores numerados),
  `GET /lugares/{id}/sectores`, `GET /sectores/{id}/asientos`.

## Feature C — Compra de entradas (`ms-ventas` + `ms-pagos`)

**US-06 — Crear la compra reservando los lugares.** *Como cliente, quiero reservar temporalmente los
lugares mientras pago.*
- `POST /compras` (header `X-Usuario-Id`, `Idempotency-Key`) crea la compra **PENDIENTE** y reserva:
  asiento LIBRE→RESERVADO (numerado) o cupo ocupado+=cantidad (zona), de forma atómica. La compra
  puede mezclar varios sectores. Respeta el límite por cliente del evento (**409** si lo supera).

**US-07 — Pagar y recibir las entradas con QR.** *Como cliente, quiero pagar y recibir mis entradas.*
- `POST /compras/{id}/pagar` llama a pagos. Si aprueba: asientos→VENDIDO, compra→**PAGADA**, cada
  entrada recibe su **código QR**. Si rechaza: libera los lugares, compra→RECHAZADA → **402**.

**US-08 — Nunca vender dos veces el mismo lugar.** *Como sistema, quiero garantizar la no-doble-venta
aun con compras concurrentes.*
- La reserva es un `UPDATE ... WHERE estado='LIBRE'` condicional (y PK compuesta por asiento): si dos
  compras pelean el mismo asiento, solo una gana; la otra recibe **409**. *(Verificado por la prueba de
  concurrencia de ventas.)*

**US-09 — Idempotencia.** *Como cliente, quiero que un reintento no cobre ni reserve dos veces.*
- La misma `Idempotency-Key` devuelve la compra existente, sin reservar de nuevo.

**US-11 — Límite de entradas por cliente y evento.** — ver US-06 (supera el límite → **409**).

## Feature D — Devoluciones y cancelación (`ms-ventas`)

**US-12 — Solicitar la devolución de una entrada.** *Como cliente, quiero pedir la devolución.*
- `POST /entradas/{id}/devolucion` (header `X-Usuario-Id`) crea la solicitud PENDIENTE.

**US-13 — Resolver una devolución.** *Como administrador, quiero aprobar o rechazar una devolución.*
- `PUT /entradas/{id}/devolucion` (headers `X-Usuario-Id`, `X-Rol`) con `{aprobar}`. Si aprueba, el
  lugar se libera. `GET /devoluciones` lista las solicitudes (admin).

**US-14 — Reintegro por cancelación de evento.** *Al cancelar un evento, sus entradas vendidas pasan a
reintegro.* — disparado por US-04.

## Historias de la Entrega 2 *(no implementadas)*
- **US-10** Liberar reservas vencidas (scheduler de vencimiento).
- Fila virtual (alta demanda), control de acceso por QR, preferencias/recomendaciones, notificaciones,
  reportes para organizadores. Seguridad con JWT y roles. Base NoSQL. Cache, resiliencia, bitácora.

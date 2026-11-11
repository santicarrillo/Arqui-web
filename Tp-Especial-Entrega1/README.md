# TicketYa — Entrega 1 (Core del sistema)

Plataforma de venta de entradas para eventos, construida como **microservicios Spring Boot**
con un **API Gateway** como único punto de entrada. Esta entrega cubre el núcleo del sistema:
publicación de eventos y lugares, búsqueda por múltiples criterios, y el circuito de compra con
reserva temporal, emisión de entradas con QR, garantía de no-doble-venta, devoluciones y
cancelación de eventos.

> **Seguridad (Entrega 1):** el sistema corre **sin autenticación**. El usuario se identifica por
> el encabezado **`X-Usuario-Id`** (y, para acciones de administración, `X-Rol`). El JWT y la
> autorización por roles son parte de la Entrega 2.

## Microservicios y puertos

| Servicio      | Puerto | Base de datos (puerto host) | Responsabilidad |
|---------------|:------:|:---------------------------:|-----------------|
| api-gateway   | 8080   | —                           | Único punto de entrada; rutea a los micros |
| ms-usuarios   | 8081   | usuarios (5437)             | Alta y consulta de usuarios con rol |
| ms-eventos    | 8082   | eventos (5434)              | Eventos, precios por sector, publicación, cancelación, búsqueda |
| ms-ventas     | 8083   | ventas (5433)               | Compra, reserva, no-doble-venta, pago, entradas/QR, devoluciones |
| ms-lugares    | 8084   | lugares (5435)              | Lugares, sectores (numerado/zona) y asientos |
| ms-pagos      | 8085   | pagos_db (5436)             | Pasarela de pagos simulada (cobro / reintegro) |

## Requisitos

- **Java 21** (Temurin recomendado) para todos los micros **excepto ms-pagos**.
- **Docker Desktop**.
- **Maven** (incluido en IntelliJ).

> **ms-pagos es un proyecto Maven independiente** (parent propio, **Java 17**, groupId `com.ticketya`).
> No cuelga del `pom.xml` padre: se abre, compila y corre por separado.

## 1. Levantar las bases de datos (Docker)

Desde esta carpeta (`Tp-Especial-Entrega1`):

```bash
docker compose up -d      # levanta las 5 bases
docker compose down       # las baja
```

Cada base usa el nombre, usuario, clave y puerto que espera el `application.yml` de su micro, así
que los micros conectan con sus valores por defecto, sin configurar nada.

## 2. Correr los microservicios

Los micros se ejecutan **localmente** desde el IDE (la Entrega 1 pide ejecución local de los
micros, con las bases en Docker). Abrí el proyecto en IntelliJ, hacé **Maven Reload** y corré:

- `UsuariosApplication`, `EventosApplication`, `VentasApplication`, `LugaresApplication` y
  `ApiGatewayApplication` (reactor Maven, Java 21).
- `ms-pagos`: abrilo/compilalo aparte (Java 17) y corré su clase principal.

Con todo levantado, entrá por el gateway: **http://localhost:8080**.

> **Docker Engine 29+ y los tests de integración:** si tenés Docker Desktop con engine 29 o mayor
> y corrés los tests de integración (Testcontainers), agregá la VM option **`-Dapi.version=1.44`**
> (el engine nuevo exige API ≥ 1.40 y docker-java por defecto pide una más vieja). Sin Docker, esos
> tests se saltean solos y no rompen la suite.

## 3. Documentación de la API (Swagger)

Cada micro publica su OpenAPI/Swagger UI:

- http://localhost:8081/swagger-ui.html — usuarios
- http://localhost:8082/swagger-ui.html — eventos
- http://localhost:8083/swagger-ui.html — ventas
- http://localhost:8084/swagger-ui.html — lugares
- http://localhost:8085/swagger-ui.html — pagos

El detalle de endpoints, parámetros y ejemplos está en `documentacion/04-api-rest.md` y en la colección de
Postman (`documentacion/TicketYa.postman_collection.json`).

## 4. Flujo de prueba de punta a punta (vía gateway)

1. **Usuarios** — `POST /usuarios` un ORGANIZADOR y un CLIENTE (y opcional un ADMINISTRADOR).
2. **Lugar** — `POST /lugares`, luego `POST /lugares/{id}/sectores` (crea sectores y sus asientos).
   Mirá los `sectorId` con `GET /lugares/{id}/sectores`.
3. **Evento** — `POST /eventos` (con `organizadorId` y `lugarId`), `POST /eventos/{id}/sectores`
   (precio por sector), `PUT /eventos/{id}/publicacion`.
4. **Búsqueda** — `GET /eventos?tipo=...&ciudad=...&desde=...&hasta=...&orden=fecha`.
5. **Compra** — `POST /compras` con headers `X-Usuario-Id: <cliente>` e `Idempotency-Key: <uuid>`
   (crea la compra PENDIENTE y reserva los lugares).
6. **Pago** — `POST /compras/{id}/pagar` con `X-Usuario-Id` (si aprueba, emite las entradas con QR).
7. **Entradas** — `GET /entradas` y `GET /entradas/{id}` (con `X-Usuario-Id`).
8. **Devolución** — `POST /entradas/{id}/devolucion` (cliente); `PUT /entradas/{id}/devolucion`
   (admin, con `X-Rol: ADMINISTRADOR`); `GET /devoluciones` (admin).

La colección de Postman en `documentacion/` ejecuta este flujo completo y sirve también como **datos de
prueba** (va creando los usuarios, el lugar, el evento, etc.).

## 5. Testing

- **Pruebas unitarias** de la lógica de negocio en cada micro (compra, devoluciones, búsqueda,
  alta de usuarios, estados de evento).
- **Prueba de no-doble-venta** (ms-ventas): simula muchas compras simultáneas del mismo asiento y
  verifica que **solo una** resulta exitosa.

## 6. Estructura del repositorio

```
Tp-Especial-Entrega1/
├── docker-compose.yml        # las 5 bases del sistema
├── pom.xml                   # reactor Maven (todos menos ms-pagos)
├── api-gateway/              # punto de entrada (8080)
├── ms-usuarios/              # 8081
├── ms-eventos/               # 8082
├── ms-ventas/                # 8083
├── ms-lugares/               # 8084
├── ms-pagos/                 # 8085 (proyecto independiente, Java 17)
└── documentacion/                     # documentación de diseño + colección Postman
    ├── 01-user-stories.md
    ├── 02-arquitectura.md
    ├── 03-modelo-datos.md
    ├── 04-api-rest.md
    ├── diagramas/
    └── TicketYa.postman_collection.json
```

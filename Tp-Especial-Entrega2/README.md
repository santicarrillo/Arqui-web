# TicketYa — Entrega 2 (Sistema completo)

Plataforma de venta de entradas para eventos, construida como **microservicios Spring Boot**
con un **API Gateway** como único punto de entrada. Esta entrega completa la funcionalidad del
sistema y los aspectos no funcionales: autenticación con JWT, base NoSQL, consistencia entre
servicios con compensaciones (sagas), resiliencia, caché, trazabilidad con log centralizado,
comunicación asíncrona y despliegue completo con Docker.

> **Seguridad (Entrega 2):** el sistema usa **autenticación y autorización con JWT**. El usuario se
> loguea en `ms-usuarios`, obtiene un token firmado, y lo envía en `Authorization: Bearer <token>`.
> El **API Gateway valida el token** e inyecta la identidad (`X-Usuario-Id` / `X-Rol`) hacia los
> microservicios. Los roles son: `CLIENTE`, `ORGANIZADOR`, `ADMINISTRADOR` y `ACCESO`.

## Microservicios y puertos

| Servicio           | Puerto | Base de datos (puerto host)      | Responsabilidad |
|--------------------|:------:|:--------------------------------:|-----------------|
| api-gateway        | 8080   | —                                | Único punto de entrada; valida JWT, propaga identidad y correlation-id, bitácora |
| ms-usuarios        | 8081   | PostgreSQL `usuarios` (5437)     | Alta de usuarios, login y emisión del JWT |
| ms-eventos         | 8082   | **MongoDB** (27017)              | Eventos, precios por sector, publicación/cancelación (sagas), búsqueda, caché |
| ms-ventas          | 8083   | PostgreSQL `ventas` (5433)       | Compra, reserva, no-doble-venta, pago, entradas/QR, devoluciones, ingresos, reportes, fila virtual |
| ms-lugares         | 8084   | PostgreSQL `lugares` (5435)      | Lugares, sectores (numerado/zona) y asientos |
| ms-pagos           | 8085   | PostgreSQL `pagos_db` (5436)     | Pasarela de pagos simulada (cobro / reintegro) |
| ms-preferencias    | 8086   | **MongoDB** (27017)              | Preferencias de clientes y recomendaciones |
| ms-notificaciones  | 8087   | **MongoDB** (27017)              | Notificaciones asíncronas (compra, cancelación, artista favorito) |

> MongoDB es un único servidor (contenedor `eventos-db`) con tres bases: `eventos`, `preferencias` y `notificaciones`.

## Novedades de la Entrega 2

- **Seguridad JWT + roles**: login en `ms-usuarios`, validación centralizada en el gateway.
- **Base NoSQL (MongoDB)**: catálogo de eventos (`ms-eventos`) y datos de preferencias/notificaciones. Justificación en `documentacion/05-mongodb-eventos.md`.
- **Consistencia entre servicios (sagas)**: publicación (consistencia fuerte) y cancelación (eventual con reintento y compensaciones). Detalle en `documentacion/06-entrega2-fase1.md`.
- **Resiliencia (Resilience4j)**: timeouts, reintentos y circuit breaker + fallback en las llamadas a `ms-pagos`.
- **Caché**: detalle y búsqueda de eventos (no se cachea la disponibilidad de asientos).
- **Trazabilidad / log centralizado**: cada request lleva un `X-Correlation-Id` que el gateway genera y propaga; cada micro lo registra en sus logs, de modo que una misma operación se sigue a través de todos los servicios. El gateway registra endpoint, usuario, código de respuesta y tiempo.
- **Comunicación asíncrona**: notificaciones (fire-and-forget).
- **Funcionalidad**: fila virtual (6), control de acceso por QR (8), preferencias y recomendaciones (10), notificaciones (11), reportes del organizador (12).

## Requisitos

- **Java 21** (Temurin recomendado) para todos los micros **excepto ms-pagos** (que usa **Java 17** y su propio parent).
- **Docker Desktop**.
- **Maven** (incluido en IntelliJ).

## Cómo ejecutar

### Opción A — Todo en Docker (recomendada para la entrega)

El sistema completo (bases + micros + gateway) se levanta con un único `docker-compose.yml`.
Los Dockerfile parten de los jars ya compilados, así que primero se compilan una vez:

```bash
# 1. Compilar los jars de los 8 módulos (desde esta carpeta)
mvn clean package -DskipTests

# 2. Construir las imágenes y levantar todo
docker compose up -d --build

# 3. Ver el estado (deben quedar 13 contenedores Up: 5 bases + 8 micros)
docker compose ps

# Para bajar todo:
docker compose down
```

Punto de entrada: **http://localhost:8080** (api-gateway).

### Opción B — Micros locales, bases en Docker

```bash
# 1. Levantar solo las bases
docker compose up -d usuarios-db eventos-db ventas-db lugares-db pagos-db

# 2. Compilar y correr cada micro desde el IDE:
#    UsuariosApplication, LugaresApp, EventosApplication, MsPagosApplication,
#    VentasApplication, PreferenciasApplication, NotificacionesApplication, GatewayApp
```

> **ms-pagos** usa Java 17 y su propio parent. Compila en el reactor junto al resto; si se corre
> por separado, abrilo con un JDK 17+.

## Seguridad: cómo autenticarse

1. **Crear usuario** (público) — ahora requiere `password`:
   ```
   POST /usuarios
   { "nombre": "Ana", "email": "ana@a.com", "rol": "CLIENTE", "password": "1234" }
   ```
2. **Login** (público) — devuelve el token:
   ```
   POST /usuarios/login
   { "email": "ana@a.com", "password": "1234" }
   → { "token": "...", "tipo": "Bearer", "usuarioId": 1, "rol": "CLIENTE", "expiraEnSegundos": 3600 }
   ```
3. **Usar el token** en todo lo demás: header `Authorization: Bearer <token>`.
   El gateway valida la firma e inyecta `X-Usuario-Id` / `X-Rol` a los micros (que ya no aceptan
   esos headers desde afuera: se limpian para evitar spoofing).

Rutas públicas: `POST /usuarios/login` y `POST /usuarios` (registro). El resto exige token válido.

## Documentación de la API (Swagger)

Cada micro publica su OpenAPI/Swagger UI:

- http://localhost:8081/swagger-ui.html — usuarios
- http://localhost:8082/swagger-ui.html — eventos
- http://localhost:8083/swagger-ui.html — ventas
- http://localhost:8084/swagger-ui.html — lugares
- http://localhost:8085/swagger-ui.html — pagos
- http://localhost:8086/swagger-ui.html — preferencias
- http://localhost:8087/swagger-ui.html — notificaciones

El detalle de endpoints está en `documentacion/04-api-rest.md`.

## Colección de pruebas (Postman)

`documentacion/TicketYa-E2.postman_collection.json` — colección actualizada para la Entrega 2
(con login/JWT). Importar en Postman y correr de arriba a abajo (o "Run collection"): los logins
guardan los tokens y los IDs se capturan solos. Todo apunta a `{{baseUrl}} = http://localhost:8080`.

Cubre: autenticación (incluido 401 sin token), lugares, eventos + saga de publicación, compra,
pago (ventas→pagos→QR), control de acceso por QR, devoluciones, reportes, preferencias y notificaciones.

## Trazabilidad (correlation-id)

Cada request recibe un `X-Correlation-Id` (lo genera el gateway si no viene, y lo devuelve al cliente).
Viaja a todos los micros involucrados y aparece en sus logs con el prefijo `[corr=...]`. Para seguir
una operación de punta a punta, buscar el mismo id en las consolas (o `docker compose logs`):

```
api-gateway  : [bfe90fef] IN  POST /compras/3/pagar (usuario=3)
ventas       : [corr=bfe90fef] POST /compras/3/pagar -> 200
pagos        : [corr=bfe90fef] POST /pagos -> 200
```

## Documentación de diseño

En `documentacion/`:

- `01-user-stories.md` — features y user stories
- `02-arquitectura.md` — arquitectura y diagramas de módulos / secuencia
- `03-modelo-datos.md` — modelo de datos por microservicio
- `04-api-rest.md` — endpoints, parámetros y ejemplos
- `05-mongodb-eventos.md` — elección y modelado NoSQL
- `06-entrega2-fase1.md` — decisiones de diseño de la Entrega 2 (sagas, resiliencia, QR, reportes)
- `07-pruebas-seguridad.md` — pruebas de seguridad por rol
- `diagramas/` — diagramas (SVG)

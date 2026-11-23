# Interfaz web del cliente (GUI)

Web en **React 19 + Vite** (`frontend/`) con el recorrido completo del cliente. Es una aplicación de una
sola página que corre en el navegador y le habla **solo al API Gateway**: para la web, el sistema es una
única API en `http://localhost:8080`, igual que para Postman.

## Pantallas y endpoints que usa

| Pantalla | Qué hace el cliente | Endpoints (todos por el gateway) |
|---|---|---|
| Iniciar sesión | Entra con email y contraseña | `POST /usuarios/login`, `GET /usuarios/{id}` (para mostrar el nombre) |
| Crear cuenta | Se registra como CLIENTE y entra | `POST /usuarios` con `rol: CLIENTE`, después el login |
| Eventos | Busca por ciudad, tipo, artista y fecha | `GET /eventos?ciudad&tipo&participante&desde` |
| Detalle del evento | Ve precios y lugares libres; elige asientos en el plano o cantidad en las zonas | `GET /eventos/{id}`, `GET /lugares/{id}`, `GET /lugares/{id}/sectores`, `GET /sectores/{id}/asientos`, `GET /disponibilidad?eventoId&sectorId`, `GET /filas/{eventoId}` |
| Fila virtual (dentro del detalle) | Saca turno y espera a que se habilite | `POST /filas/{eventoId}/turno`, `GET /filas/{eventoId}/turno` cada 3 s |
| Compra | Reserva, cuenta regresiva de 10 minutos, elige medio de pago, paga o cancela | `POST /compras` (con `Idempotency-Key`), `GET /compras/{id}`, `POST /compras/{id}/pagar`, `DELETE /compras/{id}` |
| Mis entradas | Ve cada entrada con su QR y pide devoluciones | `GET /entradas`, `POST /entradas/{id}/devolucion` |
| Devoluciones (solo ADMINISTRADOR) | Ve los pedidos pendientes con evento, cliente y monto; los aprueba o rechaza | `GET /devoluciones?estado=PENDIENTE`, `GET /usuarios/{id}`, `GET /eventos/{id}`, `PUT /entradas/{id}/devolucion` |

## Decisiones

**CORS en el gateway.** La web se sirve desde otro origen (`localhost:5173` en desarrollo, `localhost:3000`
en Docker), así que el navegador bloquea sus llamadas salvo que el gateway las autorice. Se configuró una
sola vez en el gateway (`spring.cloud.gateway.globalcors` en su `application.yml`), no en cada micro:
- el *preflight* `OPTIONS` lo contesta el gateway, sin pedir token;
- las respuestas llevan `Access-Control-Allow-Origin`, **incluidos los 401**: si no, el navegador escondería el
  401 y la web no sabría que la sesión venció;
- se exponen `X-Correlation-Id` y `X-Auth-Error` para que el front pueda leerlos;
- los orígenes se cambian con la variable `CORS_ORIGINS`. Un origen no autorizado recibe 403 (`CorsTest`).

**Sesión con JWT.** El token de `/usuarios/login` se guarda en `localStorage` junto con su vencimiento, y va
en `Authorization: Bearer` en cada llamada. Si el gateway responde 401 con una sesión abierta (token vencido
o inválido), la web cierra la sesión y vuelve al login con el aviso "Tu sesión venció". La web no decide
permisos: los controla el backend, y la web solo muestra el mensaje de error que recibe.

**Cambio en el backend: `GET /entradas` avisa si hay una devolución en curso.** La entrada sigue VALIDA hasta
que un administrador aprueba la devolución, así que con solo el estado la web no sabía que ya se había pedido y,
al recargar, volvía a ofrecer el botón. Se agregó **al final** el campo `devolucion` (`PENDIENTE`, `APROBADA` o
`null`) y "Mis entradas" muestra "Pediste la devolución" en vez del botón.

**Cambio en el backend: `GET /devoluciones` trae evento, cliente y monto.** Antes devolvía solo el id de la
entrada, y con eso el administrador no sabía qué estaba aprobando. Se agregaron **al final** `eventoId`,
`clienteId` y `monto` (el precio de la entrada, que es lo que se reintegra).

**Errores del backend tal cual.** Todos los micros responden los errores como `{error, mensaje}`. La web
muestra el `mensaje` y usa el `error` para decidir qué hacer: con `TURNO_*` muestra la fila virtual, con un
409 de lugares (alguien compró antes) recarga la disponibilidad y saca de tu pedido los asientos que ya no
están libres.

**Compra idempotente.** Cada pedido distinto genera una `Idempotency-Key` nueva; reintentar el mismo pedido
(por ejemplo, después de un corte de red) usa la misma clave, así ventas no crea dos compras.

**Fila virtual.** Si `GET /filas/{evento}` dice que la fila está activa, el botón de comprar se habilita
recién cuando el turno está habilitado. Mientras el cliente espera, la web consulta su turno cada 3 segundos
(el backend avanza la fila cada 5).

**Datos que casi no cambian, en memoria.** Evento, lugar, nombres de sectores y filas/números de asientos se
piden una vez y se guardan mientras la página está abierta (`catalogo.js`): "Mis entradas" muestra muchas
entradas del mismo evento y no tiene sentido pedirlos para cada una. La **disponibilidad no se guarda**:
se pide cada vez, por el mismo motivo por el que el backend no la cachea.

**Cambio en el backend: `GET /eventos/{id}` trae más datos.** Para mostrar el nombre del evento, el lugar y
los nombres de los sectores, la web necesitaba `nombre` y `lugarId`, que ese endpoint no devolvía. Se
agregaron **al final** `nombre`, `descripcion`, `tipo`, `ciudad`, `lugarId` y `participantes`. Es compatible
con ventas, que lee el mismo endpoint: su DTO declara solo los campos que usa y Jackson ignora el resto. La
respuesta sigue cacheada (F5) y se invalida igual.

## Diseño visual

Tema oscuro tipo **noche de recital**: fondo casi negro, texto claro y un **magenta de luces de escenario** solo
para lo que elige el cliente: sus asientos en el plano, el botón de comprar, el turno habilitado y el talón de la
entrada. El QR del talón se dibuja oscuro sobre el magenta para que los lectores de la puerta lo lean bien.
En las pantallas de iniciar sesión y crear cuenta, detrás del título hay una escena de recital animada (luces
que barren el escenario y el público en silueta con celulares prendidos), hecha con SVG y CSS en
`componentes/Escenario.jsx`, sin imágenes externas.
Las entradas en "Mis entradas" tienen forma de entrada de papel: los datos de un lado y, del otro lado del
troquel, el QR que se muestra en la puerta. Si la entrada ya se usó o se devolvió, lleva un sello y el QR
queda apagado. Tipografías: Big Shoulders Display para títulos y Barlow para el texto, incluidas en el
proyecto (`@fontsource`), así la web funciona sin internet.

Se adapta al celular (las entradas pasan a vertical, con el QR abajo), tiene foco visible para teclado y
respeta "reducir movimiento".

## Límites conocidos

- La web es para clientes, más la pantalla de devoluciones del administrador. Con los otros roles se puede
  entrar, pero muestra un aviso; las pantallas de organizador y personal de acceso quedan para Postman/Swagger.
- El token se guarda en `localStorage`; en producción se preferiría una cookie `HttpOnly` para que un script
  inyectado no pueda leerlo.

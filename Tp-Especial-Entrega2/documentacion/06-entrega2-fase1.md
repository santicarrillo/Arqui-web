# Entrega 2 — Fase 1: decisiones de diseño

Qué se hizo en cada ítem de la Fase 1, cómo funciona y por qué se eligió así. Lo de MongoDB en eventos (F4)
está en [05-mongodb-eventos.md](05-mongodb-eventos.md).

## F6 · Control de acceso con QR (funcionalidad 8)

**Endpoint:** `POST /ingresos` en ventas, con `{eventoId, codigoQr}`. Lo usa el personal de acceso (rol
`ACCESO`; también puede un `ADMINISTRADOR`). Pasa por el gateway.

**Cómo valida:** un solo `UPDATE` condicional pasa la entrada de `VALIDA` a `USADA`, y solo si el QR existe,
es de ese evento y la compra está pagada:

```sql
UPDATE detalle_compra d SET estado = 'USADA', usado_en = :ahora, usado_por = :personal
FROM compra c
WHERE c.id = d.compra_id AND d.codigo_qr = :qr AND c.evento_id = :evento
  AND c.estado = 'PAGADA' AND d.estado = 'VALIDA'
RETURNING d.id
```

Si no actualiza ninguna fila, se lee la entrada solo para explicar el motivo:

| Caso | Respuesta |
|---|---|
| El QR no existe | 404 `QR_INVALIDO` |
| Es de otro evento | 409 `ENTRADA_DE_OTRO_EVENTO` |
| Ya se usó | 409 `ENTRADA_YA_USADA` (con la hora del ingreso) |
| Fue devuelta | 409 `ENTRADA_DEVUELTA` |
| Quien valida no es personal de acceso | 403 `SOLO_PERSONAL_DE_ACCESO` |

**Por qué así:** es la misma garantía que la no doble venta. Si dos molinetes leen el mismo QR a la vez (por
ejemplo, una captura compartida), PostgreSQL bloquea la fila y el segundo `UPDATE` ya no la encuentra
`VALIDA`: entra una sola persona. Lo prueba `IngresoTest` con 20 hilos sobre el mismo QR.

**Datos nuevos:** `detalle_compra.usado_en` y `usado_por` (quién validó), agregados con
`ADD COLUMN IF NOT EXISTS` para que también aparezcan en bases ya creadas.

**Simplificación:** no se controla el horario (se puede validar antes del día del evento).

## F12 · Consistencia entre servicios: sagas de publicación y cancelación

En la Entrega 1 eventos y ventas no se hablaban: la disponibilidad se preparaba a mano (un paso del Postman)
y cancelar un evento no generaba las devoluciones. Ahora son dos **sagas** coordinadas por eventos. No hay
transacción distribuida: cada servicio confirma su parte en su propia base, y el orden de los pasos y la
idempotencia de ventas hacen que el sistema nunca quede en un estado que haga daño.

### Publicar (consistencia fuerte para el cliente)

1. Eventos le pide a lugares los sectores del lugar y valida cada sector con precio: tiene que existir en ese
   lugar (`409 SECTOR_FUERA_DEL_LUGAR`) y coincidir en si es numerado (`409 SECTOR_INCONSISTENTE`). De lugares
   toma la capacidad de las zonas.
2. Le pide a ventas que prepare asientos y cupos (`POST /interno/disponibilidad`).
3. Recién entonces pasa el evento a `PUBLICADO`, con un update condicional (solo desde `BORRADOR`).

Si ventas o lugares no responden, el evento **sigue en `BORRADOR`** (503) y el organizador reintenta. Nunca
hay un evento publicado sin lugares para vender. Si fallara el paso 3 después del 2, no hace falta compensar:
la disponibilidad creada no molesta (ventas no vende eventos que no están publicados) y el reintento no la
duplica (`ON CONFLICT DO NOTHING`).

### Cancelar (consistencia eventual con reintento)

1. El evento pasa a `CANCELADO` y queda marcado `cancelacionNotificada = false`. Desde ese momento ventas
   rechaza compras nuevas, porque consulta el estado del evento en cada compra.
2. Eventos le avisa a ventas (`POST /interno/eventos/{id}/cancelacion`): ventas vence las compras pendientes
   y crea las devoluciones de las entradas pagadas, que piden el reintegro a pagos.
3. Si ventas responde, se marca `cancelacionNotificada = true` y la respuesta dice `devoluciones: PROCESADAS`.

Si ventas está caído, la cancelación igual se confirma al organizador (`devoluciones: PENDIENTES`) y la tarea
`CancelacionesPendientesJob` reintenta cada minuto hasta que ventas la procese. Repetir el aviso es seguro:
ventas saltea las entradas ya devueltas (lo prueba `DevolucionTest`).

**Caso borde conocido:** si una compra se crea justo antes de cancelar (ventas leyó el evento todavía
publicado) y se inserta después de que ventas venció las pendientes, esa compra podría pagarse. Es una
ventana de milisegundos; se cerraría haciendo que `pagar` vuelva a consultar el estado del evento.

**Cambio en la API:** `PUT /eventos/{id}/cancelacion` ahora responde `{eventoId, estado, devoluciones}`.
El paso manual de `POST /interno/disponibilidad` del Postman ya no hace falta.

## F11 · Resiliencia en las llamadas a pagos

Se usa **Resilience4j** (`resilience4j-spring-boot3`). La configuración está en `application.yml` de ventas
(`resilience4j.*`) y se aplica en `RestClients.PagosRestClient`.

| Mecanismo | Configuración | Para qué |
|---|---|---|
| Timeouts | conexión 1 s, lectura 3 s (todos los clientes de ventas) | Si pagos se cuelga, ventas no se queda esperando |
| Reintento `pagos` | 3 intentos, 300 ms entre intentos, solo ante `ServicioNoDisponibleException` | Salva fallas momentáneas (un reinicio, un pico) |
| Circuit breaker `pagos` | ventana de 10 llamadas, se abre con 50 % de fallas, 20 s abierto, 2 de prueba en semiabierto | Si pagos está caído, se deja de llamarlo y se responde al instante |
| Fallback | 503 `PAGOS_NO_DISPONIBLE`; la transacción se deshace y la compra sigue `PENDIENTE` | El cliente no pierde su reserva: puede volver a pagar antes de que venza |

**Por qué reintentar un cobro no cobra dos veces:** pagos es idempotente por compra (si ya existe un pago para
esa compra, devuelve el mismo) y ventas además consulta `GET /pagos?compraId=` antes de cobrar. Así, si una
respuesta se pierde por timeout y se reintenta, el segundo intento no genera otro pago.

**Qué no se reintenta:** un pago rechazado (402) es una respuesta válida, no una falla; tampoco los errores de
negocio (404, 409). Solo `ServicioNoDisponibleException` (caída, timeout o 5xx) se reintenta y cuenta para
el circuito.

**Orden:** el reintento va por fuera del circuit breaker, así cada intento cuenta como una llamada. Con el
circuito abierto, el reintento no insiste (`CallNotPermittedException` no es reintentable).

**Cómo verlo andando:** `GET http://localhost:8083/actuator/circuitbreakers` muestra el estado del circuito
(`CLOSED`, `OPEN`, `HALF_OPEN`). Para probarlo: frenar ms-pagos, intentar pagar 2 o 3 veces y consultar ese
endpoint. `PagosResilienciaTest` lo prueba sin Docker, con un pagos falso que falla a propósito.

**Notificaciones:** el envío es asíncrono y nunca frena la operación principal; se describe en la F8.

## F7 · Reportes del organizador (funcionalidad 12)

**Endpoint:** `GET /reportes/eventos/{eventoId}` en ventas (pasa por el gateway). Responde, por sector y en
total: capacidad, entradas vendidas, devueltas, ingresados (ya pasaron por la puerta), porcentaje de
ocupación y recaudación.

```json
{ "eventoId": 7, "capacidad": 6, "vendidas": 2, "devueltas": 1, "ingresados": 1,
  "ocupacionPorcentaje": 33.3, "recaudacion": 45000.00,
  "sectores": [
    { "sectorId": 1, "numerado": true,  "capacidad": 5, "vendidas": 1, "devueltas": 1, "ingresados": 1,
      "ocupacionPorcentaje": 20.0, "recaudacion": 30000.00 },
    { "sectorId": 2, "numerado": false, "capacidad": 1, "vendidas": 1, "devueltas": 0, "ingresados": 0,
      "ocupacionPorcentaje": 100.0, "recaudacion": 15000.00 } ] }
```

**De dónde sale cada número** (todo está en la base de ventas, dos consultas):
- Capacidad: el cupo de cada zona (`cupo_sector`) y la cantidad de asientos de cada sector numerado
  (`evento_asiento`).
- Vendidas: entradas de compras `PAGADA` que siguen valiendo (`VALIDA` o `USADA`). Las reservas sin pagar no
  cuentan.
- Recaudación: suma de esas entradas; las devueltas se descuentan.
- Ocupación: vendidas / capacidad.

**Quién puede verlo:** un `ORGANIZADOR` solo los de sus eventos (403 `NO_ES_TU_EVENTO`); un `ADMINISTRADOR`,
todos. El dueño del evento lo informa eventos: `GET /eventos/{id}` suma el campo `organizadorId` (agregar un
campo no rompe a quien no lo usa).

## F5 · Cache

**Dónde:** en ms-eventos, en memoria, con **Caffeine** y las anotaciones de Spring Cache (`CacheConfig`,
`EventoService`).

| Cache | Qué guarda | Tiempo de vida | Por qué |
|---|---|---|---|
| `eventoDetalle` | `GET /eventos/{id}` | 30 s | Ventas lo consulta en **cada** compra; en una venta masiva es la lectura más repetida del sistema |
| `eventoBusquedas` | `GET /eventos` con sus filtros (la clave son todos los filtros juntos) | 60 s | El catálogo lo consultan todos los clientes, y también preferencias para recomendar |

**Invalidación:** además del tiempo de vida, cada operación que cambia un evento (cargar precios, sumar
participantes, publicar, cancelar) borra su detalle y todas las búsquedas (`@CacheEvict`). Así, en una sola
instancia, un cambio se ve enseguida. Crear un evento no borra nada, porque un borrador no aparece en las
búsquedas.

**Qué NO se cachea y por qué:**
- **La disponibilidad de lugares** (en ventas): cambia con cada compra. Una cache mostraría asientos libres
  que ya se vendieron, y el cliente se enteraría recién al intentar comprarlos. Además ya es barata (una
  consulta indexada por evento y sector) y la garantía de no doble venta depende de leer el dato real.
- **Compras, entradas y pagos:** son datos de cada usuario, se leen poco y tienen que ser exactos.

**Límite conocido:** es una cache local de cada instancia. Con varias instancias de eventos, una podría
servir un evento recién cancelado como publicado hasta 30 s (el tiempo de vida acota el desfasaje). Para
varias instancias se usaría una cache compartida (por ejemplo Redis) o invalidación por mensajes.

**Cómo verlo andando:** `GET http://localhost:8082/actuator/caches` lista las caches y
`/actuator/metrics/cache.gets?tag=name:eventoDetalle&tag=result:hit` cuenta los aciertos. El test
`el_detalle_se_lee_de_la_cache_la_segunda_vez` verifica que la segunda lectura no va a MongoDB.

## F9 · Preferencias y recomendaciones (ms-preferencias)

**Servicio nuevo:** `ms-preferencias`, puerto **8086**, MongoDB. Usa el mismo servidor Mongo que eventos
(`eventos-db` del docker-compose) con su propia base, `preferencias`: cada servicio sigue siendo dueño de sus
datos, solo comparten el proceso de base. En el gateway: `/preferencias/**` y `/recomendaciones/**`.

**Por qué MongoDB:** el perfil es un documento por cliente con listas adentro (tipos, artistas, eventos
comprados) que crecen y cambian de forma; en relacional serían cuatro tablas con joins. Las dos consultas
que importan (quién tiene a tal artista de favorito, y qué compraron los clientes parecidos) se resuelven con
un índice multikey y una agregación.

**Documento** (colección `perfiles`, `_id` = id del cliente):
```json
{ "_id": 5, "tiposEvento": ["RECITAL"], "artistasFavoritos": ["León Gieco"], "artistasClave": ["leon gieco"],
  "ciudad": "Tandil", "eventosComprados": [1, 7], "actualizadoEn": "..." }
```
- Lo que declara el cliente se escribe con `$set` (PUT /preferencias) y las compras con `$addToSet` (las
  avisa ventas). Son actualizaciones parciales del mismo documento: una compra que llega mientras el cliente
  edita sus preferencias no se pierde, y repetir el aviso de una compra no la duplica.
- `artistasClave` es el nombre normalizado (minúsculas, sin tildes): "León Gieco" y "leon gieco" son el mismo.

**Endpoints**

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| GET | `/preferencias` | CLIENTE | Su perfil (vacío si nunca cargó nada) |
| PUT | `/preferencias` | CLIENTE | Reemplaza tipos, artistas (hasta 10 c/u) y ciudad; no toca las compras |
| GET | `/recomendaciones?limite=10` | CLIENTE | Eventos recomendados con puntaje y motivos |
| POST | `/interno/compras` | ventas | Registra una compra confirmada (idempotente) |
| GET | `/interno/interesados?artistas=...` | notificaciones (F8) | Clientes con esos artistas de favoritos |

**Algoritmo:** cada evento publicado desde hoy que el cliente no compró suma puntos y se explica con motivos:

| Motivo | Puntos |
|---|---|
| `ARTISTA_FAVORITO` | +3 (eventos busca el artista entre los participantes) |
| `TIPO_PREFERIDO` | +2 |
| `TU_CIUDAD` | +1 |
| `LO_COMPRARON_CLIENTES_PARECIDOS` | +2 por cada cliente que compró alguno de sus eventos y también este (hasta 3) |
| `POPULAR` | +1 por comprador (hasta 3), solo para un cliente sin ningún dato |

La parte colaborativa es una agregación en MongoDB (`$match` → `$unwind` → `$match` → `$group` → `$sort`),
en `PerfilRepository.compradosPorClientesParecidos`. El catálogo se pide a `GET /eventos`, que en eventos está
cacheado (F5).

**Cómo se entera de las compras:** `CompraService.confirmar` publica un evento de Spring, `CompraConfirmada`.
`PreferenciasListener` lo atiende con `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`:
- solo se avisa si el pago quedó guardado (después del commit);
- corre en otro hilo: el cliente no espera a preferencias, y si preferencias está caído el pago no falla;
- se reintenta 3 veces (Resilience4j, instancia `avisos`); si igual falla queda en el log.

**Límite conocido:** si ventas se cae entre el commit y el aviso, esa compra no llega a preferencias. Para no
perder ninguna se usaría un *outbox* (guardar el aviso en la misma transacción y mandarlo con un job) o un
broker de mensajes. Para recomendaciones alcanza: perder un aviso solo las hace un poco menos precisas.

**Tests:** `PreferenciasIntegrationTest` (12, MongoDB en Testcontainers) y, en ventas,
`unPagoAprobadoAvisaQueLaCompraSeConfirmo` y los avisos en `FlujoCompraTest`.

## F8 · Notificaciones (ms-notificaciones)

**Servicio nuevo:** `ms-notificaciones`, puerto **8087**, MongoDB (base `notificaciones` en el mismo servidor).
En el gateway: `/notificaciones/**`.

**Qué se notifica**

| Aviso | Lo manda | A quién le llega |
|---|---|---|
| `COMPRA_CONFIRMADA` | ventas, al pagarse una compra | al comprador |
| `EVENTO_CANCELADO` | ventas, al terminar las devoluciones de un evento cancelado | a cada cliente al que se le devolvió alguna entrada |
| `EVENTO_PUBLICADO` | eventos, al publicar un evento con artistas | a los clientes que tienen a alguno de esos artistas de favorito (se le pregunta a preferencias, F9) |

**Asincrónico de punta a punta:**
1. ventas y eventos publican un evento de Spring (`CompraConfirmada`, `EventoCancelado`, `EventoPublicado`) y un
   listener `@Async` hace la llamada HTTP. La operación principal (pagar, cancelar, publicar) no espera y no
   falla si notificaciones está caído. En ventas, además, el aviso sale recién después del commit.
2. notificaciones valida el aviso y responde **202 Accepted** enseguida; arma las notificaciones en segundo plano
   (`ProcesadorDeAvisos`, `@Async`).
3. Por cada destinatario guarda un documento y "manda" el email (simulado: se escribe en el log, `[EMAIL] ...`).

**Reintentos sin duplicados:** quien avisa reintenta (ventas con Resilience4j `avisos`, eventos con 3 intentos).
Para que un reintento no genere dos notificaciones, el `_id` de cada una se arma con lo que se notifica y a quién
(`compra-15-cliente-5`, `publicado-7-cliente-8`): el segundo insert choca con el `_id` existente, se descarta, y
tampoco se manda un segundo email.

**Endpoints**

| Método | Ruta | Quién |
|---|---|---|
| POST | `/interno/avisos/compra-confirmada` · `/evento-cancelado` · `/evento-publicado` | ventas, eventos (202) |
| GET | `/notificaciones?soloNoLeidas=false` | el usuario logueado: las suyas, de la más nueva a la más vieja |
| PUT | `/notificaciones/{id}/leida` | el usuario logueado (404 si es de otro) |

**Documento:**
```json
{ "_id": "compra-15-cliente-5", "clienteId": 5, "tipo": "COMPRA_CONFIRMADA", "eventoId": 7,
  "titulo": "Tu compra esta confirmada", "mensaje": "Compraste 2 entrada(s)...", "canal": "EMAIL",
  "creadaEn": "...", "leida": false }
```
Índice `(clienteId, creadaEn desc)` para la bandeja de cada usuario.

**Límites conocidos:** igual que en F9, si el proceso que avisa se cae entre la operación y el aviso, ese aviso se
pierde (se resolvería con un outbox o un broker de mensajes). El email es simulado; en producción se buscaría el
email en usuarios-service.

**Tests:** `NotificacionesIntegrationTest` (7), y en los productores: `FlujoCompraTest` y `DevolucionTest` en
ventas, `publicar_un_evento_con_artistas_avisa_a_notificaciones` y `EventoServiceTest` en eventos.

## F10 · Fila virtual simplificada (ventas)

**Para qué:** en una venta masiva, miles de clientes intentan comprar en el mismo segundo. La fila virtual
limita cuántos compran a la vez; el resto espera su turno consultando una lectura barata.

**Cómo funciona**
1. El organizador del evento (o un administrador) la activa: `PUT /filas/{eventoId}` con
   `{"simultaneos": 50, "minutosTurno": 10}` (cuántos clientes compran a la vez y cuánto tiempo tiene cada uno).
2. Cada cliente saca un turno, `POST /filas/{eventoId}/turno`, y recibe un número. Pedirlo de nuevo devuelve el
   mismo (no lo manda al final).
3. Cada 5 segundos, `FilaVirtualJob` vence los turnos sin tiempo y habilita a los siguientes por orden de número
   hasta completar los simultáneos.
4. Mientras la fila está activa, `POST /compras` solo acepta a quien tiene turno habilitado y vigente:
   - `409 TURNO_REQUERIDO`: no sacó turno;
   - `409 TURNO_EN_ESPERA`: todavía no le toca (el mensaje dice cuántas personas hay antes);
   - `409 TURNO_VENCIDO`: se le terminó el tiempo (puede sacar otro, va al final).
5. Al crear la compra, el turno pasa a `USADO` y su lugar en la tanda queda libre para el siguiente. Hasta que
   vence, puede seguir comprando (el límite por cliente se sigue aplicando).
6. `DELETE /filas/{eventoId}` vuelve a la compra directa.

`GET /filas/{eventoId}` (si hay fila, cuántos esperan y cuántos compran) y `GET /filas/{eventoId}/turno`
(mi número, cuántos hay antes o hasta cuándo puedo comprar) son para que la GUI muestre la espera.

**Tablas** (`schema.sql`): `fila_virtual` (configuración por evento y último número entregado) y `turno_fila`
(un turno por cliente y evento: `ESPERANDO → HABILITADO → USADO | VENCIDO`).

**Concurrencia**
- **Números distintos:** el número sale de `UPDATE fila_virtual SET ultimo_numero = ultimo_numero + 1 ... RETURNING`.
  PostgreSQL ordena los pedidos simultáneos y cada uno recibe uno distinto (test con 30 clientes a la vez). El
  UPDATE se confirma enseguida, así la fila del evento queda bloqueada solo un instante. Si algo falla después
  queda un número sin usar, y no molesta: se habilita por orden, no por números consecutivos.
- **No habilitar de más:** el avance bloquea la fila del evento (`SELECT ... FOR UPDATE`) durante su
  transacción. Con dos instancias de ventas, una espera a la otra.
- **La no doble venta no cambia:** la fila solo regula quién entra; los lugares se siguen tomando con los UPDATE
  condicionales de siempre.

**Dónde se controla:** en `CompraController`, antes de `CompraService.crear`. Así la lógica de compra (y sus
tests) no cambia, y sin fila activa el control es una sola consulta por clave primaria.

**Simplificaciones:** el avance es por sondeo cada 5 s (no avisa en el momento; la GUI consulta el turno);
la fila vive en la misma base de ventas. Para millones de clientes se usaría una estructura en memoria
compartida (por ejemplo Redis) y avisos por WebSocket.

**Tests:** `FilaVirtualTest` (10).

---

## Avisos para el grupo (lo que depende de otras partes)

- **F2 (Docker):** hay dos servicios nuevos, `ms-preferencias` (8086) y `ms-notificaciones` (8087). Variables:
  - eventos: `EVENTOS_MONGO_URI=mongodb://root:password@eventos-db:27017/eventos?authSource=admin`,
    `LUGARES_URL`, `VENTAS_URL`, `NOTIFICACIONES_URL`;
  - ventas: `EVENTOS_URL`, `LUGARES_URL`, `PAGOS_URL`, `PREFERENCIAS_URL`, `NOTIFICACIONES_URL`;
  - preferencias: `PREFERENCIAS_MONGO_URI=mongodb://root:password@eventos-db:27017/preferencias?authSource=admin`, `EVENTOS_URL`;
  - notificaciones: `NOTIFICACIONES_MONGO_URI=mongodb://root:password@eventos-db:27017/notificaciones?authSource=admin`, `PREFERENCIAS_URL`;
  - gateway: `PREFERENCIAS_URL`, `NOTIFICACIONES_URL` (además de los que ya tenía).
  Publicar solo el puerto del gateway; los `/interno/**` no tienen ruta en el gateway.
- **F1 (seguridad):** falta la autorización por rol en el gateway y que el registro no permita crear un
  ADMINISTRADOR. Los servicios ya controlan el rol donde importa (reportes, fila, ingresos, devoluciones,
  preferencias).
- **F3** sigue pendiente.
- **Postman (F14):** todas las rutas necesitan token. El paso manual de `/interno/disponibilidad` ya no hace
  falta: publicar el evento la prepara sola (F12).

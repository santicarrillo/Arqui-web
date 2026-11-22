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

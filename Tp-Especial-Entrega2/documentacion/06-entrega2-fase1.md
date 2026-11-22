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

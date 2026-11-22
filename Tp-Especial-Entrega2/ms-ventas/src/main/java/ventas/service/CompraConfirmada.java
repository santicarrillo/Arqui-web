package ventas.service;

import java.math.BigDecimal;

/**
 * Evento interno de Spring: "esta compra se pago y ya tiene sus QR".
 *
 * CompraService lo publica dentro de la transaccion del pago, pero los que lo escuchan usan
 * @TransactionalEventListener(AFTER_COMMIT): solo se enteran si la transaccion confirmo. Asi nunca se avisa
 * una compra que despues se deshizo. Ademas lo atienden con @Async, en otro hilo: el cliente recibe la
 * respuesta del pago sin esperar a preferencias ni a notificaciones, y si esos servicios estan caidos el
 * pago no falla.
 */
public record CompraConfirmada(long compraId, long clienteId, long eventoId, BigDecimal total, int entradas) {
}

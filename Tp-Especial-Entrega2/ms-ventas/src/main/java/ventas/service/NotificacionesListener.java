package ventas.service;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ventas.client.NotificacionesClient;

/**
 * F8: avisos a notificaciones-service. Igual que PreferenciasListener:
 *  - despues del commit: nunca se avisa algo que despues se deshizo;
 *  - en otro hilo (@Async): el cliente no espera, y si notificaciones esta caido la compra o la cancelacion
 *    no fallan;
 *  - con reintentos ("avisos" en application.yml). Repetir es seguro: notificaciones no duplica un aviso.
 *
 * La cancelacion de un evento no corre en una sola transaccion (cada entrada va en la suya), por eso su
 * listener tiene fallbackExecution = true: si no hay transaccion, se ejecuta igual.
 */
@Component
public class NotificacionesListener {

    private static final Logger log = LoggerFactory.getLogger(NotificacionesListener.class);

    private final NotificacionesClient notificaciones;
    private final Retry reintento;

    public NotificacionesListener(NotificacionesClient notificaciones, RetryRegistry reintentos) {
        this.notificaciones = notificaciones;
        this.reintento = reintentos.retry("avisos");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alConfirmarCompra(CompraConfirmada compra) {
        avisar("la compra " + compra.compraId(), () -> notificaciones.compraConfirmada(
                compra.compraId(), compra.clienteId(), compra.eventoId(), compra.entradas(), compra.total()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alCancelarEvento(EventoCancelado cancelacion) {
        avisar("la cancelacion del evento " + cancelacion.eventoId(),
                () -> notificaciones.eventoCancelado(cancelacion.eventoId(), cancelacion.clientes()));
    }

    private void avisar(String que, Runnable llamada) {
        try {
            Retry.decorateRunnable(reintento, llamada).run();
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar {} a notificaciones-service: {}", que, e.getMessage());
        }
    }
}

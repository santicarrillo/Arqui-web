package ventas.service;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ventas.client.PreferenciasClient;

/**
 * F9: cuando una compra se confirma, le avisa a preferencias-service para el historial del cliente.
 *
 * Corre despues del commit y en otro hilo (ver CompraConfirmada). Si preferencias no responde se reintenta
 * ("avisos" en application.yml: 3 intentos, 1 s entre cada uno; se puede repetir porque preferencias es
 * idempotente). Si igual falla, se registra en el log y la compra sigue pagada: perder un aviso solo hace
 * un poco menos precisa una recomendacion.
 */
@Component
public class PreferenciasListener {

    private static final Logger log = LoggerFactory.getLogger(PreferenciasListener.class);

    private final PreferenciasClient preferencias;
    private final Retry reintento;

    public PreferenciasListener(PreferenciasClient preferencias, RetryRegistry reintentos) {
        this.preferencias = preferencias;
        this.reintento = reintentos.retry("avisos");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alConfirmarCompra(CompraConfirmada compra) {
        try {
            Retry.decorateRunnable(reintento,
                    () -> preferencias.registrarCompra(compra.clienteId(), compra.eventoId())).run();
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar la compra {} a preferencias-service: {}", compra.compraId(), e.getMessage());
        }
    }
}

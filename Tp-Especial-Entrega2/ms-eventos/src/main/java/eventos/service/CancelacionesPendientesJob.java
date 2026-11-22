package eventos.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reintenta avisarle a ventas las cancelaciones que no pudo procesar (por ejemplo, ventas estaba caido).
 * Ventas procesa la cancelacion de forma idempotente, asi que repetir el aviso no duplica devoluciones.
 */
@Component
public class CancelacionesPendientesJob {

    private final EventoService eventos;

    public CancelacionesPendientesJob(EventoService eventos) {
        this.eventos = eventos;
    }

    @Scheduled(fixedDelayString = "${eventos.reintento-cancelaciones-ms:60000}",
               initialDelayString = "${eventos.reintento-cancelaciones-ms:60000}")
    public void reintentar() {
        eventos.reintentarCancelacionesPendientes();
    }
}

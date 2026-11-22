package eventos.service;

import eventos.client.NotificacionesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * F8: cuando se publica un evento con artistas, le avisa a notificaciones-service para que les llegue a sus
 * seguidores. Corre en otro hilo (@Async): el organizador no espera, y si notificaciones no responde la
 * publicacion igual queda hecha. Se intenta hasta 3 veces; repetir es seguro porque notificaciones no
 * duplica un aviso.
 */
@Component
public class AvisosListener {

    private static final Logger log = LoggerFactory.getLogger(AvisosListener.class);
    static final int INTENTOS = 3;

    private final NotificacionesClient notificaciones;
    private final long esperaMs;

    public AvisosListener(NotificacionesClient notificaciones,
                          @Value("${eventos.avisos.espera-entre-intentos-ms:1000}") long esperaMs) {
        this.notificaciones = notificaciones;
        this.esperaMs = esperaMs;
    }

    @Async
    @EventListener
    public void alPublicar(EventoPublicado evento) {
        if (evento.artistas().isEmpty()) {
            return; // sin artistas no hay a quien avisarle
        }
        for (int intento = 1; intento <= INTENTOS; intento++) {
            try {
                notificaciones.eventoPublicado(evento.eventoId(), evento.nombre(), evento.ciudad(),
                        evento.fechaHora(), evento.artistas());
                return;
            } catch (RuntimeException e) {
                if (intento == INTENTOS) {
                    log.warn("No se pudo avisar la publicacion del evento {} a notificaciones-service: {}",
                            evento.eventoId(), e.getMessage());
                    return;
                }
                esperar();
            }
        }
    }

    private void esperar() {
        try {
            Thread.sleep(esperaMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

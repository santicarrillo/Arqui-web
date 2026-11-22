package ventas.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ventas.repository.FilaRepository;

/** F10: cada 5 segundos hace avanzar las filas virtuales activas (vence turnos y habilita a los siguientes). */
@Component
public class FilaVirtualJob {

    private static final Logger log = LoggerFactory.getLogger(FilaVirtualJob.class);

    private final FilaRepository repo;
    private final FilaVirtualService filas;

    public FilaVirtualJob(FilaRepository repo, FilaVirtualService filas) {
        this.repo = repo;
        this.filas = filas;
    }

    @Scheduled(fixedDelayString = "${ventas.fila.avance-cada-ms:5000}",
               initialDelayString = "${ventas.fila.avance-cada-ms:5000}")
    public void avanzarFilas() {
        for (Long eventoId : repo.eventosConFilaActiva()) {
            try {
                int habilitados = filas.avanzar(eventoId);
                if (habilitados > 0) {
                    log.info("Fila del evento {}: {} turno(s) habilitado(s)", eventoId, habilitados);
                }
            } catch (RuntimeException e) {
                log.warn("No se pudo avanzar la fila del evento {}: {}", eventoId, e.getMessage());
            }
        }
    }
}

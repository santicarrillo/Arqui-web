package service;

import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import repository.VentasRepository;

/** Tarea programada: pasa a VENCIDA las compras pendientes cuya reserva ya vencio y libera sus lugares. */
@Component
public class VencimientoJob {

    private static final Logger log = LoggerFactory.getLogger(VencimientoJob.class);

    private final VentasRepository repo;
    private final CompraService compras;
    private final Clock clock;

    public VencimientoJob(VentasRepository repo, CompraService compras, Clock clock) {
        this.repo = repo;
        this.compras = compras;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${ventas.vencimiento-cada-ms:30000}")
    public void vencerReservas() {
        for (Long compraId : repo.pendientesVencidas(LocalDateTime.now(clock))) {
            try {
                compras.vencer(compraId);
                log.info("Compra {} vencida: lugares liberados", compraId);
            } catch (RuntimeException e) {
                log.warn("No se pudo vencer la compra {}: {}", compraId, e.getMessage());
            }
        }
    }
}
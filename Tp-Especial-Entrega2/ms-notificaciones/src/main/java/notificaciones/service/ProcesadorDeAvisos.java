package notificaciones.service;

import notificaciones.api.Dtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Procesa los avisos en segundo plano (@Async, con el pool de hilos de Spring Boot). Esta separado de
 * AvisoService porque @Async funciona a traves del proxy de Spring: una llamada desde la misma clase no
 * seria asincronica.
 *
 * Si algo falla (por ejemplo preferencias no responde) se registra en el log: quien aviso ya recibio su 202
 * y su operacion no se ve afectada.
 */
@Component
public class ProcesadorDeAvisos {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorDeAvisos.class);

    private final AvisoService avisos;

    public ProcesadorDeAvisos(AvisoService avisos) {
        this.avisos = avisos;
    }

    @Async
    public void compraConfirmada(Dtos.CompraConfirmadaRequest aviso) {
        procesar("COMPRA_CONFIRMADA compra " + aviso.compraId(), () -> avisos.compraConfirmada(aviso));
    }

    @Async
    public void eventoCancelado(Dtos.EventoCanceladoRequest aviso) {
        procesar("EVENTO_CANCELADO evento " + aviso.eventoId(), () -> avisos.eventoCancelado(aviso));
    }

    @Async
    public void eventoPublicado(Dtos.EventoPublicadoRequest aviso) {
        procesar("EVENTO_PUBLICADO evento " + aviso.eventoId(), () -> avisos.eventoPublicado(aviso));
    }

    private void procesar(String que, Supplier<Integer> tarea) {
        try {
            int nuevas = tarea.get();
            log.info("Aviso {}: {} notificacion(es) nueva(s)", que, nuevas);
        } catch (RuntimeException e) {
            log.warn("No se pudo procesar el aviso {}: {}", que, e.getMessage());
        }
    }
}

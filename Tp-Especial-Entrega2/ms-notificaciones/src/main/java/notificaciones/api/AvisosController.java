package notificaciones.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import notificaciones.service.ProcesadorDeAvisos;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Avisos de los otros servicios. Responden 202 Accepted apenas validan el pedido: armar las notificaciones
 * (y para una publicacion, consultar a preferencias) se hace en segundo plano, asi quien avisa no espera.
 * El gateway no tiene ruta a /interno/**.
 */
@RestController
@RequestMapping("/interno/avisos")
@Tag(name = "Interno", description = "Avisos de ventas y eventos; no los llama el cliente")
public class AvisosController {

    private final ProcesadorDeAvisos procesador;

    public AvisosController(ProcesadorDeAvisos procesador) {
        this.procesador = procesador;
    }

    @Operation(summary = "ventas: se confirmo una compra. Se le avisa al cliente.")
    @PostMapping("/compra-confirmada")
    public ResponseEntity<Void> compraConfirmada(@Valid @RequestBody Dtos.CompraConfirmadaRequest aviso) {
        procesador.compraConfirmada(aviso);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "ventas: se cancelo un evento. Se le avisa a cada cliente al que se le devolvieron entradas.")
    @PostMapping("/evento-cancelado")
    public ResponseEntity<Void> eventoCancelado(@Valid @RequestBody Dtos.EventoCanceladoRequest aviso) {
        procesador.eventoCancelado(aviso);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "eventos: se publico un evento. Se le avisa a quienes tienen a alguno de sus artistas de favorito.")
    @PostMapping("/evento-publicado")
    public ResponseEntity<Void> eventoPublicado(@Valid @RequestBody Dtos.EventoPublicadoRequest aviso) {
        procesador.eventoPublicado(aviso);
        return ResponseEntity.accepted().build();
    }
}

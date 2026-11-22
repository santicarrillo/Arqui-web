package ventas.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ventas.service.CompraService;

import java.util.Map;

@RestController
@RequestMapping("/compras")
public class CompraController {
    private final CompraService compras;

    public CompraController(CompraService compras) {
        this.compras = compras;
    }

    @Operation(summary = "Reserva los lugares y crea la compra PENDIENTE (vence a los 10 minutos). "
            + "Repetir la misma Idempotency-Key devuelve la misma compra (200).")
    @PostMapping
    public ResponseEntity<Dtos.CompraView> crear(@RequestHeader("X-Usuario-Id") long usuarioId,
                                                 @RequestHeader("Idempotency-Key") String clave,
                                                 @Valid @RequestBody Dtos.CompraRequest pedido) {
        CompraService.CompraCreada creada = compras.crear(usuarioId, clave, pedido);
        return ResponseEntity.status(creada.nueva() ? HttpStatus.CREATED : HttpStatus.OK).body(creada.compra());
    }

    @Operation(summary = "Paga la compra: 200 con las entradas y sus QR, 402 si el pago es rechazado, "
            + "409 si la reserva vencio.")
    @PostMapping("/{id}/pagar")
    public ResponseEntity<Object> pagar(@RequestHeader("X-Usuario-Id") long usuarioId,
                                        @PathVariable("id") long id,
                                        @Valid @RequestBody Dtos.PagoRequest pedido) {
        CompraService.ResultadoPago resultado = compras.pagar(usuarioId, id, pedido.medioPago());
        return switch (resultado.resultado()) {
            case PAGADA -> ResponseEntity.ok().<Object>body(resultado.compra());
            case RECHAZADA -> ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).<Object>body(Map.of(
                    "error", "PAGO_RECHAZADO",
                    "mensaje", "El pago fue rechazado y la compra paso a RECHAZADA"));
            case VENCIDA -> ResponseEntity.status(HttpStatus.CONFLICT).<Object>body(Map.of(
                    "error", "COMPRA_VENCIDA",
                    "mensaje", "La reserva vencio; hay que hacer una compra nueva"));
        };
    }

    @Operation(summary = "Consulta una compra del usuario")
    @GetMapping("/{id}")
    public Dtos.CompraView obtener(@RequestHeader("X-Usuario-Id") long usuarioId, @PathVariable("id") long id) {
        return compras.obtener(usuarioId, id);
    }

    @Operation(summary = "Cancela una compra pendiente y libera los lugares")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelar(@RequestHeader("X-Usuario-Id") long usuarioId, @PathVariable("id") long id) {
        compras.cancelar(usuarioId, id);
        return ResponseEntity.noContent().build();
    }
}

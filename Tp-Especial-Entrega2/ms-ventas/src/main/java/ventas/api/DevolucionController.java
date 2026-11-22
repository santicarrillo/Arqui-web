package ventas.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ventas.api.DevolucionDtos.CancelacionView;
import ventas.api.DevolucionDtos.DevolucionView;
import ventas.api.DevolucionDtos.ResolucionRequest;
import ventas.service.DevolucionService;

@RestController
public class DevolucionController {

    private final DevolucionService devoluciones;

    public DevolucionController(DevolucionService devoluciones) {
        this.devoluciones = devoluciones;
    }

    @Tag(name = "Devoluciones")
    @Operation(summary = "El cliente pide devolver una entrada pagada (hasta 72 horas antes del evento). "
            + "409 si esta fuera de plazo o ya tiene una devolucion en curso.")
    @PostMapping("/entradas/{id}/devolucion")
    public ResponseEntity<DevolucionView> solicitar(@RequestHeader("X-Usuario-Id") long usuarioId,
                                                    @PathVariable("id") long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(devoluciones.solicitar(usuarioId, id));
    }

    @Tag(name = "Devoluciones")
    @Operation(summary = "Un administrador aprueba o rechaza la devolucion pendiente de una entrada. "
            + "Al aprobar, la entrada queda DEVUELTA, el lugar se libera y se pide el reintegro a pagos.")
    @PutMapping("/entradas/{id}/devolucion")
    public DevolucionView resolver(@RequestHeader("X-Usuario-Id") long usuarioId,
                                   @RequestHeader("X-Rol") String rol,
                                   @PathVariable("id") long id,
                                   @Valid @RequestBody ResolucionRequest pedido) {
        return devoluciones.resolver(usuarioId, rol, id, pedido.aprobar());
    }

    @Tag(name = "Devoluciones")
    @Operation(summary = "Devoluciones por estado (por defecto las PENDIENTE), para el administrador")
    @GetMapping("/devoluciones")
    public List<DevolucionView> listar(@RequestHeader("X-Rol") String rol,
                                       @RequestParam(name = "estado", defaultValue = "PENDIENTE") String estado) {
        return devoluciones.listar(rol, estado);
    }

    @Tag(name = "Interno", description = "Solo lo llama eventos-service; no se expone en el gateway")
    @Operation(summary = "Eventos avisa que cancelo un evento: vence las compras pendientes y devuelve "
            + "todas las entradas. Se puede repetir sin duplicar nada.")
    @PostMapping("/interno/eventos/{eventoId}/cancelacion")
    public CancelacionView cancelarEvento(@PathVariable("eventoId") long eventoId) {
        return devoluciones.cancelarEvento(eventoId);
    }
}

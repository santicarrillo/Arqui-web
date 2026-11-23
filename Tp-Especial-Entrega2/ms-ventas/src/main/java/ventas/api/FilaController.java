package ventas.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ventas.api.FilaDtos.ActivarFilaRequest;
import ventas.api.FilaDtos.FilaView;
import ventas.api.FilaDtos.TurnoView;
import ventas.service.FilaVirtualService;

@RestController
@RequestMapping("/filas/{eventoId}")
@Tag(name = "Fila virtual", description = "Turnos para comprar en eventos de alta demanda (F10)")
public class FilaController {

    private final FilaVirtualService filas;

    public FilaController(FilaVirtualService filas) {
        this.filas = filas;
    }

    @Operation(summary = "El organizador del evento (o un administrador) activa la fila o cambia su configuracion.")
    @PutMapping
    public FilaView activar(@RequestHeader("X-Usuario-Id") long usuarioId, @RequestHeader("X-Rol") String rol,
                            @PathVariable("eventoId") long eventoId, @Valid @RequestBody ActivarFilaRequest pedido) {
        return filas.activar(usuarioId, rol, eventoId, pedido);
    }

    @Operation(summary = "Desactiva la fila: se vuelve a comprar directamente.")
    @DeleteMapping
    public FilaView desactivar(@RequestHeader("X-Usuario-Id") long usuarioId, @RequestHeader("X-Rol") String rol,
                               @PathVariable("eventoId") long eventoId) {
        return filas.desactivar(usuarioId, rol, eventoId);
    }

    @Operation(summary = "Si el evento tiene fila activa, cuantos esperan y cuantos estan comprando.")
    @GetMapping
    public FilaView estado(@PathVariable("eventoId") long eventoId) {
        return filas.estado(eventoId);
    }

    @Operation(summary = "Saca un turno (o devuelve el que ya tengo). 409 SIN_FILA si el evento no tiene fila activa.")
    @PostMapping("/turno")
    public TurnoView tomarTurno(@RequestHeader("X-Usuario-Id") long usuarioId, @RequestHeader("X-Rol") String rol,
                                @PathVariable("eventoId") long eventoId) {
        Roles.exigirCliente(rol);
        return filas.tomarTurno(usuarioId, eventoId);
    }

    @Operation(summary = "Mi turno: cuantas personas hay antes, o hasta cuando puedo comprar.")
    @GetMapping("/turno")
    public TurnoView turno(@RequestHeader("X-Usuario-Id") long usuarioId, @RequestHeader("X-Rol") String rol,
                           @PathVariable("eventoId") long eventoId) {
        Roles.exigirCliente(rol);
        return filas.turno(usuarioId, eventoId);
    }
}

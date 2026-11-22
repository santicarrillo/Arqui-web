package ventas.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import ventas.api.IngresoDtos.IngresoRequest;
import ventas.api.IngresoDtos.IngresoView;
import ventas.service.IngresoService;

@RestController
@Tag(name = "Control de acceso", description = "Validacion del QR en la puerta del evento (rol ACCESO)")
public class IngresoController {

    private final IngresoService ingresos;

    public IngresoController(IngresoService ingresos) {
        this.ingresos = ingresos;
    }

    @Operation(summary = "Valida el QR de una entrada en la puerta y la marca como usada. "
            + "404 si el QR no existe; 409 si es de otro evento, ya se uso o fue devuelta; 403 si no es personal de acceso.")
    @PostMapping("/ingresos")
    public IngresoView validar(@RequestHeader("X-Usuario-Id") long usuarioId,
                               @RequestHeader("X-Rol") String rol,
                               @Valid @RequestBody IngresoRequest pedido) {
        return ingresos.validar(usuarioId, rol, pedido.eventoId(), pedido.codigoQr());
    }
}

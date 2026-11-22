package ventas.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import ventas.api.ReporteDtos.ReporteEventoView;
import ventas.service.ReporteService;

@RestController
@Tag(name = "Reportes", description = "Reportes de ventas para organizadores")
public class ReporteController {

    private final ReporteService reportes;

    public ReporteController(ReporteService reportes) {
        this.reportes = reportes;
    }

    @Operation(summary = "Vendidas, devueltas e ingresados por sector, ocupacion y recaudacion de un evento. "
            + "Un organizador solo ve sus eventos (403 si no es suyo); un administrador ve todos.")
    @GetMapping("/reportes/eventos/{eventoId}")
    public ReporteEventoView deEvento(@RequestHeader("X-Usuario-Id") long usuarioId,
                                      @RequestHeader("X-Rol") String rol,
                                      @PathVariable("eventoId") long eventoId) {
        return reportes.deEvento(usuarioId, rol, eventoId);
    }
}

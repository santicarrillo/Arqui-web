package notificaciones.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import notificaciones.Domains.ApiException;
import notificaciones.repository.NotificacionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Lo que usa el cliente a traves del gateway. Cada uno ve solo las suyas (el id sale del token). */
@RestController
@Tag(name = "Notificaciones", description = "Las notificaciones del usuario logueado")
public class NotificacionesController {

    private static final int MAXIMO = 50;

    private final NotificacionRepository repo;

    public NotificacionesController(NotificacionRepository repo) {
        this.repo = repo;
    }

    @Operation(summary = "Mis notificaciones, de la mas nueva a la mas vieja (hasta 50).")
    @GetMapping("/notificaciones")
    public List<Dtos.NotificacionView> mias(@RequestHeader("X-Usuario-Id") long usuarioId,
                                            @RequestParam(defaultValue = "false") boolean soloNoLeidas) {
        return repo.deCliente(usuarioId, soloNoLeidas, MAXIMO).stream().map(Dtos.NotificacionView::de).toList();
    }

    @Operation(summary = "Marca una notificacion mia como leida. 404 si no existe o es de otro usuario.")
    @PutMapping("/notificaciones/{id}/leida")
    public ResponseEntity<Void> marcarLeida(@RequestHeader("X-Usuario-Id") long usuarioId, @PathVariable String id) {
        if (!repo.marcarLeida(id, usuarioId)) {
            throw ApiException.notFound("NOTIFICACION_NO_ENCONTRADA", "La notificacion " + id + " no existe");
        }
        return ResponseEntity.noContent().build();
    }
}

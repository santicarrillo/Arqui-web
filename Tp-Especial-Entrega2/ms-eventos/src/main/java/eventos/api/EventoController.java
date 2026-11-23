package eventos.api;

import eventos.service.EventoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/eventos")
public class EventoController {

    private final EventoService service;

    public EventoController(EventoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> crear(@RequestHeader("X-Usuario-Id") long usuarioId,
                                                   @RequestHeader("X-Rol") String rol,
                                                   @RequestBody Dtos.CrearEventoRequest req) {
        long id = service.crear(req, usuarioId, rol);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @PostMapping("/{id}/sectores")
    public ResponseEntity<Void> configurarSectores(@RequestHeader("X-Usuario-Id") long usuarioId,
                                                   @RequestHeader("X-Rol") String rol,
                                                   @PathVariable long id,
                                                   @RequestBody List<Dtos.SectorPrecioRequest> sectores) {
        service.configurarSectores(id, sectores, usuarioId, rol);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/participantes")
    public ResponseEntity<Void> participantes(@RequestHeader("X-Usuario-Id") long usuarioId,
                                              @RequestHeader("X-Rol") String rol,
                                              @PathVariable long id,
                                              @RequestBody List<Dtos.ParticipanteRequest> participantes) {
        service.agregarParticipantes(id, participantes, usuarioId, rol);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/publicacion")
    public ResponseEntity<Void> publicar(@RequestHeader("X-Usuario-Id") long usuarioId,
                                         @RequestHeader("X-Rol") String rol,
                                         @PathVariable long id) {
        service.publicar(id, usuarioId, rol);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/cancelacion")
    public Dtos.CancelacionView cancelar(@RequestHeader("X-Usuario-Id") long usuarioId,
                                         @RequestHeader("X-Rol") String rol,
                                         @PathVariable long id, @RequestBody Dtos.CancelacionRequest req) {
        return service.cancelar(id, req.motivo(), usuarioId, rol);
    }

    // busqueda por multiples criterios (todos opcionales)
    @GetMapping
    public List<Dtos.EventoResumenView> buscar(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Long organizadorId,
            @RequestParam(required = false) String participante,
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false, defaultValue = "fecha") String orden) {
        return service.buscar(tipo, organizadorId, participante, ciudad, desde, hasta, orden);
    }

    // >>> lo que llama ventas <<<
    @GetMapping("/{id}")
    public Dtos.EventoInfoView obtener(@PathVariable long id) {
        return service.obtenerInfo(id);
    }
}

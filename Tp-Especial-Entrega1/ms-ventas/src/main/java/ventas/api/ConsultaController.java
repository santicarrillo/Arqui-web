package ventas.api;


import ventas.Domains.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ventas.repository.VentasRepository;
import ventas.service.DisponibilidadService;

import java.util.List;
import java.util.Map;

@RestController
public class ConsultaController {

    private final DisponibilidadService disponibilidad;
    private final VentasRepository repo;

    public ConsultaController(DisponibilidadService disponibilidad, VentasRepository repo) {
        this.disponibilidad = disponibilidad;
        this.repo = repo;
    }

    @Tag(name = "Disponibilidad")
    @Operation(summary = "Lugares libres de un sector de un evento (asientos libres, o cupo de la zona)")
    @GetMapping("/disponibilidad")
    public Dtos.DisponibilidadView disponibilidad(@RequestParam("eventoId") long eventoId, @RequestParam("sectorId") long sectorId) {
        return disponibilidad.consultar(eventoId, sectorId);
    }

    @Tag(name = "Entradas")
    @Operation(summary = "Entradas pagadas del usuario que viene en X-Usuario-Id")
    @GetMapping("/entradas")
    public List<Dtos.EntradaView> entradas(@RequestHeader("X-Usuario-Id") long usuarioId) {
        return repo.entradasDeCliente(usuarioId);
    }

    @Tag(name = "Entradas")
    @Operation(summary = "Una entrada del usuario, con su codigo QR")
    @GetMapping("/entradas/{id}")
    public Dtos.EntradaView entrada(@RequestHeader("X-Usuario-Id") long usuarioId, @PathVariable("id") long id) {
        return repo.entradaDeCliente(id, usuarioId).orElseThrow(() ->
                ApiException.notFound("ENTRADA_NO_ENCONTRADA", "La entrada " + id + " no existe"));
    }

    @Tag(name = "Interno", description = "Solo lo llama eventos-service; no se expone en el gateway")
    @Operation(summary = "Prepara la disponibilidad de un evento que se acaba de publicar")
    @PostMapping("/interno/disponibilidad")
    public Map<String, Integer> inicializar(@Valid @RequestBody Dtos.InicializarRequest pedido) {
        return disponibilidad.inicializar(pedido);
    }
}
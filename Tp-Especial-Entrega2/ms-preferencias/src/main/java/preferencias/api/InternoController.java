package preferencias.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import preferencias.service.PreferenciasService;

import java.util.List;

/**
 * Endpoints entre servicios. El gateway no tiene ruta a /interno/**, asi que desde afuera no se llega.
 */
@RestController
@RequestMapping("/interno")
@Tag(name = "Interno", description = "Lo llaman otros microservicios, no el cliente")
public class InternoController {

    private final PreferenciasService preferencias;

    public InternoController(PreferenciasService preferencias) {
        this.preferencias = preferencias;
    }

    @Operation(summary = "ventas avisa que el cliente compro entradas para un evento. Es idempotente: "
            + "repetir el aviso no duplica nada.")
    @PostMapping("/compras")
    public ResponseEntity<Void> registrarCompra(@Valid @RequestBody Dtos.CompraRegistradaRequest pedido) {
        preferencias.registrarCompra(pedido.clienteId(), pedido.eventoId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Clientes que tienen alguno de estos artistas como favorito (sin importar mayusculas "
            + "ni tildes). Lo usa notificaciones cuando se publica un evento.")
    @GetMapping("/interesados")
    public List<Long> interesados(@RequestParam List<String> artistas) {
        return preferencias.interesados(artistas);
    }
}

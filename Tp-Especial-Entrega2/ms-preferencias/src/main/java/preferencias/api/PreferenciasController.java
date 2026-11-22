package preferencias.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import preferencias.Domains.ApiException;
import preferencias.service.PreferenciasService;
import preferencias.service.RecomendacionService;

import java.util.List;

/**
 * Lo que usa el cliente, a traves del gateway (que valida el JWT y manda X-Usuario-Id y X-Rol).
 * Cada cliente ve y edita solo su perfil: el id sale del token, nunca de la URL.
 */
@RestController
@Tag(name = "Preferencias y recomendaciones", description = "Perfil del cliente y eventos recomendados (rol CLIENTE)")
public class PreferenciasController {

    private final PreferenciasService preferencias;
    private final RecomendacionService recomendaciones;

    public PreferenciasController(PreferenciasService preferencias, RecomendacionService recomendaciones) {
        this.preferencias = preferencias;
        this.recomendaciones = recomendaciones;
    }

    @Operation(summary = "Mis preferencias y los eventos que compre. Si todavia no cargue nada, vienen vacias.")
    @GetMapping("/preferencias")
    public Dtos.PerfilView obtener(@RequestHeader("X-Usuario-Id") long usuarioId,
                                   @RequestHeader("X-Rol") String rol) {
        soloClientes(rol);
        return preferencias.obtener(usuarioId);
    }

    @Operation(summary = "Reemplaza mis tipos de evento, artistas favoritos y ciudad (hasta 10 de cada lista). "
            + "No borra el historial de compras.")
    @PutMapping("/preferencias")
    public Dtos.PerfilView guardar(@RequestHeader("X-Usuario-Id") long usuarioId,
                                   @RequestHeader("X-Rol") String rol,
                                   @RequestBody Dtos.PreferenciasRequest pedido) {
        soloClientes(rol);
        return preferencias.guardar(usuarioId, pedido);
    }

    @Operation(summary = "Eventos publicados recomendados para mi, del mas al menos afin, con el motivo de cada uno.")
    @GetMapping("/recomendaciones")
    public List<Dtos.RecomendacionView> recomendar(@RequestHeader("X-Usuario-Id") long usuarioId,
                                                   @RequestHeader("X-Rol") String rol,
                                                   @RequestParam(defaultValue = "10") int limite) {
        soloClientes(rol);
        return recomendaciones.recomendar(usuarioId, limite);
    }

    private static void soloClientes(String rol) {
        if (!"CLIENTE".equals(rol)) {
            throw ApiException.forbidden("SOLO_CLIENTES", "Las preferencias y recomendaciones son de los clientes");
        }
    }
}

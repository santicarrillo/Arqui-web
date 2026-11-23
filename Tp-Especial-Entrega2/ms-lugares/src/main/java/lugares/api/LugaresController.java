package lugares.api;


import lugares.Domains.ApiException;
import lugares.service.LugaresService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * F1: dar de alta lugares y sectores es del ADMINISTRADOR o del ORGANIZADOR (el rol lo manda el gateway en
 * X-Rol). Las consultas quedan abiertas: eventos y ventas las llaman directo, sin pasar por el gateway.
 */
@RestController
@RequestMapping("/lugares")
public class LugaresController {
    private final LugaresService service;

    public LugaresController(LugaresService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> crearLugar(@RequestHeader(value = "X-Rol", required = false) String rol,
                                                        @RequestBody Dtos.CrearLugarRequest req){
        exigirAdminUOrganizador(rol);
        Long id = service.crearLugar(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Dtos.LugarView> obtenerLugarPorId(@PathVariable Long id){
        return ResponseEntity.ok(service.obtenerLugarPorId(id));
    }

    @PostMapping("/{id}/sectores")
    public ResponseEntity<Map<String, Long>> crearSector(@RequestHeader(value = "X-Rol", required = false) String rol,
                                                         @RequestBody Dtos.CrearSectorRequest req,@PathVariable Long id){
        exigirAdminUOrganizador(rol);
        Long idSector = service.crearSector(id,req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id",idSector));
    }

    @GetMapping("/{id}/sectores")
    public ResponseEntity<List<Dtos.SectorView>> listarSectoresPorLugar(@PathVariable Long id){
        return ResponseEntity.ok(service.listarSectoresPorLugar(id));
    }

    private static void exigirAdminUOrganizador(String rol) {
        if (!"ADMINISTRADOR".equals(rol) && !"ORGANIZADOR".equals(rol)) {
            throw ApiException.forbidden("SOLO_ADMIN_U_ORGANIZADOR",
                    "Solo un administrador o un organizador puede dar de alta lugares y sectores");
        }
    }
}

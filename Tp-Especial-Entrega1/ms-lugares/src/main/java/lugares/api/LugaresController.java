package lugares.api;


import lugares.service.LugaresService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/lugares")
public class LugaresController {
    private final LugaresService service;

    public LugaresController(LugaresService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> crearLugar(@RequestBody Dtos.CrearLugarRequest req){
        Long id = service.crearLugar(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Dtos.LugarView> obtenerLugarPorId(@PathVariable Long id){
        return ResponseEntity.ok(service.obtenerLugarPorId(id));
    }
}

package lugares.api;


import lugares.service.LugaresService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/sectores")
public class SectoresController {

    private final LugaresService lugaresService;

    public SectoresController(LugaresService lugaresService) {
        this.lugaresService = lugaresService;
    }

    @GetMapping("/{id}/asientos")
    public ResponseEntity<List<Dtos.AsientoView>> listarAsientosPorSector(@PathVariable Long id){
        return ResponseEntity.ok(lugaresService.listarAsientosPorSector(id));
    }
}

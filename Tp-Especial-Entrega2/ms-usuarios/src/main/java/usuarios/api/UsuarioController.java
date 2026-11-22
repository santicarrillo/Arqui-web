package usuarios.api;

import usuarios.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> crear(@RequestBody Dtos.CrearUsuarioRequest req) {
        long id = service.crear(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @GetMapping("/{id}")
    public Dtos.UsuarioView obtener(@PathVariable long id) {
        return service.obtener(id);
    }

    @GetMapping
    public List<Dtos.UsuarioView> listar(@RequestParam(required = false) String rol) {
        return service.listar(rol);
    }
}

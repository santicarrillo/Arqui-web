package usuarios.api;

import usuarios.Domains.ApiException;
import usuarios.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * F1: el gateway valida el JWT y manda X-Usuario-Id / X-Rol. En el registro publico (sin token) esos
 * encabezados no llegan: el gateway los borra, asi nadie puede hacerse pasar por administrador.
 */
@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private static final String ADMIN = "ADMINISTRADOR";
    // Roles que solo puede dar de alta un administrador (CLIENTE es el unico del registro publico).
    private static final Set<String> ROLES_RESTRINGIDOS = Set.of("ORGANIZADOR", "ADMINISTRADOR", "ACCESO");

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> crear(@RequestHeader(value = "X-Rol", required = false) String rol,
                                                   @RequestBody Dtos.CrearUsuarioRequest req) {
        // sin rol o con uno inexistente sigue al service, que responde 400 ROL_INVALIDO
        // (Set.of no acepta contains(null): se chequea antes)
        if (req.rol() != null && ROLES_RESTRINGIDOS.contains(req.rol()) && !ADMIN.equals(rol))
            throw ApiException.forbidden("SOLO_ADMIN_CREA_ROLES",
                    "Solo un administrador puede crear usuarios con rol " + req.rol());
        long id = service.crear(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    // El propio usuario (para mostrar su nombre) o un administrador (por ejemplo, en las devoluciones).
    @GetMapping("/{id}")
    public Dtos.UsuarioView obtener(@RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
                                    @RequestHeader(value = "X-Rol", required = false) String rol,
                                    @PathVariable long id) {
        if (!ADMIN.equals(rol) && (usuarioId == null || usuarioId != id))
            throw ApiException.forbidden("NO_ES_TU_USUARIO", "Solo podes ver tus propios datos");
        return service.obtener(id);
    }

    @GetMapping
    public List<Dtos.UsuarioView> listar(@RequestHeader(value = "X-Rol", required = false) String rolSolicitante,
                                         @RequestParam(required = false) String rol) {
        if (!ADMIN.equals(rolSolicitante))
            throw ApiException.forbidden("SOLO_ADMIN", "Solo un administrador puede listar los usuarios");
        return service.listar(rol);
    }
}

package usuarios.service;

import usuarios.Domains.ApiException;
import usuarios.Domains.Usuario;
import usuarios.api.Dtos;
import usuarios.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class UsuarioService {

    // Roles de la consigna. En la Entrega 1 el rol es informativo (no hay auth);
    // el JWT y la autorizacion por rol son de la Entrega 2.
    private static final Set<String> ROLES = Set.of("CLIENTE", "ORGANIZADOR", "ADMINISTRADOR", "ACCESO");

    private final UsuarioRepository repo;

    public UsuarioService(UsuarioRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public long crear(Dtos.CrearUsuarioRequest r) {
        if (r.nombre() == null || r.nombre().isBlank())
            throw ApiException.badRequest("DATOS_INVALIDOS", "El nombre es obligatorio");
        if (r.email() == null || r.email().isBlank())
            throw ApiException.badRequest("DATOS_INVALIDOS", "El email es obligatorio");
        if (r.rol() == null || !ROLES.contains(r.rol()))
            throw ApiException.badRequest("ROL_INVALIDO", "El rol debe ser uno de: " + ROLES);
        if (repo.existeEmail(r.email()))
            throw ApiException.conflict("EMAIL_DUPLICADO", "Ya existe un usuario con ese email");
        return repo.crear(r);
    }

    public Dtos.UsuarioView obtener(long id) {
        return vista(obtenerOErr(id));
    }

    public List<Dtos.UsuarioView> listar(String rol) {
        return repo.listar(rol).stream().map(this::vista).toList();
    }

    private Usuario obtenerOErr(long id) {
        return repo.buscarPorId(id)
                .orElseThrow(() -> ApiException.notFound("USUARIO_NO_ENCONTRADO",
                        "El usuario " + id + " no existe"));
    }

    private Dtos.UsuarioView vista(Usuario u) {
        return new Dtos.UsuarioView(u.id(), u.nombre(), u.email(), u.rol());
    }
}

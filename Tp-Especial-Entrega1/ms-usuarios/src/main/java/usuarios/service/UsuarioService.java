package usuarios.service;

import usuarios.Domains.ApiException;
import usuarios.Domains.Credencial;
import usuarios.Domains.Usuario;
import usuarios.api.Dtos;
import usuarios.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class UsuarioService {

    // Roles de la consigna.
    private static final Set<String> ROLES = Set.of("CLIENTE", "ORGANIZADOR", "ADMINISTRADOR", "ACCESO");

    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;

    public UsuarioService(UsuarioRepository repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
    }

    @Transactional
    public long crear(Dtos.CrearUsuarioRequest r) {
        if (r.nombre() == null || r.nombre().isBlank())
            throw ApiException.badRequest("DATOS_INVALIDOS", "El nombre es obligatorio");
        if (r.email() == null || r.email().isBlank())
            throw ApiException.badRequest("DATOS_INVALIDOS", "El email es obligatorio");
        if (r.rol() == null || !ROLES.contains(r.rol()))
            throw ApiException.badRequest("ROL_INVALIDO", "El rol debe ser uno de: " + ROLES);
        if (r.password() == null || r.password().isBlank())
            throw ApiException.badRequest("DATOS_INVALIDOS", "La password es obligatoria");
        if (repo.existeEmail(r.email()))
            throw ApiException.conflict("EMAIL_DUPLICADO", "Ya existe un usuario con ese email");
        // Nunca guardamos la password en texto plano: solo el hash BCrypt.
        String hash = encoder.encode(r.password());
        return repo.crear(r, hash);
    }

    // Login: valida email + password y devuelve la credencial (id + rol) para armar el token.
    // Mensaje generico a proposito: no revelamos si fallo el email o la password.
    public Credencial validarCredenciales(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank())
            throw ApiException.badRequest("DATOS_INVALIDOS", "Email y password son obligatorios");
        Credencial c = repo.buscarCredencialPorEmail(email)
                .orElseThrow(() -> ApiException.unauthorized("CREDENCIALES_INVALIDAS", "Email o password incorrectos"));
        if (c.passwordHash() == null || !encoder.matches(password, c.passwordHash()))
            throw ApiException.unauthorized("CREDENCIALES_INVALIDAS", "Email o password incorrectos");
        return c;
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

package usuarios.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import usuarios.api.Dtos;
import usuarios.repository.UsuarioRepository;

/**
 * F1: el registro publico solo crea clientes, asi que el primer administrador se carga al arrancar.
 * Si ya existe un usuario con ADMIN_EMAIL no hace nada (los reinicios no lo duplican ni le cambian la clave).
 * Desde ese administrador se crean los organizadores y el personal de acceso.
 */
@Component
public class AdministradorInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdministradorInicial.class);

    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public AdministradorInicial(UsuarioRepository repo, PasswordEncoder encoder,
                                @Value("${ADMIN_EMAIL:admin@a.com}") String email,
                                @Value("${ADMIN_PASSWORD:1234}") String password) {
        this.repo = repo;
        this.encoder = encoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repo.existeEmail(email)) {
            log.info("Administrador inicial {} ya existe", email);
            return;
        }
        var admin = new Dtos.CrearUsuarioRequest("Admin", email, "ADMINISTRADOR", password);
        long id = repo.crear(admin, encoder.encode(password));
        log.info("Administrador inicial creado: {} (id {})", email, id);
    }
}

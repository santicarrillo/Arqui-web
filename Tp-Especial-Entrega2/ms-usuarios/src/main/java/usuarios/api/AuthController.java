package usuarios.api;

import usuarios.Domains.Credencial;
import usuarios.service.JwtService;
import usuarios.service.UsuarioService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/usuarios")
public class AuthController {

    private final UsuarioService usuarios;
    private final JwtService jwt;

    public AuthController(UsuarioService usuarios, JwtService jwt) {
        this.usuarios = usuarios;
        this.jwt = jwt;
    }

    // POST /usuarios/login -> valida credenciales y devuelve el token.
    @PostMapping("/login")
    public Dtos.TokenView login(@RequestBody Dtos.LoginRequest req) {
        Credencial c = usuarios.validarCredenciales(req.email(), req.password());
        String token = jwt.generar(c.id(), c.rol());
        return new Dtos.TokenView(token, "Bearer", c.id(), c.rol(), jwt.expiracionSegundos());
    }
}

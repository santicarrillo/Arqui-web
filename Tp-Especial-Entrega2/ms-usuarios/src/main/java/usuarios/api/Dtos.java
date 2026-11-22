package usuarios.api;

public final class Dtos {
    private Dtos() {}

    // ---------- request (lo que entra) ----------
    public record CrearUsuarioRequest(String nombre, String email, String rol, String password) {}
    public record LoginRequest(String email, String password) {}

    // ---------- response (lo que sale) ----------
    public record UsuarioView(Long id, String nombre, String email, String rol) {}
    public record TokenView(String token, String tipo, Long usuarioId, String rol, long expiraEnSegundos) {}
}

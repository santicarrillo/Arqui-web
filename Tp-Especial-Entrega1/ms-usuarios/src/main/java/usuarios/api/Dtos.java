package usuarios.api;

public final class Dtos {
    private Dtos() {}

    // ---------- request (lo que entra) ----------
    public record CrearUsuarioRequest(String nombre, String email, String rol) {}

    // ---------- response (lo que sale) ----------
    public record UsuarioView(Long id, String nombre, String email, String rol) {}
}

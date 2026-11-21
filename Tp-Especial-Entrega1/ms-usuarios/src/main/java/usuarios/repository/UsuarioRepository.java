package usuarios.repository;

import usuarios.Domains.Credencial;
import usuarios.Domains.Usuario;
import usuarios.api.Dtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class UsuarioRepository {

    private final JdbcTemplate jdbc;

    public UsuarioRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Usuario> USUARIO = (rs, n) -> new Usuario(
            rs.getLong("id"),
            rs.getString("nombre"),
            rs.getString("email"),
            rs.getString("rol")
    );

    public long crear(Dtos.CrearUsuarioRequest r, String passwordHash) {
        return jdbc.queryForObject(
                "INSERT INTO usuario (nombre, email, rol, password_hash) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, r.nombre(), r.email(), r.rol(), passwordHash);
    }

    public Optional<Usuario> buscarPorId(long id) {
        return jdbc.query("SELECT * FROM usuario WHERE id = ?", USUARIO, id).stream().findFirst();
    }

    // Login: trae id, rol y hash para comparar. Case-insensitive sobre el email.
    public Optional<Credencial> buscarCredencialPorEmail(String email) {
        return jdbc.query(
                "SELECT id, rol, password_hash FROM usuario WHERE LOWER(email) = LOWER(?)",
                (rs, n) -> new Credencial(rs.getLong("id"), rs.getString("rol"), rs.getString("password_hash")),
                email).stream().findFirst();
    }

    public boolean existeEmail(String email) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuario WHERE LOWER(email) = LOWER(?)", Integer.class, email);
        return n != null && n > 0;
    }

    public List<Usuario> listar(String rol) {
        StringBuilder sql = new StringBuilder("SELECT * FROM usuario WHERE 1 = 1 ");
        List<Object> p = new ArrayList<>();
        if (rol != null && !rol.isBlank()) { sql.append("AND rol = ? "); p.add(rol); }
        sql.append("ORDER BY id");
        return jdbc.query(sql.toString(), USUARIO, p.toArray());
    }
}

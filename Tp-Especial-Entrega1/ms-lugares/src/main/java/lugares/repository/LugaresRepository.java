package lugares.repository;


import lugares.Domains.Asiento;
import lugares.Domains.Lugar;
import lugares.Domains.Sector;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class LugaresRepository {

    private final JdbcTemplate jdbc;

    public LugaresRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Lugar> MAPPER_LUGAR = (rs,n)-> new Lugar(
            rs.getLong("id"),
            rs.getString("nombre"),
            rs.getString("direccion"),
            rs.getDouble("latitud"),
            rs.getDouble("longitud"),
            rs.getString("ciudad")
    );

    private static final RowMapper<Sector> MAPPER_SECTOR=(rs,n) -> new Sector(
            rs.getLong("id"),
            rs.getLong("lugar_id"),
            rs.getString("nombre"),
            rs.getBoolean("numerado"),
            rs.getInt("capacidad")
    );


    public Long guardarLugar(String nombre, String direccion, Double latitud, Double longitud, String ciudad){
        String sql= """
                INSERT INTO lugar (nombre,direccion,latitud,longitud,ciudad)
                VALUES(?,?,?,?,?)
                RETURNING id;
                """;
        return jdbc.queryForObject(sql,Long.class,nombre,direccion,latitud,longitud,ciudad);
    }

    public Optional<Lugar> buscarLugarPorId(Long id){
        String sql= """
                SELECT * 
                FROM lugar
                WHERE id=?
                """;
        return jdbc.query(sql,MAPPER_LUGAR,id).stream().findFirst();
    }

    public Long guardarSector(Long lugar_id,String nombre,boolean numerado,int capacidad){
        String sql= """
            INSERT INTO sector (lugar_id, nombre, numerado, capacidad)
            VALUES (?, ?, ?, ?)
            RETURNING id;
            """;
        return jdbc.queryForObject(sql,Long.class,lugar_id, nombre, numerado, capacidad);
    }

    public List<Sector> listarSectoresPorLugar(Long lugarId) {
        String sql = """
            SELECT * 
            FROM sector 
            WHERE lugar_id = ? 
            ORDER BY id
            """;
        return jdbc.query(sql, MAPPER_SECTOR, lugarId);
    }

    public void guardarAsientosBatch(List<Asiento> asientos) {
        String sql = "INSERT INTO asiento (sector_id, fila, numero) VALUES (?, ?, ?)";
        jdbc.batchUpdate(sql, asientos, asientos.size(), (ps, asiento) -> {
            ps.setLong(1, asiento.sectorId());
            ps.setString(2, asiento.fila());
            ps.setString(3, asiento.numero());
        });
    }
}

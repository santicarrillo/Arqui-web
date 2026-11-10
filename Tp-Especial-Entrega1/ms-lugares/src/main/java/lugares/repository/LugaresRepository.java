package lugares.repository;


import lugares.Domains.Lugar;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

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
}

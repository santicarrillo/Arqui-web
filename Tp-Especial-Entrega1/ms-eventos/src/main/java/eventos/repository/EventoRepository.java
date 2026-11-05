package eventos.repository;

import eventos.Domains.Evento;
import eventos.Domains.EventoSector;
import eventos.api.Dtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class EventoRepository {

    private final JdbcTemplate jdbc;

    public EventoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- mapeos: una fila de la base -> un objeto ----
    private static final RowMapper<Evento> EVENTO = (rs, n) -> new Evento(
            rs.getLong("id"),
            rs.getString("nombre"),
            rs.getString("descripcion"),
            rs.getString("tipo"),
            rs.getTimestamp("fecha_hora").toLocalDateTime(),
            rs.getString("estado"),
            rs.getLong("lugar_id"),
            rs.getString("ciudad"),
            rs.getLong("organizador_id"),
            rs.getInt("limite_por_cliente"),
            rs.getString("motivo_cancelacion")
    );

    private static final RowMapper<EventoSector> SECTOR = (rs, n) -> new EventoSector(
            rs.getLong("evento_id"),
            rs.getLong("sector_id"),
            rs.getBoolean("numerado"),
            rs.getBigDecimal("precio")
    );

    // ---- evento ----
    public long crear(Dtos.CrearEventoRequest r) {
        return jdbc.queryForObject("""
                INSERT INTO evento (nombre, descripcion, tipo, fecha_hora, estado,
                                    lugar_id, ciudad, organizador_id, limite_por_cliente)
                VALUES (?, ?, ?, ?, 'BORRADOR', ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                r.nombre(), r.descripcion(), r.tipo(), Timestamp.valueOf(r.fechaHora()),
                r.lugarId(), r.ciudad(), r.organizadorId(),
                r.limitePorCliente() == null ? 6 : r.limitePorCliente());
    }

    public Optional<Evento> buscarPorId(long id) {
        return jdbc.query("SELECT * FROM evento WHERE id = ?", EVENTO, id).stream().findFirst();
    }

    public void cambiarEstado(long id, String estado) {
        jdbc.update("UPDATE evento SET estado = ? WHERE id = ?", estado, id);
    }

    public void cancelar(long id, String motivo) {
        jdbc.update("UPDATE evento SET estado = 'CANCELADO', motivo_cancelacion = ? WHERE id = ?", motivo, id);
    }

    // ---- evento_sector (precios por sector) ----
    public void guardarSector(long eventoId, Dtos.SectorPrecioRequest s) {
        jdbc.update("""
                INSERT INTO evento_sector (evento_id, sector_id, numerado, precio)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (evento_id, sector_id)
                DO UPDATE SET numerado = EXCLUDED.numerado, precio = EXCLUDED.precio
                """, eventoId, s.sectorId(), s.numerado(), s.precio());
    }

    public List<EventoSector> sectoresDe(long eventoId) {
        return jdbc.query("SELECT * FROM evento_sector WHERE evento_id = ?", SECTOR, eventoId);
    }

    public boolean tieneSectores(long eventoId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM evento_sector WHERE evento_id = ?", Integer.class, eventoId);
        return n != null && n > 0;
    }

    // ---- participantes ----
    public long crearParticipante(String nombre, String tipo) {
        return jdbc.queryForObject(
                "INSERT INTO participante (nombre, tipo) VALUES (?, ?) RETURNING id",
                Long.class, nombre, tipo);
    }

    public void vincularParticipante(long eventoId, long participanteId) {
        jdbc.update("""
                INSERT INTO evento_participante (evento_id, participante_id)
                VALUES (?, ?) ON CONFLICT DO NOTHING
                """, eventoId, participanteId);
    }
}
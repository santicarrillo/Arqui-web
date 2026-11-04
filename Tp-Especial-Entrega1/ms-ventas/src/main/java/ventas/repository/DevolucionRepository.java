package ventas.repository;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ventas.Domains.Devolucion;

/** Acceso a la tabla devolucion y a lo que cambia cuando una entrada se devuelve. */
@Repository
public class DevolucionRepository {

    /** Una entrada junto con los datos de su compra que hacen falta para devolverla. */
    public record EntradaDeCompra(long entradaId, long compraId, long clienteId, long eventoId,
                                  LocalDateTime eventoFechaHora, String compraEstado,
                                  long sectorId, Long asientoId, BigDecimal precio, String estado) {
    }

    private static final RowMapper<Devolucion> DEVOLUCION = (rs, n) -> new Devolucion(
            rs.getLong("id"),
            rs.getLong("detalle_compra_id"),
            rs.getObject("fecha_solicitud", LocalDateTime.class),
            rs.getString("estado"),
            rs.getObject("admin_id", Long.class),
            rs.getObject("fecha_resolucion", LocalDateTime.class),
            rs.getString("motivo"));

    private static final RowMapper<EntradaDeCompra> ENTRADA = (rs, n) -> new EntradaDeCompra(
            rs.getLong("entrada_id"),
            rs.getLong("compra_id"),
            rs.getLong("cliente_id"),
            rs.getLong("evento_id"),
            rs.getObject("evento_fecha_hora", LocalDateTime.class),
            rs.getString("compra_estado"),
            rs.getLong("sector_id"),
            rs.getObject("asiento_id", Long.class),
            rs.getBigDecimal("precio_unitario"),
            rs.getString("estado"));

    private final JdbcClient jdbc;

    public DevolucionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ entrada

    /**
     * Lee la entrada y la bloquea hasta el fin de la transaccion. Asi, si el cliente pide la devolucion
     * justo cuando el evento se cancela, una de las dos operaciones espera a que termine la otra.
     */
    public Optional<EntradaDeCompra> entradaBloqueando(long entradaId) {
        return jdbc.sql("""
                        SELECT d.id AS entrada_id, d.compra_id, c.cliente_id, c.evento_id, c.evento_fecha_hora,
                               c.estado AS compra_estado, d.sector_id, d.asiento_id, d.precio_unitario, d.estado
                        FROM detalle_compra d JOIN compra c ON c.id = d.compra_id
                        WHERE d.id = :id
                        FOR UPDATE OF d
                        """)
                .param("id", entradaId).query(ENTRADA).optional();
    }

    /** VALIDA -> DEVUELTA. Devuelve false si la entrada ya no estaba VALIDA. */
    public boolean marcarEntradaDevuelta(long entradaId) {
        return jdbc.sql("UPDATE detalle_compra SET estado = 'DEVUELTA' WHERE id = :id AND estado = 'VALIDA'")
                .param("id", entradaId).update() == 1;
    }

    /** El asiento vendido vuelve a estar libre, para que se pueda vender de nuevo. */
    public void liberarAsientoVendido(long eventoId, long asientoId, long compraId) {
        jdbc.sql("""
                        UPDATE evento_asiento SET estado = 'LIBRE', compra_id = NULL
                        WHERE evento_id = :evento AND asiento_id = :asiento
                          AND compra_id = :compra AND estado = 'VENDIDO'
                        """)
                .param("evento", eventoId).param("asiento", asientoId).param("compra", compraId).update();
    }

    /** Una zona sin numerar recupera un lugar. */
    public void liberarLugarDeZona(long eventoId, long sectorId) {
        jdbc.sql("""
                        UPDATE cupo_sector SET ocupados = ocupados - 1
                        WHERE evento_id = :evento AND sector_id = :sector AND ocupados >= 1
                        """)
                .param("evento", eventoId).param("sector", sectorId).update();
    }

    /** Entradas que hay que devolver si el evento se cancela: las validas de compras pagadas. */
    public List<Long> entradasVigentesDeEvento(long eventoId) {
        return jdbc.sql("""
                        SELECT d.id FROM detalle_compra d JOIN compra c ON c.id = d.compra_id
                        WHERE c.evento_id = :evento AND c.estado = 'PAGADA' AND d.estado = 'VALIDA'
                        ORDER BY d.id
                        """)
                .param("evento", eventoId).query(Long.class).list();
    }

    public List<Long> comprasPendientesDeEvento(long eventoId) {
        return jdbc.sql("SELECT id FROM compra WHERE evento_id = :evento AND estado = 'PENDIENTE' ORDER BY id")
                .param("evento", eventoId).query(Long.class).list();
    }

    // ---------------------------------------------------------------- devolucion

    public Optional<Devolucion> porId(long id) {
        return jdbc.sql("SELECT * FROM devolucion WHERE id = :id")
                .param("id", id).query(DEVOLUCION).optional();
    }

    /** La devolucion activa (PENDIENTE o APROBADA) de una entrada, si tiene. Puede haber una sola. */
    public Optional<Devolucion> activaDeEntrada(long entradaId) {
        return jdbc.sql("""
                        SELECT * FROM devolucion
                        WHERE detalle_compra_id = :entrada AND estado IN ('PENDIENTE', 'APROBADA')
                        """)
                .param("entrada", entradaId).query(DEVOLUCION).optional();
    }

    public List<Devolucion> porEstado(String estado) {
        return jdbc.sql("SELECT * FROM devolucion WHERE estado = :estado ORDER BY fecha_solicitud, id")
                .param("estado", estado).query(DEVOLUCION).list();
    }

    /**
     * Crea la devolucion PENDIENTE. Si la entrada ya tiene una activa, el indice unico devolucion_activa_uk
     * hace fallar el INSERT (DuplicateKeyException).
     */
    public long insertar(long entradaId, String motivo, LocalDateTime ahora) {
        return jdbc.sql("""
                        INSERT INTO devolucion (detalle_compra_id, fecha_solicitud, estado, motivo)
                        VALUES (:entrada, :ahora, 'PENDIENTE', :motivo)
                        RETURNING id
                        """)
                .param("entrada", entradaId).param("ahora", ahora).param("motivo", motivo)
                .query(Long.class).single();
    }

    /** PENDIENTE -> APROBADA o RECHAZADA. Devuelve false si ya estaba resuelta. adminId es nulo si la resolvio el sistema. */
    public boolean resolver(long devolucionId, String nuevoEstado, Long adminId, LocalDateTime ahora) {
        return jdbc.sql("""
                        UPDATE devolucion SET estado = :estado, admin_id = :admin, fecha_resolucion = :ahora
                        WHERE id = :id AND estado = 'PENDIENTE'
                        """)
                .param("estado", nuevoEstado)
                .param("admin", adminId, Types.BIGINT)
                .param("ahora", ahora)
                .param("id", devolucionId)
                .update() == 1;
    }
}

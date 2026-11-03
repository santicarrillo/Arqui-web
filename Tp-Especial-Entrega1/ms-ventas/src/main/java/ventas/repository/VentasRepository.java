package ventas.repository;

import ventas.Domains.Compra;
import ventas.Domains.Detalle;
import ventas.api.Dtos;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class VentasRepository {
    public record Cupo(int capacidad, int ocupados) {
    }
        private static final RowMapper<Compra> COMPRA = (rs, n) -> new Compra(
                rs.getLong("id"),
                rs.getLong("cliente_id"),
                rs.getLong("evento_id"),
                rs.getObject("evento_fecha_hora", LocalDateTime.class),
                rs.getString("estado"),
                rs.getString("medio_pago"),
                rs.getBigDecimal("total"),
                rs.getString("clave_idempotencia"),
                rs.getObject("fecha_compra", LocalDateTime.class),
                rs.getObject("vence_en", LocalDateTime.class));

    private static final RowMapper<Detalle> DETALLE = (rs, n) -> new Detalle(
            rs.getLong("id"),
            rs.getLong("compra_id"),
            rs.getLong("sector_id"),
            rs.getObject("asiento_id", Long.class),
            rs.getBigDecimal("precio_unitario"),
            rs.getString("codigo_qr"),
            rs.getString("estado"));
    private static final RowMapper<Dtos.EntradaView> ENTRADA = (rs, n) -> new Dtos.EntradaView(
            rs.getLong("entrada_id"),
            rs.getLong("compra_id"),
            rs.getLong("evento_id"),
            rs.getLong("sector_id"),
            rs.getObject("asiento_id", Long.class),
            rs.getBigDecimal("precio_unitario"),
            rs.getString("codigo_qr"),
            rs.getString("estado"));

    private final JdbcClient jdbc;

    public VentasRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }
    // ---------------------------------------------------------------- compra

    public Optional<Compra> porClave(String clave) {
        return jdbc.sql("SELECT * FROM compra WHERE clave_idempotencia = :clave")
                .param("clave", clave).query(COMPRA).optional();
    }

    public Optional<Compra> porId(long id) {
        return jdbc.sql("SELECT * FROM compra WHERE id = :id")
                .param("id", id).query(COMPRA).optional();
    }

    /** Lee la compra y la bloquea hasta el fin de la transaccion, para que nadie la procese a la vez. */
    public Optional<Compra> porIdBloqueando(long id) {
        return jdbc.sql("SELECT * FROM compra WHERE id = :id FOR UPDATE")
                .param("id", id).query(COMPRA).optional();
    }

    /** Inserta la compra PENDIENTE. Si la clave de idempotencia ya existe, no inserta y devuelve vacio. */
    public Optional<Long> insertarCompra(long clienteId, long eventoId, LocalDateTime eventoFechaHora,
                                         BigDecimal total, String clave, LocalDateTime ahora, LocalDateTime venceEn) {
        return jdbc.sql("""
                        INSERT INTO compra (cliente_id, evento_id, evento_fecha_hora, estado, total,
                                            clave_idempotencia, fecha_compra, vence_en)
                        VALUES (:cliente, :evento, :fechaHora, 'PENDIENTE', :total, :clave, :ahora, :vence)
                        ON CONFLICT (clave_idempotencia) DO NOTHING
                        RETURNING id
                        """)
                .param("cliente", clienteId)
                .param("evento", eventoId)
                .param("fechaHora", eventoFechaHora)
                .param("total", total)
                .param("clave", clave)
                .param("ahora", ahora)
                .param("vence", venceEn)
                .query(Long.class).optional();
    }

    /** Entradas que el cliente ya tiene (o esta por tener) para el evento: cuentan para el limite. */
    public long contarEntradasActivas(long clienteId, long eventoId) {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM detalle_compra d
                        JOIN compra c ON c.id = d.compra_id
                        WHERE c.cliente_id = :cliente AND c.evento_id = :evento
                          AND c.estado IN ('PENDIENTE', 'PAGADA') AND d.estado <> 'DEVUELTA'
                        """)
                .param("cliente", clienteId).param("evento", eventoId)
                .query(Long.class).single();
    }

    /** PENDIENTE -> nuevoEstado. Devuelve false si la compra ya no estaba PENDIENTE. */
    public boolean cambiarEstadoDesdePendiente(long compraId, String nuevoEstado) {
        return jdbc.sql("UPDATE compra SET estado = :estado WHERE id = :id AND estado = 'PENDIENTE'")
                .param("estado", nuevoEstado).param("id", compraId).update() == 1;
    }

    public boolean marcarPagada(long compraId, String medioPago) {
        return jdbc.sql("""
                        UPDATE compra SET estado = 'PAGADA', medio_pago = :medio
                        WHERE id = :id AND estado = 'PENDIENTE'
                        """)
                .param("medio", medioPago).param("id", compraId).update() == 1;
    }

    public List<Long> pendientesVencidas(LocalDateTime ahora) {
        return jdbc.sql("SELECT id FROM compra WHERE estado = 'PENDIENTE' AND vence_en <= :ahora ORDER BY id")
                .param("ahora", ahora).query(Long.class).list();
    }

    // --------------------------------------------------------------- detalle

    public long insertarDetalle(long compraId, long sectorId, Long asientoId, BigDecimal precio) {
        return jdbc.sql("""
                        INSERT INTO detalle_compra (compra_id, sector_id, asiento_id, precio_unitario, estado)
                        VALUES (:compra, :sector, :asiento, :precio, 'VALIDA')
                        RETURNING id
                        """)
                .param("compra", compraId)
                .param("sector", sectorId)
                .param("asiento", asientoId, Types.BIGINT)
                .param("precio", precio)
                .query(Long.class).single();
    }

    public List<Detalle> detalles(long compraId) {
        return jdbc.sql("SELECT * FROM detalle_compra WHERE compra_id = :compra ORDER BY id")
                .param("compra", compraId).query(DETALLE).list();
    }

    public void asignarQr(long detalleId, String codigoQr) {
        jdbc.sql("UPDATE detalle_compra SET codigo_qr = :qr WHERE id = :id")
                .param("qr", codigoQr).param("id", detalleId).update();
    }

    public List<Dtos.EntradaView> entradasDeCliente(long clienteId) {
        return jdbc.sql(ENTRADAS_SQL + " WHERE c.cliente_id = :cliente AND c.estado = 'PAGADA' ORDER BY d.id")
                .param("cliente", clienteId).query(ENTRADA).list();
    }

    public Optional<Dtos.EntradaView> entradaDeCliente(long entradaId, long clienteId) {
        return jdbc.sql(ENTRADAS_SQL + " WHERE d.id = :id AND c.cliente_id = :cliente AND c.estado = 'PAGADA'")
                .param("id", entradaId).param("cliente", clienteId).query(ENTRADA).optional();
    }

    private static final String ENTRADAS_SQL = """
            SELECT d.id AS entrada_id, d.compra_id, c.evento_id, d.sector_id, d.asiento_id,
                   d.precio_unitario, d.codigo_qr, d.estado
            FROM detalle_compra d JOIN compra c ON c.id = d.compra_id
            """;

    // ------------------------------------------------- reserva y venta de lugares

    /** LIBRE -> RESERVADO en una sola sentencia. false = alguien llego antes. */
    public boolean tomarAsiento(long eventoId, long sectorId, long asientoId, long compraId) {
        return jdbc.sql("""
                        UPDATE evento_asiento SET estado = 'RESERVADO', compra_id = :compra
                        WHERE evento_id = :evento AND sector_id = :sector AND asiento_id = :asiento
                          AND estado = 'LIBRE'
                        """)
                .param("compra", compraId).param("evento", eventoId)
                .param("sector", sectorId).param("asiento", asientoId)
                .update() == 1;
    }

    /** Suma al cupo de una zona solo si queda lugar. false = no alcanza. */
    public boolean tomarCupo(long eventoId, long sectorId, int cantidad) {
        return jdbc.sql("""
                        UPDATE cupo_sector SET ocupados = ocupados + :cantidad
                        WHERE evento_id = :evento AND sector_id = :sector
                          AND ocupados + :cantidad <= capacidad
                        """)
                .param("cantidad", cantidad).param("evento", eventoId).param("sector", sectorId)
                .update() == 1;
    }

    public int liberarAsientos(long compraId) {
        return jdbc.sql("""
                        UPDATE evento_asiento SET estado = 'LIBRE', compra_id = NULL
                        WHERE compra_id = :compra AND estado = 'RESERVADO'
                        """)
                .param("compra", compraId).update();
    }

    public void liberarCupo(long eventoId, long sectorId, int cantidad) {
        jdbc.sql("""
                        UPDATE cupo_sector SET ocupados = ocupados - :cantidad
                        WHERE evento_id = :evento AND sector_id = :sector AND ocupados >= :cantidad
                        """)
                .param("cantidad", cantidad).param("evento", eventoId).param("sector", sectorId)
                .update();
    }

    public int venderAsientos(long compraId) {
        return jdbc.sql("""
                        UPDATE evento_asiento SET estado = 'VENDIDO'
                        WHERE compra_id = :compra AND estado = 'RESERVADO'
                        """)
                .param("compra", compraId).update();
    }

    // ---------------------------------------------------------- disponibilidad

    public void insertarAsiento(long eventoId, long sectorId, long asientoId) {
        jdbc.sql("""
                        INSERT INTO evento_asiento (evento_id, sector_id, asiento_id, estado)
                        VALUES (:evento, :sector, :asiento, 'LIBRE')
                        ON CONFLICT (evento_id, asiento_id) DO NOTHING
                        """)
                .param("evento", eventoId).param("sector", sectorId).param("asiento", asientoId).update();
    }

    public void crearCupo(long eventoId, long sectorId, int capacidad) {
        jdbc.sql("""
                        INSERT INTO cupo_sector (evento_id, sector_id, capacidad, ocupados)
                        VALUES (:evento, :sector, :capacidad, 0)
                        ON CONFLICT (evento_id, sector_id) DO NOTHING
                        """)
                .param("evento", eventoId).param("sector", sectorId).param("capacidad", capacidad).update();
    }

    /** Cantidad de asientos de un sector por estado (LIBRE, RESERVADO, VENDIDO). */
    public Map<String, Long> asientosPorEstado(long eventoId, long sectorId) {
        List<Map.Entry<String, Long>> filas = jdbc.sql("""
                        SELECT estado, COUNT(*) AS n FROM evento_asiento
                        WHERE evento_id = :evento AND sector_id = :sector GROUP BY estado
                        """)
                .param("evento", eventoId).param("sector", sectorId)
                .query((rs, i) -> Map.entry(rs.getString("estado"), rs.getLong("n"))).list();
        Map<String, Long> resultado = new HashMap<>();
        filas.forEach(f -> resultado.put(f.getKey(), f.getValue()));
        return resultado;
    }

    public List<Long> asientosLibres(long eventoId, long sectorId) {
        return jdbc.sql("""
                        SELECT asiento_id FROM evento_asiento
                        WHERE evento_id = :evento AND sector_id = :sector AND estado = 'LIBRE'
                        ORDER BY asiento_id
                        """)
                .param("evento", eventoId).param("sector", sectorId).query(Long.class).list();
    }

    public Optional<Cupo> cupo(long eventoId, long sectorId) {
        return jdbc.sql("SELECT capacidad, ocupados FROM cupo_sector WHERE evento_id = :evento AND sector_id = :sector")
                .param("evento", eventoId).param("sector", sectorId)
                .query((rs, i) -> new Cupo(rs.getInt("capacidad"), rs.getInt("ocupados"))).optional();
    }
}

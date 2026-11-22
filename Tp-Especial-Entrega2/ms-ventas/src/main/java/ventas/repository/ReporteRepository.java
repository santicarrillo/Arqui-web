package ventas.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/** Consultas de los reportes del organizador (F7). Solo lectura. */
@Repository
public class ReporteRepository {

    public record CapacidadSector(long sectorId, boolean numerado, int capacidad) {
    }

    public record VentasSector(long sectorId, long vendidas, long ingresados, long devueltas, BigDecimal recaudacion) {
    }

    private final JdbcClient jdbc;

    public ReporteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Capacidad de cada sector del evento: el cupo de las zonas y la cantidad de asientos de los numerados. */
    public List<CapacidadSector> capacidades(long eventoId) {
        return jdbc.sql("""
                        SELECT sector_id, FALSE AS numerado, capacidad FROM cupo_sector WHERE evento_id = :evento
                        UNION ALL
                        SELECT sector_id, TRUE AS numerado, COUNT(*) AS capacidad FROM evento_asiento
                        WHERE evento_id = :evento GROUP BY sector_id
                        ORDER BY sector_id
                        """)
                .param("evento", eventoId)
                .query((rs, n) -> new CapacidadSector(rs.getLong("sector_id"), rs.getBoolean("numerado"),
                        rs.getInt("capacidad")))
                .list();
    }

    /**
     * Entradas de compras pagadas, por sector. Vendidas = las que siguen valiendo (VALIDA o USADA);
     * ingresados = las USADA (ya pasaron por la puerta); la recaudacion descuenta las devueltas.
     */
    public List<VentasSector> ventasPorSector(long eventoId) {
        return jdbc.sql("""
                        SELECT d.sector_id,
                               COUNT(*) FILTER (WHERE d.estado IN ('VALIDA', 'USADA')) AS vendidas,
                               COUNT(*) FILTER (WHERE d.estado = 'USADA')              AS ingresados,
                               COUNT(*) FILTER (WHERE d.estado = 'DEVUELTA')           AS devueltas,
                               COALESCE(SUM(d.precio_unitario) FILTER (WHERE d.estado IN ('VALIDA', 'USADA')), 0)
                                                                                       AS recaudacion
                        FROM detalle_compra d JOIN compra c ON c.id = d.compra_id
                        WHERE c.evento_id = :evento AND c.estado = 'PAGADA'
                        GROUP BY d.sector_id
                        """)
                .param("evento", eventoId)
                .query((rs, n) -> new VentasSector(rs.getLong("sector_id"), rs.getLong("vendidas"),
                        rs.getLong("ingresados"), rs.getLong("devueltas"), rs.getBigDecimal("recaudacion")))
                .list();
    }
}

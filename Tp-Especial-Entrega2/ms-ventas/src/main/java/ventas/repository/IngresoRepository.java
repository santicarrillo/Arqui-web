package ventas.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/** Acceso a datos del control de acceso: validar un QR en la puerta del evento. */
@Repository
public class IngresoRepository {

    /** Lo que hace falta saber de una entrada para decidir si puede ingresar. */
    public record EntradaParaIngreso(long entradaId, long eventoId, long sectorId, Long asientoId,
                                     String estado, String compraEstado, LocalDateTime usadoEn) {
    }

    private static final RowMapper<EntradaParaIngreso> ENTRADA = (rs, n) -> new EntradaParaIngreso(
            rs.getLong("entrada_id"),
            rs.getLong("evento_id"),
            rs.getLong("sector_id"),
            rs.getObject("asiento_id", Long.class),
            rs.getString("estado"),
            rs.getString("compra_estado"),
            rs.getObject("usado_en", LocalDateTime.class));

    private final JdbcClient jdbc;

    public IngresoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * VALIDA -> USADA en una sola sentencia, con todas las condiciones adentro del WHERE: el QR existe, es de
     * ese evento, la compra esta pagada y la entrada todavia no se uso. Es la misma idea que la no doble venta:
     * si dos molinetes leen el mismo QR a la vez, PostgreSQL bloquea la fila y el segundo UPDATE ya no la
     * encuentra VALIDA, asi que afecta 0 filas. Devuelve el id de la entrada si la marco.
     */
    public Optional<Long> marcarUsada(String codigoQr, long eventoId, long personalId, LocalDateTime ahora) {
        return jdbc.sql("""
                        UPDATE detalle_compra d
                           SET estado = 'USADA', usado_en = :ahora, usado_por = :personal
                          FROM compra c
                         WHERE c.id = d.compra_id
                           AND d.codigo_qr = :qr
                           AND c.evento_id = :evento
                           AND c.estado = 'PAGADA'
                           AND d.estado = 'VALIDA'
                        RETURNING d.id
                        """)
                .param("ahora", ahora)
                .param("personal", personalId)
                .param("qr", codigoQr)
                .param("evento", eventoId)
                .query(Long.class).optional();
    }

    /** Lee la entrada de un QR, para informar por que no puede ingresar o para armar la respuesta. */
    public Optional<EntradaParaIngreso> porQr(String codigoQr) {
        return jdbc.sql("""
                        SELECT d.id AS entrada_id, c.evento_id, d.sector_id, d.asiento_id, d.estado,
                               c.estado AS compra_estado, d.usado_en
                        FROM detalle_compra d JOIN compra c ON c.id = d.compra_id
                        WHERE d.codigo_qr = :qr
                        """)
                .param("qr", codigoQr).query(ENTRADA).optional();
    }
}

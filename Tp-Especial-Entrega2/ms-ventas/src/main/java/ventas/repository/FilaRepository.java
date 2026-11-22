package ventas.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Acceso a datos de la fila virtual (F10): la configuracion de cada evento y los turnos de los clientes. */
@Repository
public class FilaRepository {

    public record Fila(long eventoId, boolean activa, int simultaneos, int minutosTurno, long ultimoNumero) {
    }

    public record Turno(long eventoId, long clienteId, long numero, String estado, LocalDateTime pedidoEn,
                        LocalDateTime habilitadoEn, LocalDateTime venceEn) {
    }

    private static final RowMapper<Fila> FILA = (rs, n) -> new Fila(
            rs.getLong("evento_id"),
            rs.getBoolean("activa"),
            rs.getInt("simultaneos"),
            rs.getInt("minutos_turno"),
            rs.getLong("ultimo_numero"));

    private static final RowMapper<Turno> TURNO = (rs, n) -> new Turno(
            rs.getLong("evento_id"),
            rs.getLong("cliente_id"),
            rs.getLong("numero"),
            rs.getString("estado"),
            rs.getObject("pedido_en", LocalDateTime.class),
            rs.getObject("habilitado_en", LocalDateTime.class),
            rs.getObject("vence_en", LocalDateTime.class));

    private final JdbcClient jdbc;

    public FilaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- configuracion

    /** Crea la fila del evento o, si ya existia, la vuelve a activar con la nueva configuracion. */
    public void activar(long eventoId, int simultaneos, int minutosTurno, LocalDateTime ahora) {
        jdbc.sql("""
                        INSERT INTO fila_virtual (evento_id, activa, simultaneos, minutos_turno, actualizada_en)
                        VALUES (:evento, TRUE, :simultaneos, :minutos, :ahora)
                        ON CONFLICT (evento_id) DO UPDATE
                           SET activa = TRUE, simultaneos = EXCLUDED.simultaneos,
                               minutos_turno = EXCLUDED.minutos_turno, actualizada_en = EXCLUDED.actualizada_en
                        """)
                .param("evento", eventoId).param("simultaneos", simultaneos)
                .param("minutos", minutosTurno).param("ahora", ahora)
                .update();
    }

    public void desactivar(long eventoId, LocalDateTime ahora) {
        jdbc.sql("UPDATE fila_virtual SET activa = FALSE, actualizada_en = :ahora WHERE evento_id = :evento")
                .param("ahora", ahora).param("evento", eventoId).update();
    }

    public Optional<Fila> fila(long eventoId) {
        return jdbc.sql("SELECT * FROM fila_virtual WHERE evento_id = :evento")
                .param("evento", eventoId).query(FILA).optional();
    }

    /** Bloquea la fila del evento hasta el fin de la transaccion: dos instancias no la hacen avanzar a la vez. */
    public Optional<Fila> filaBloqueando(long eventoId) {
        return jdbc.sql("SELECT * FROM fila_virtual WHERE evento_id = :evento FOR UPDATE")
                .param("evento", eventoId).query(FILA).optional();
    }

    public List<Long> eventosConFilaActiva() {
        return jdbc.sql("SELECT evento_id FROM fila_virtual WHERE activa ORDER BY evento_id").query(Long.class).list();
    }

    // ---------------------------------------------------------------- turnos

    public Optional<Turno> turno(long eventoId, long clienteId) {
        return jdbc.sql("SELECT * FROM turno_fila WHERE evento_id = :evento AND cliente_id = :cliente")
                .param("evento", eventoId).param("cliente", clienteId).query(TURNO).optional();
    }

    /**
     * Entrega el proximo numero con un UPDATE atomico sobre la fila del evento: aunque miles de clientes pidan
     * turno a la vez, PostgreSQL los ordena y cada uno recibe un numero distinto. Vacio si la fila no esta activa.
     */
    public Optional<Long> siguienteNumero(long eventoId) {
        return jdbc.sql("""
                        UPDATE fila_virtual SET ultimo_numero = ultimo_numero + 1
                         WHERE evento_id = :evento AND activa
                        RETURNING ultimo_numero
                        """)
                .param("evento", eventoId).query(Long.class).optional();
    }

    /** false si el cliente ya tenia turno (otro pedido suyo se adelanto). */
    public boolean insertarTurno(long eventoId, long clienteId, long numero, LocalDateTime ahora) {
        return jdbc.sql("""
                        INSERT INTO turno_fila (evento_id, cliente_id, numero, estado, pedido_en)
                        VALUES (:evento, :cliente, :numero, 'ESPERANDO', :ahora)
                        ON CONFLICT (evento_id, cliente_id) DO NOTHING
                        """)
                .param("evento", eventoId).param("cliente", clienteId)
                .param("numero", numero).param("ahora", ahora)
                .update() == 1;
    }

    /** Un cliente cuyo turno ya termino vuelve a la fila, al final. */
    public boolean volverAEncolar(long eventoId, long clienteId, long numero, LocalDateTime ahora) {
        return jdbc.sql("""
                        UPDATE turno_fila
                           SET numero = :numero, estado = 'ESPERANDO', pedido_en = :ahora,
                               habilitado_en = NULL, vence_en = NULL
                         WHERE evento_id = :evento AND cliente_id = :cliente
                           AND (estado = 'VENCIDO' OR (estado IN ('HABILITADO', 'USADO') AND vence_en <= :ahora))
                        """)
                .param("numero", numero).param("ahora", ahora)
                .param("evento", eventoId).param("cliente", clienteId)
                .update() == 1;
    }

    public long contar(long eventoId, String estado) {
        return jdbc.sql("SELECT COUNT(*) FROM turno_fila WHERE evento_id = :evento AND estado = :estado")
                .param("evento", eventoId).param("estado", estado).query(Long.class).single();
    }

    /** Cuantos clientes esperan antes de ese numero. */
    public long esperandoAntesDe(long eventoId, long numero) {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM turno_fila
                         WHERE evento_id = :evento AND estado = 'ESPERANDO' AND numero < :numero
                        """)
                .param("evento", eventoId).param("numero", numero).query(Long.class).single();
    }

    /** Los turnos habilitados cuyo tiempo para comprar ya paso quedan VENCIDOS (y liberan su lugar). */
    public int vencerTurnos(long eventoId, LocalDateTime ahora) {
        return jdbc.sql("""
                        UPDATE turno_fila SET estado = 'VENCIDO'
                         WHERE evento_id = :evento AND estado = 'HABILITADO' AND vence_en <= :ahora
                        """)
                .param("evento", eventoId).param("ahora", ahora).update();
    }

    /** Habilita a los proximos clientes que esperan, por orden de numero. */
    public int habilitarSiguientes(long eventoId, int cuantos, LocalDateTime ahora, LocalDateTime venceEn) {
        return jdbc.sql("""
                        UPDATE turno_fila SET estado = 'HABILITADO', habilitado_en = :ahora, vence_en = :vence
                         WHERE evento_id = :evento
                           AND cliente_id IN (SELECT cliente_id FROM turno_fila
                                               WHERE evento_id = :evento AND estado = 'ESPERANDO'
                                               ORDER BY numero
                                               LIMIT :cuantos)
                        """)
                .param("ahora", ahora).param("vence", venceEn)
                .param("evento", eventoId).param("cuantos", cuantos)
                .update();
    }

    /** El cliente ya hizo su compra: su lugar en la tanda queda libre para el siguiente. */
    public void marcarUsado(long eventoId, long clienteId, LocalDateTime ahora) {
        jdbc.sql("""
                        UPDATE turno_fila SET estado = 'USADO'
                         WHERE evento_id = :evento AND cliente_id = :cliente
                           AND estado = 'HABILITADO' AND vence_en > :ahora
                        """)
                .param("evento", eventoId).param("cliente", clienteId).param("ahora", ahora).update();
    }
}

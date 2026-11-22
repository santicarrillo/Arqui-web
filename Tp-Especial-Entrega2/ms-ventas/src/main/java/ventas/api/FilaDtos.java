package ventas.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Requests y responses de la fila virtual (F10). */
public final class FilaDtos {

    private FilaDtos() {
    }

    /** Cuantos clientes compran a la vez y cuantos minutos tiene cada uno. */
    public record ActivarFilaRequest(@NotNull @Min(1) @Max(10000) Integer simultaneos,
                                     @NotNull @Min(1) @Max(60) Integer minutosTurno) {
    }

    /** Estado de la fila de un evento. Si nunca se activo, activa = false y el resto vacio. */
    public record FilaView(Long eventoId, boolean activa, Integer simultaneos, Integer minutosTurno,
                           long esperando, long comprando) {
    }

    /**
     * El turno de un cliente. estado: ESPERANDO (personasAntes dice cuantos hay adelante), HABILITADO (puede
     * comprar hasta venceEn), USADO (ya compro; puede seguir comprando hasta venceEn) o VENCIDO.
     */
    public record TurnoView(Long eventoId, long numero, String estado, Long personasAntes,
                            LocalDateTime habilitadoEn, LocalDateTime venceEn, boolean puedeComprar) {
    }
}

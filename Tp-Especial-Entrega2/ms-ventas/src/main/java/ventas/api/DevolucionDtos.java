package ventas.api;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Requests y responses de devoluciones y de la cancelacion de un evento. */
public final class DevolucionDtos {

    private DevolucionDtos() {
    }

    public record DevolucionView(Long devolucionId, Long entradaId, String estado, String motivo,
                                 LocalDateTime fechaSolicitud, Long adminId, LocalDateTime fechaResolucion,
                                 Long eventoId, Long clienteId, BigDecimal monto) {
    }

    /** Lo que manda el administrador: true para aprobar, false para rechazar. */
    public record ResolucionRequest(@NotNull Boolean aprobar) {
    }

    public record CancelacionView(Long eventoId, int comprasPendientesCanceladas, int entradasDevueltas) {
    }
}

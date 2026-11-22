package notificaciones.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import notificaciones.Domains.Notificacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class Dtos {
    private Dtos() {}

    // ---------- avisos que mandan los otros servicios ----------

    /** ventas: se pago una compra. */
    public record CompraConfirmadaRequest(@NotNull Long compraId, @NotNull Long clienteId, @NotNull Long eventoId,
                                          @NotNull Integer entradas, @NotNull BigDecimal total) {}

    /** ventas: se cancelo un evento y se devolvieron las entradas de estos clientes. */
    public record EventoCanceladoRequest(@NotNull Long eventoId, @NotEmpty List<@NotNull Long> clientes) {}

    /** eventos: se publico un evento con estos artistas. Se avisa a quienes los tengan de favoritos. */
    public record EventoPublicadoRequest(@NotNull Long eventoId, @NotBlank String nombre, String ciudad,
                                         LocalDateTime fechaHora, @NotEmpty List<@NotBlank String> artistas) {}

    // ---------- lo que ve el cliente ----------

    public record NotificacionView(String id, String tipo, Long eventoId, String titulo, String mensaje,
                                   LocalDateTime creadaEn, boolean leida) {
        public static NotificacionView de(Notificacion n) {
            return new NotificacionView(n.id(), n.tipo(), n.eventoId(), n.titulo(), n.mensaje(), n.creadaEn(),
                    Boolean.TRUE.equals(n.leida()));
        }
    }
}

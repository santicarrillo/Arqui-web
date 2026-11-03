package api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class Dtos {
    private Dtos() {
    }

    /** Un lugar pedido: un asiento (sector numerado) o una cantidad (zona sin numerar). */
    public record LugarRequest(@NotNull Long sectorId, Long asientoId, Integer cantidad) {
    }

    public record CompraRequest(@NotNull Long eventoId, @NotEmpty List<@Valid LugarRequest> lugares) {
    }

    public record PagoRequest(@NotBlank String medioPago) {
    }

    public record DetalleView(Long entradaId, Long sectorId, Long asientoId, BigDecimal precio,
                              String codigoQr, String estado) {
    }

    public record CompraView(Long compraId, Long eventoId, String estado, BigDecimal total,
                             String medioPago, LocalDateTime venceEn, List<DetalleView> detalle) {
    }

    public record EntradaView(Long entradaId, Long compraId, Long eventoId, Long sectorId, Long asientoId,
                              BigDecimal precio, String codigoQr, String estado) {
    }

    public record DisponibilidadView(Long eventoId, Long sectorId, boolean numerado, int capacidad,
                                     int libres, int ocupados, List<Long> asientosLibres) {
    }

    /** Un sector del evento que hay que preparar al publicarlo. La capacidad solo se usa en zonas. */
    public record SectorInit(@NotNull Long sectorId, boolean numerado, Integer capacidad) {
    }

    public record InicializarRequest(@NotNull Long eventoId, @NotEmpty List<@Valid SectorInit> sectores) {
    }
}

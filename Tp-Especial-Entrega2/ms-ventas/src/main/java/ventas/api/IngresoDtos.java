package ventas.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Requests y responses del control de acceso (validar el QR en la puerta). */
public final class IngresoDtos {

    private IngresoDtos() {
    }

    /** Lo que manda el personal de acceso: el evento de la puerta y el QR que leyo. */
    public record IngresoRequest(@NotNull Long eventoId, @NotBlank String codigoQr) {
    }

    public record IngresoView(boolean permitido, Long entradaId, Long eventoId, Long sectorId, Long asientoId,
                              LocalDateTime ingresoEn, String mensaje) {
    }
}

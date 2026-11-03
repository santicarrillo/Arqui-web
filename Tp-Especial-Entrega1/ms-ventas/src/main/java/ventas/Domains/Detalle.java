package ventas.Domains;

import java.math.BigDecimal;

/** Una linea del detalle de una compra: cada linea es una entrada. */
public record Detalle(
        long id,
        long compraId,
        long sectorId,
        Long asientoId,
        BigDecimal precioUnitario,
        String codigoQr,
        String estado) {
}

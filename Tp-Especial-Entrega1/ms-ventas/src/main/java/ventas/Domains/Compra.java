package ventas.Domains;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Compra(
                     long id,
                     long clienteId,
                     long eventoId,
                     LocalDateTime eventoFechaHora,
                     String estado,
                     String medioPago,
                     BigDecimal total,
                     String claveIdempotencia,
                     LocalDateTime fechaCompra,
                     LocalDateTime venceEn){}

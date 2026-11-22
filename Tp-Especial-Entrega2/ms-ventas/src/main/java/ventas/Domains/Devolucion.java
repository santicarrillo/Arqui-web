package ventas.Domains;

import java.time.LocalDateTime;

public record Devolucion(
        long id,
         long detalleCompraId,
         LocalDateTime fechaSolicitud,
         String estado,
         Long adminId,
         LocalDateTime fechaResolucion,
         String motivo) { }

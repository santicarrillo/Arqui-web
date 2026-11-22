package ventas.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ClientDtos {
    private ClientDtos() {
    }

    public record SectorEvento(Long sectorId, boolean numerado, BigDecimal precio) {
    }

    public record EventoInfo(Long id, String estado, LocalDateTime fechaHora, Integer limitePorCliente,
                             List<SectorEvento> sectores) {
    }

    /** Solo los campos de GET /eventos/{id} que hacen falta para saber de quien es el evento. */
    public record EventoDueno(Long id, Long organizadorId) {
    }

    public record AsientoInfo(Long id) {
    }

    public record PagoInfo(Long id, Long compraId, String estado) {
    }

    public record CobroRequest(Long compraId, Long clienteId, BigDecimal monto) {
    }
    public record ReintegroRequest(long detalleCompraId, BigDecimal monto){}
}

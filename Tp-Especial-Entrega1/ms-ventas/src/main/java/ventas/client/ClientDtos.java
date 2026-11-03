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

    public record AsientoInfo(Long id) {
    }

    public record PagoInfo(Long id, Long compraId, String estado) {
    }

    public record CobroRequest(Long compraId, Long clienteId, BigDecimal monto) {
    }
}

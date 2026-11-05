package eventos.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class Dtos {
    private Dtos() {}

    // ---------- requests (lo que entra) ----------
    public record CrearEventoRequest(
            String nombre, String descripcion, String tipo,
            LocalDateTime fechaHora, Long lugarId, String ciudad,
            Long organizadorId, Integer limitePorCliente) {}

    public record SectorPrecioRequest(Long sectorId, boolean numerado, BigDecimal precio) {}

    public record ParticipanteRequest(String nombre, String tipo) {}

    public record CancelacionRequest(String motivo) {}

    // ---------- responses (lo que sale) ----------
    // >>> CONTRATO que consume ventas (GET /eventos/{id}). NO cambiar nombres. <<<
    public record EventoInfoView(
            Long id, String estado, LocalDateTime fechaHora,
            Integer limitePorCliente, List<SectorEventoView> sectores) {}

    public record SectorEventoView(Long sectorId, boolean numerado, BigDecimal precio) {}
}
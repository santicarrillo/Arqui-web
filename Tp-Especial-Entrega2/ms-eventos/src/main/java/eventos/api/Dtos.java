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
    // F7: se agrega organizadorId al final (ventas lo usa para que cada organizador vea solo sus reportes)
    public record EventoInfoView(
            Long id, String estado, LocalDateTime fechaHora,
            Integer limitePorCliente, List<SectorEventoView> sectores, Long organizadorId) {}

    public record SectorEventoView(Long sectorId, boolean numerado, BigDecimal precio) {}

    // respuesta de la cancelacion: devoluciones PROCESADAS por ventas, o PENDIENTES (se reintenta solo)
    public record CancelacionView(Long eventoId, String estado, String devoluciones) {}

    // listado que devuelve la busqueda
    public record EventoResumenView(
            Long id, String nombre, String tipo, LocalDateTime fechaHora, String ciudad, String estado) {}
}

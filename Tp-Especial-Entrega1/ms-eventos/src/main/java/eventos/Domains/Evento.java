package eventos.Domains;

import java.time.LocalDateTime;

public record Evento(
        Long id,
        String nombre,
        String descripcion,
        String tipo,
        LocalDateTime fechaHora,
        String estado,
        Long lugarId,
        String ciudad,
        Long organizadorId,
        Integer limitePorCliente,
        String motivoCancelacion
) {}
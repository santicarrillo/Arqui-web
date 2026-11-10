package lugares.Domains;

public record Asiento(
        Long id,
        Long sectorId,
        String fila,
        String numero
) {}
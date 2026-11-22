package lugares.Domains;

public record Sector(
        Long id,
        Long lugarId,
        String nombre,
        boolean numerado,
        int capacidad
) {}
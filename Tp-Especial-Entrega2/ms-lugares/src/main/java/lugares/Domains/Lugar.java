package lugares.Domains;

public record Lugar(
        Long id,
        String nombre,
        String direccion,
        Double latitud,
        Double longitud,
        String ciudad
) {}
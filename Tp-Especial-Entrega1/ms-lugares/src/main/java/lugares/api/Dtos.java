package lugares.api;

public class Dtos {
    private Dtos(){}

    //===================================
    //DTOS de entrada(Request)
    //===================================
    public record CrearLugarRequest(
            String nombre,
            String direccion,
            Double latitud,
            Double longitud,
            String ciudad
    ){}
    public record CrearSectorRequest(
            String nombre,
            boolean numerado,
            Integer capacidad,          // Para zonas NO numeradas (ej. Campo general: 5000)
            Integer totalFilas,         // Para sectores NUMERADOS (ej. 10 filas)
            Integer asientosPorFila     // Para sectores NUMERADOS (ej. 20 asientos por fila)
    ) {}

    //===================================
    //DTOS de salida(Views/Response)
    //===================================
    public record LugarView(
            Long id,
            String nombre,
            String direccion,
            Double latitud,
            Double longitud,
            String ciudad
    ){}

    public record SectorView(
            Long id,
            Long lugarId,
            String nombre,
            boolean numerado,
            int capacidad
    ) {}
}

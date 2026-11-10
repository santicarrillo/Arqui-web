package lugares.api;

public class Dtos {
    private Dtos(){}

    //DTOS de entrada(Request)
    public record CrearLugarRequest(
            String nombre,
            String direccion,
            Double latitud,
            Double longitud,
            String ciudad
    ){}


    //DTOS de salida(Views/Response)

    public record LugarView(
            Long id,
            String nombre,
            String direccion,
            Double latitud,
            Double longitud,
            String ciudad
    ){}
}

package notificaciones.Domains;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String codigo;

    public ApiException(HttpStatus status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus status() { return status; }
    public String codigo() { return codigo; }

    public static ApiException notFound(String codigo, String msg)    { return new ApiException(HttpStatus.NOT_FOUND, codigo, msg); }
    public static ApiException unavailable(String codigo, String msg) { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, codigo, msg); }
}

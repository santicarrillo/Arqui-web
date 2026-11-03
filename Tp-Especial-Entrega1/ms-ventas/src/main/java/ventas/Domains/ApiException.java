package ventas.Domains;

import org.springframework.http.HttpStatus;

/** Error de negocio que se traduce a una respuesta HTTP con {error, mensaje}. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String error;

    public ApiException(HttpStatus status, String error, String mensaje) {
        super(mensaje);
        this.status = status;
        this.error = error;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public static ApiException badRequest(String error, String mensaje) {
        return new ApiException(HttpStatus.BAD_REQUEST, error, mensaje);
    }

    public static ApiException notFound(String error, String mensaje) {
        return new ApiException(HttpStatus.NOT_FOUND, error, mensaje);
    }

    public static ApiException conflict(String error, String mensaje) {
        return new ApiException(HttpStatus.CONFLICT, error, mensaje);
    }

    public static ApiException unavailable(String error, String mensaje) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, error, mensaje);
    }
}

package preferencias.Domains;

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

    public static ApiException badRequest(String codigo, String msg)  { return new ApiException(HttpStatus.BAD_REQUEST, codigo, msg); }
    public static ApiException forbidden(String codigo, String msg)   { return new ApiException(HttpStatus.FORBIDDEN, codigo, msg); }
    public static ApiException unavailable(String codigo, String msg) { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, codigo, msg); }
}

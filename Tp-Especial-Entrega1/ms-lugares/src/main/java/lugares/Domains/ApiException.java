package lugares.Domains;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException{
    private final HttpStatus status;
    private final String codigo;

    public ApiException(HttpStatus status, String codigo,String message) {
        super(message);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus status(){return this.status;}
    public String codigo(){return this.codigo;}

    public static ApiException notFound(String codigo,String msj){
        return new ApiException(HttpStatus.NOT_FOUND,codigo,msj);
    }

    public static ApiException badRequest(String codigo,String msj){
        return new ApiException(HttpStatus.BAD_REQUEST,codigo,msj);
    }

    public static ApiException conflict(String codigo,String msj){
        return new ApiException(HttpStatus.CONFLICT,codigo,msj);
    }
}

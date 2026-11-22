package ventas.Domains;

import org.springframework.http.HttpStatus;

/**
 * Otro servicio no respondio (caido, timeout o error 5xx). Es la unica excepcion que se reintenta y la que
 * cuenta como falla para el circuit breaker; los errores de negocio (404, 409...) no.
 */
public class ServicioNoDisponibleException extends ApiException {

    public ServicioNoDisponibleException(String error, String mensaje) {
        super(HttpStatus.SERVICE_UNAVAILABLE, error, mensaje);
    }
}

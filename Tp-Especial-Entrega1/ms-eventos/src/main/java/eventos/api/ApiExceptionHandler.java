package eventos.api;

import eventos.Domains.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> manejar(ApiException e) {
        return ResponseEntity.status(e.status())
                .body(Map.of("error", e.codigo(), "mensaje", e.getMessage()));
    }
}
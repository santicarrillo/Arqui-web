package notificaciones.api;

import notificaciones.Domains.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> datosInvalidos(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .findFirst().orElse("datos invalidos");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "DATOS_INVALIDOS", "mensaje", detalle));
    }
}

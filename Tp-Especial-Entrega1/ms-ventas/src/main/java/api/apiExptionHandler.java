package api;


import java.util.Map;

import Domains.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Todos los errores salen como {"error": "...", "mensaje": "..."}. */
@RestControllerAdvice;
public class apiExptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> negocio(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getError(), "mensaje", e.getMessage()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, String>> faltaEncabezado(MissingRequestHeaderException e) {
        return respuesta(HttpStatus.BAD_REQUEST, "FALTA_ENCABEZADO", "Falta el encabezado " + e.getHeaderName());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> datosInvalidos(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b).orElse("datos invalidos");
        return respuesta(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", detalle);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> pedidoMalFormado(Exception e) {
        return respuesta(HttpStatus.BAD_REQUEST, "PEDIDO_INVALIDO", "El pedido no se pudo interpretar");
    }

    private static ResponseEntity<Map<String, String>> respuesta(HttpStatus status, String error, String mensaje) {
        return ResponseEntity.status(status).body(Map.of("error", error, "mensaje", mensaje));
    }
}

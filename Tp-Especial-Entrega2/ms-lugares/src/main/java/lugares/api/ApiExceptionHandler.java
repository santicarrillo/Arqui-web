package lugares.api;


import lugares.Domains.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String,String>> manjearNegocio(ApiException e){
        return ResponseEntity.status(e.status()).body(Map.of("error",e.codigo(),"mensaje",e.getMessage()));
    }


    // 2. Atrapa errores de formato HTTP (ej: si mandan texto donde va un número, o un JSON roto)
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> pedidoMalFormado(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "PEDIDO_INVALIDO", "mensaje", "El pedido no se pudo interpretar o los tipos son incorrectos"));
    }
}

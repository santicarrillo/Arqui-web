package com.example.controller.estudiante;

import com.example.service.dto.ErrorDTO;
import com.example.service.exception.EstudianteException;
import com.example.service.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDate;
import java.util.stream.Collectors;

/**
 * Atrapa las excepciones de todos los controllers y las devuelve como un ErrorDTO,
 * con el código HTTP que corresponde (@ResponseStatus). Así cada endpoint no tiene que hacer try/catch.
 */
@RestControllerAdvice(basePackages = "com.example.controller")
public class GlobalExceptionHandler {

    // 404: no se encontró lo que se buscaba
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorDTO notFoundException(NotFoundException ex) {
        return new ErrorDTO(ex.getMessage(), LocalDate.now());
    }

    // 409: el estudiante ya existe (DNI o LU repetidos)
    @ExceptionHandler(EstudianteException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorDTO estudianteException(EstudianteException ex) {
        return new ErrorDTO(ex.getMessage(), LocalDate.now());
    }

    // 400: el JSON no cumple las validaciones del RequestDTO (@NotBlank, @Positive, ...)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO validationException(MethodArgumentNotValidException ex) {
        String errores = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining(" "));
        return new ErrorDTO(errores, LocalDate.now());
    }

    // 400: el body no es un JSON válido o un campo tiene un tipo incorrecto (ej. "edad": "veinte")
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO jsonMalFormado(HttpMessageNotReadableException ex) {
        return new ErrorDTO("El cuerpo de la solicitud no es un JSON válido.", LocalDate.now());
    }
}


package com.example.service.dto.Estudiante.Request;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EstudianteRequestDTO (
    @NotNull(message = "El DNI es un campo obligatorio.")
    @Positive(message = "El DNI debe ser un número positivo.")
    Integer numeroDocumento,

    @NotNull(message = "La libreta universitaria es un campo obligatorio.")
    @Positive(message = "La libreta universitaria debe ser un número positivo.")
    Integer libretaUniversitaria,

    @NotBlank(message = "El nombre es un campo obligatorio.")
    String nombre,

    @NotBlank(message = "El apellido es un campo obligatorio.")
    String apellido,

    @NotNull(message = "La edad es un campo obligatorio.")
    @Min(value = 16, message = "La edad mínima es 16.")
    @Max(value = 120, message = "La edad máxima es 120.")
    Integer edad,

    @NotBlank(message = "El género es un campo obligatorio.")
    String genero,

    @NotBlank(message = "La ciudad de residencia es un campo obligatorio.")
    String ciudadResidencia
){

}

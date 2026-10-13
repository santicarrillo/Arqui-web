package com.example.service.dto.Inscripcion.Request;

import jakarta.validation.constraints.NotNull;

public record InscripcionRequestDTO(
        @NotNull(message = "El DNI del estudiante es obligatorio")
        Integer numeroDocumento,

        @NotNull(message = "El ID de la carrera es obligatorio")
        Integer idCarrera
) {}
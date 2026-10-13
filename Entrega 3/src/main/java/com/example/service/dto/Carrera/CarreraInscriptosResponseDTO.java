package com.example.service.dto.Carrera;

public record CarreraInscriptosResponseDTO(
        int idCarrera,
        String nombre,
        int duracion,
        long cantidadInscriptos
) {
}
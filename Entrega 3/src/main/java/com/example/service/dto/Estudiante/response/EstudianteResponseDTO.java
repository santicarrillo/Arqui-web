package com.example.service.dto.Estudiante.response;


import com.example.entity.Estudiante;

/**
 * Lo que la API devuelve de un estudiante.
 * Se usa un DTO en vez de la entidad para no exponer la lista de inscripciones
 * (que además genera un ciclo Estudiante -> Inscripcion -> Estudiante al pasar a JSON).
 */
public record EstudianteResponseDTO(
        int numeroDocumento,
        int libretaUniversitaria,
        String nombre,
        String apellido,
        int edad,
        String genero,
        String ciudadResidencia
) {
    public EstudianteResponseDTO(Estudiante e) {
        this(e.getNumeroDocumento(),
                e.getLibretaUniversitaria(),
                e.getNombre(),
                e.getApellido(),
                e.getEdad(),
                e.getGenero(),
                e.getCiudadResidencia());
    }
}

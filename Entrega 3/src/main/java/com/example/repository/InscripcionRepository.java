package com.example.repository;

import com.example.entity.Inscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InscripcionRepository extends JpaRepository<Inscripcion, Integer> {

    // 2.b) Verifica si un estudiante ya está inscripto en una carrera dada
    boolean existsByEstudianteNumeroDocumentoAndCarreraIdCarrera(int dni, int idCarrera);
}
package com.example.repository;

import com.example.entity.Inscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InscripcionRepository extends JpaRepository<Inscripcion, Integer> {

    // ---- Acá van las consultas del inciso 2.b (matricular) si hacen falta ----
}

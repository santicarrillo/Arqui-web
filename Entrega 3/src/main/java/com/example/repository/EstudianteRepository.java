package com.example.repository;


import com.example.entity.Estudiante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface EstudianteRepository extends JpaRepository<Estudiante, Integer> {

    // Usada por el 2.a) para no permitir dos estudiantes con la misma libreta universitaria.
    // Spring arma la consulta a partir del nombre del método:
    // SELECT COUNT(e) > 0 FROM Estudiante e WHERE e.libretaUniversitaria = ?
    boolean existsByLibretaUniversitaria(int libretaUniversitaria);

    // ---- Acá van las consultas de los incisos 2.c, 2.d, 2.e y 2.g ----
    //2.d
    Optional<Estudiante> findByLibretaUniversitaria(int libretaUniversitaria);
    //2.e
    List<Estudiante> findByGenero(String genero);
}


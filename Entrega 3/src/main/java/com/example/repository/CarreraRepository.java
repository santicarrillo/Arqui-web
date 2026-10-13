package com.example.repository;
import com.example.entity.Carrera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CarreraRepository extends JpaRepository<Carrera, Integer> {

    @Query("""
    SELECT c, COUNT(i)
    FROM Inscripcion i
    JOIN i.carrera c
    GROUP BY c
    ORDER BY COUNT(i) DESC
    """)
    List<Object[]> findCarrerasOrdenadasPorInscriptos();


}

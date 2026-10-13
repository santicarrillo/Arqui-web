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

    // 2.h) Reporte: cantidad de INSCRIPTOS por carrera y año de inscripcion
    @Query("""
    SELECT c.nombre, i.fechaInscripcion, COUNT(i)
    FROM Inscripcion i
    JOIN i.carrera c
    GROUP BY c.nombre, i.fechaInscripcion
    ORDER BY c.nombre ASC, i.fechaInscripcion ASC
    """)
    List<Object[]> findInscriptosPorCarreraYAnio();

    // 2.h) Reporte: cantidad de EGRESADOS por carrera y año de egreso (graduacion <> 0)
    @Query("""
    SELECT c.nombre, i.fechaGraduacion, COUNT(i)
    FROM Inscripcion i
    JOIN i.carrera c
    WHERE i.fechaGraduacion <> 0
    GROUP BY c.nombre, i.fechaGraduacion
    ORDER BY c.nombre ASC, i.fechaGraduacion ASC
    """)
    List<Object[]> findEgresadosPorCarreraYAnio();
}

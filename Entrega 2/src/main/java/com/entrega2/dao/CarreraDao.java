package com.entrega2.dao;

import com.entrega2.entitys.Carrera;

import java.util.List;

public interface CarreraDao {

    // 2.f) Recuperar las carreras con estudiantes inscriptos, ordenadas por cantidad de inscriptos
    List<Carrera> getCarrerasOrdenadasPorInscriptos();

    // Inciso 3) Inscriptos por carrera y año -> [nombreCarrera, anio, cantidad]
    List<Object[]> getInscriptosPorCarreraYAnio();

    // Inciso 3) Egresados por carrera y año -> [nombreCarrera, anio, cantidad]
    List<Object[]> getEgresadosPorCarreraYAnio();
}

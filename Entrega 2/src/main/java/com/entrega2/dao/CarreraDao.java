package com.entrega2.dao;

import com.entrega2.entitys.Carrera;

import java.util.List;

public interface CarreraDao {

    //2.f) recuperar las carreras con estudiantes inscriptos, y ordenar por cantidad de inscriptos.
    // ordenadas por cantidad de inscriptos
    List<Carrera> getCarrerasOrdenadasPorInscriptos();
}

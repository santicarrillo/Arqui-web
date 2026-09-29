package com.entrega2.services;

import com.entrega2.dao.CarreraDao;
import com.entrega2.entitys.Carrera;

import java.util.List;

public class CarreraService {

    private CarreraDao carreraDao;

    public CarreraService(CarreraDao carreraDao) {
        this.carreraDao = carreraDao;
    }

    // 2.f) Recuperar carreras con estudiantes inscriptos,
    // ordenadas por cantidad de inscriptos
    public List<Carrera> getCarrerasOrdenadasPorInscriptos() {

        List<Carrera> carreras =
                carreraDao.getCarrerasOrdenadasPorInscriptos();

        if (!carreras.isEmpty()) {
            System.out.println("Se encontraron carreras con estudiantes inscriptos.");
            return carreras;
        } else {
            System.out.println("No se encontraron carreras con estudiantes inscriptos.");
            return null;
        }
    }
}
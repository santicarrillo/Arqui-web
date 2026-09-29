package com.entrega2.services;

import com.entrega2.dao.EstudianteDao;
import com.entrega2.entitys.Estudiante;

import java.util.List;

public class EstudianteService {

    private EstudianteDao estudianteDao;

    public EstudianteService(EstudianteDao estudianteDao) {
        this.estudianteDao = estudianteDao;
    }

    // 2.c) Recuperar todos los estudiantes ordenados
    public List<Estudiante> getEstudiantesOrdenados() {
        return estudianteDao.getEstudiantesOrdenados();
    }

    // 2.d) Recuperar estudiante por LU
    public Estudiante getEstudianteByLU(int lu) {
        return estudianteDao.getEstudianteByLU(lu);
    }

    // 2.e) Recuperar estudiantes por género
    public List<Estudiante> getEstudiantesByGenero(String genero) {

        List<Estudiante> estudiantes =
                estudianteDao.getEstudiantesByGenero(genero);

        if (!estudiantes.isEmpty()) {
            System.out.println("Se encontraron estudiantes para el género: " + genero);
            return estudiantes;
        } else {
            System.out.println("No se encontraron estudiantes para el género: " + genero);
            return null;
        }
    }

    //f) recuperar las carreras con estudiantes inscriptos, y ordenar por cantidad de inscriptos.
}
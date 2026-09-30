package com.entrega2.services;

import com.entrega2.dao.EstudianteDao;
import com.entrega2.entitys.Estudiante;

import java.util.List;

public class EstudianteService {

    private EstudianteDao estudianteDao;

    public EstudianteService(EstudianteDao estudianteDao) {
        this.estudianteDao = estudianteDao;
    }

    // 2.a) Dar de alta un estudiante
    public void altaEstudiante(Estudiante estudiante) {
        estudianteDao.altaEstudiante(estudiante);
        System.out.println("Estudiante dado de alta: "
                + estudiante.getNombre() + " " + estudiante.getApellido());
    }

    // 2.b) Matricular un estudiante en una carrera
    public void matricularEnCarrera(int dniEstudiante, int idCarrera, int anioInscripcion) {
        estudianteDao.matricularEnCarrera(dniEstudiante, idCarrera, anioInscripcion);
        System.out.println("Estudiante " + dniEstudiante + " matriculado en la carrera "
                + idCarrera + " (año " + anioInscripcion + ")");
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
        List<Estudiante> estudiantes = estudianteDao.getEstudiantesByGenero(genero);
        if (!estudiantes.isEmpty()) {
            System.out.println("Se encontraron estudiantes para el género: " + genero);
            return estudiantes;
        } else {
            System.out.println("No se encontraron estudiantes para el género: " + genero);
            return null;
        }
    }

    // 2.g) Recuperar estudiantes de una carrera, filtrados por ciudad de residencia
    public List<Estudiante> getEstudiantesByCarreraAndCiudad(int idCarrera, String ciudad) {
        List<Estudiante> estudiantes = estudianteDao.getEstudiantesByCarreraAndCiudad(idCarrera, ciudad);
        if (estudiantes.isEmpty()) {
            System.out.println("No se encontraron estudiantes de la carrera "
                    + idCarrera + " en la ciudad " + ciudad);
        }
        return estudiantes;
    }
}

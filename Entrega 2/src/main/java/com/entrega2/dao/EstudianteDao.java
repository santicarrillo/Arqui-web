package com.entrega2.dao;


import com.entrega2.entitys.Estudiante;
import java.util.List;
public interface EstudianteDao {
    // 2.a) Dar de alta un estudiante
    void altaEstudiante(Estudiante estudiante);

    // 2.b) Matricular un estudiante (por DNI) en una carrera (por id), en un año dado
    void matricularEnCarrera(int dniEstudiante, int idCarrera, int anioInscripcion);

    // 2.c) Recuperar todos los estudiantes ordenados
    List<Estudiante> getEstudiantesOrdenados();

    // 2.d) Recuperar un estudiante por su Libreta Universitaria (LU)
    Estudiante getEstudianteByLU(int lu);

    //2.e) recuperar todos los estudiantes, en base a su género
    List<Estudiante> getEstudiantesByGenero(String genero);

    // 2.g) Recuperar estudiantes de una carrera (por id), filtrados por ciudad de residencia
    List<Estudiante> getEstudiantesByCarreraAndCiudad(int idCarrera, String ciudad);
}

package com.entrega2.dao;


import com.entrega2.entitys.Estudiante;
import java.util.List;
public interface EstudianteDao {
    // 2.c) Recuperar todos los estudiantes ordenados
    List<Estudiante> getEstudiantesOrdenados();

    // 2.d) Recuperar un estudiante por su Libreta Universitaria (LU)
    Estudiante getEstudianteByLU(int lu);
}
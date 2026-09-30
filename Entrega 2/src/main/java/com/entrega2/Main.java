package com.entrega2;

import com.entrega2.dao.CarreraDao;
import com.entrega2.dao.EstudianteDao;
import com.entrega2.dao.impljpql.CarreraJPQL;
import com.entrega2.dao.impljpql.EstudianteJPQL;
import com.entrega2.entitys.Carrera;
import com.entrega2.entitys.Estudiante;
import com.entrega2.services.CarreraService;
import com.entrega2.services.EstudianteService;
import com.entrega2.utils.HelperMySQL;
import jakarta.persistence.EntityManagerFactory;

public class Main {
    public static void main(String[] args) {

        try {
            System.out.println("=== Iniciando TP2 - JPA ===\n");

            HelperMySQL helper = new HelperMySQL();
            helper.populateDB();
            System.out.println("¡Base de datos cargada exitosamente!");

            EntityManagerFactory emf = PersistenceManager.getEntityManagerFactory();

            EstudianteDao estudianteDao = new EstudianteJPQL(emf);
            EstudianteService estudianteService = new EstudianteService(estudianteDao);

            CarreraDao carreraDao = new CarreraJPQL(emf);
            CarreraService carreraService = new CarreraService(carreraDao);

            // 2.a) Alta de un estudiante
            System.out.println("\n--- 2.a) Alta de un estudiante ---");
            Estudiante nuevo = new Estudiante(999999, 40000000, "Nacho", "Torres", 25, "Male", "Tandil");
            estudianteService.altaEstudiante(nuevo);

            // 2.b) Matricular en una carrera
            System.out.println("\n--- 2.b) Matricular estudiante en una carrera ---");
            estudianteService.matricularEnCarrera(40000000, 1, 2024);

            // 2.c) Estudiantes ordenados
            System.out.println("\n--- 2.c) Estudiantes ordenados ---");
            for (Estudiante e : estudianteService.getEstudiantesOrdenados()) {
                System.out.println(e);
            }

            // 2.d) Estudiante por LU
            System.out.println("\n--- 2.d) Estudiante con LU 34978 ---");
            Estudiante porLU = estudianteService.getEstudianteByLU(34978);
            System.out.println(porLU != null ? porLU : "No se encontró un estudiante con esa LU");

            // 2.e) Estudiantes por género
            System.out.println("\n--- 2.e) Estudiantes de género 'Male' ---");
            for (Estudiante e : estudianteService.getEstudiantesByGenero("Male")) {
                System.out.println(e);
            }

            // 2.f) Carreras por cantidad de inscriptos
            System.out.println("\n--- 2.f) Carreras ordenadas por inscriptos ---");
            for (Carrera c : carreraService.getCarrerasOrdenadasPorInscriptos()) {
                System.out.println(c);
            }

            // 2.g) Estudiantes de una carrera por ciudad
            System.out.println("\n--- 2.g) Estudiantes de la carrera 1 (TUDAI) en Tandil ---");
            for (Estudiante e : estudianteService.getEstudiantesByCarreraAndCiudad(1, "Tandil")) {
                System.out.println(e);
            }

            // Inciso 3) Reporte por carrera
            carreraService.imprimirReportePorCarrera();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            PersistenceManager.closeEntityManager();
            PersistenceManager.closeEntityManagerFactory();
        }
    }
}

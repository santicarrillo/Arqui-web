package com.entrega2;

import com.entrega2.dao.CarreraDao;
import com.entrega2.dao.EstudianteDao;
import com.entrega2.dao.impljpql.CarreraJPQL;
import com.entrega2.entitys.Carrera;
import com.entrega2.entitys.Estudiante;
import com.entrega2.services.CarreraService;
import com.entrega2.services.EstudianteService;
import com.entrega2.utils.HelperMySQL;
import com.entrega2.dao.impljpql.EstudianteJPQL;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Main {
    public static void main(String[] args) {


        try {
            System.out.println("=== Iniciando TP2 - JPA ===\n");

            HelperMySQL helper = new com.entrega2.utils.HelperMySQL();
            helper.populateDB();
            System.out.println("¡Base de datos cargada exitosamente!");

            // Instanciamos tu clase encargada de las consultas JPQL
            EntityManagerFactory emf = PersistenceManager.getEntityManagerFactory();
            EstudianteJPQL estudianteRepo = new com.entrega2.dao.impljpql.EstudianteJPQL(emf);

            //Utilizando el SERVICE
            EstudianteDao estudianteDao = new EstudianteJPQL(emf);
            EstudianteService estudianteService = new EstudianteService(estudianteDao);

            //Aca irian  consignas 2(c 2(d
            // for ... estudiantesService.getEstudiantes....

            //e) recuperar todos los estudiantes, en base a su género
            for (Estudiante e : estudianteRepo.getEstudiantesByGenero("Male")) {
                System.out.println(e);
            }

            //f) recuperar las carreras con estudiantes inscriptos, y ordenar por cantidad de inscriptos
            CarreraDao carreraDao = new CarreraJPQL(emf);
            CarreraService carreraService = new CarreraService(carreraDao);

            for(Carrera c : carreraService.getCarrerasOrdenadasPorInscriptos()){
                System.out.println(c);
            }



        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            PersistenceManager.closeEntityManager();
            PersistenceManager.closeEntityManagerFactory();
        }


    }
}


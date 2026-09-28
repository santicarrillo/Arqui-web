package com.entrega2;
Ñ
import com.entrega2.entitys.Estudiante;
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

            // --- 2.c) Recuperar todos los estudiantes ordenados ---
            System.out.println("\n--- Ejercicio 2.c: Estudiantes ordenados ---");
            List<Estudiante> estudiantesOrdenados = estudianteRepo.getEstudiantesOrdenados();

            if (estudiantesOrdenados.isEmpty()) {
                System.out.println("No se encontraron estudiantes.");
            } else {
                for (Estudiante e : estudiantesOrdenados) {
                    System.out.println("LU: " + e.getLibretaUniversitaria() +
                            " | Apellido: " + e.getApellido() +
                            " | Nombre: " + e.getNombre());
                }
            }

            // --- 2.d) Recuperar un estudiante por su Libreta Universitaria (LU) ---
            System.out.println("\n--- Ejercicio 2.d: Buscar estudiante por LU ---");
            int luBuscada = 90958;
            Estudiante estudiante = estudianteRepo.getEstudianteByLU(luBuscada);

            if (estudiante != null) {
                System.out.println("¡Estudiante encontrado: " + estudiante.getNombre() + " " + estudiante.getApellido() + "!");
            } else {
                System.out.println("No existe ningún estudiante con la LU: " + luBuscada);
            }

            System.out.println("\n=== Fin del programa ===");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            PersistenceManager.closeEntityManager();
            PersistenceManager.closeEntityManagerFactory();
        }
    }
}


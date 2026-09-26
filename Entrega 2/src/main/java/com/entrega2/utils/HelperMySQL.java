package com.entrega2.utils;

import com.entrega2.PersistenceManager;
import com.entrega2.entitys.Estudiante;
import com.entrega2.entitys.Carrera;
import com.entrega2.entitys.Inscripcion;
import jakarta.persistence.EntityManager;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import java.io.FileReader;

public class HelperMySQL {

    private EntityManager em;
    // Ruta base donde están los CSV (ajustar si es necesario)
    private static final String CSV_PATH = "src/resources/";

    public HelperMySQL() {
        this.em = PersistenceManager.getEntityManager();
    }

    private void popularEstudiantes() throws Exception {
        em.getTransaction().begin();
        try (FileReader reader = new FileReader(CSV_PATH + "estudiantes.csv");
                CSVParser csvParser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {

            for (CSVRecord row : csvParser) {
                Estudiante estudiante = new Estudiante(
                        Integer.parseInt(row.get("LU")),
                        Integer.parseInt(row.get("DNI")),
                        row.get("nombre"),
                        row.get("apellido"),
                        Integer.parseInt(row.get("edad")),
                        row.get("genero"),
                        row.get("ciudad"));
                em.persist(estudiante);
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            System.err.println("Error al popular estudiantes: " + e.getMessage());
            throw e;
        }
    }

    private void popularCarreras() throws Exception {
        em.getTransaction().begin();
        try (FileReader reader = new FileReader(CSV_PATH + "carreras.csv");
                CSVParser csvParser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {

            for (CSVRecord row : csvParser) {
                Carrera carrera = new Carrera(
                        Integer.parseInt(row.get("id_carrera")),
                        row.get("carrera"),
                        Integer.parseInt(row.get("duracion")));
                em.persist(carrera);
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            System.err.println("Error al popular carreras: " + e.getMessage());
            throw e;
        }
    }

    private void popularInscripciones() throws Exception {
        em.getTransaction().begin();
        try (FileReader reader = new FileReader(CSV_PATH + "estudianteCarrera.csv");
                CSVParser csvParser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {

            for (CSVRecord row : csvParser) {
                // Buscamos las entidades fuertes primero usando sus IDs
                Estudiante estudiante = em.find(Estudiante.class, Integer.parseInt(row.get("id_estudiante")));
                Carrera carrera = em.find(Carrera.class, Integer.parseInt(row.get("id_carrera")));

                if (estudiante != null && carrera != null) {
                    Inscripcion inscripcion = new Inscripcion(
                            0,
                            estudiante,
                            carrera,
                            Integer.parseInt(row.get("inscripcion")),
                            Integer.parseInt(row.get("graduacion")),
                            Integer.parseInt(row.get("antiguedad")));
                    em.persist(inscripcion);
                }
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            System.err.println("Error al popular inscripciones: " + e.getMessage());
            throw e;
        }
    }

    public void populateDB() throws Exception {
        // Ejecutamos en orden: primero entidades fuertes, luego la intermedia
        popularEstudiantes();
        popularCarreras();
        popularInscripciones();
    }
}

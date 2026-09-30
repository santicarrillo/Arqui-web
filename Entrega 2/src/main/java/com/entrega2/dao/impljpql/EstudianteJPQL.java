package com.entrega2.dao.impljpql;

import com.entrega2.dao.EstudianteDao;
import com.entrega2.entitys.Carrera;
import com.entrega2.entitys.Estudiante;
import com.entrega2.entitys.Inscripcion;
import jakarta.persistence.*;

import java.util.List;

public class EstudianteJPQL implements EstudianteDao {
    private final EntityManagerFactory emf;

    public EstudianteJPQL(EntityManagerFactory emf) {
        this.emf = emf;
    }

    // 2.a) Dar de alta un estudiante
    @Override
    public void altaEstudiante(Estudiante estudiante) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.persist(estudiante);
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            System.err.println("Error al dar de alta el estudiante: " + e.getMessage());
            throw e;
        } finally {
            em.close();
        }
    }

    // 2.b) Matricular un estudiante en una carrera
    @Override
    public void matricularEnCarrera(int dniEstudiante, int idCarrera, int anioInscripcion) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Estudiante estudiante = em.find(Estudiante.class, dniEstudiante);
            Carrera carrera = em.find(Carrera.class, idCarrera);
            if (estudiante == null || carrera == null) {
                throw new IllegalArgumentException(
                        "No existe el estudiante (DNI " + dniEstudiante + ") o la carrera (id " + idCarrera + ")");
            }
            Inscripcion inscripcion = new Inscripcion(0, estudiante, carrera, anioInscripcion, 0, 0);
            em.persist(inscripcion);
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            System.err.println("Error al matricular el estudiante: " + e.getMessage());
            throw e;
        } finally {
            em.close();
        }
    }

    @Override
    public List<Estudiante> getEstudiantesOrdenados() {
        try (EntityManager em = emf.createEntityManager()) {
            String jpql = "SELECT e FROM Estudiante e ORDER BY e.apellido ASC, e.nombre ASC";
            TypedQuery<Estudiante> query = em.createQuery(jpql, Estudiante.class);
            return query.getResultList();
        } catch (PersistenceException e) {
            System.err.println("Error al recuperar estudiantes ordenados: " + e.getMessage());
            return List.of();
        }
    }

    @Override
    public Estudiante getEstudianteByLU(int lu) {
        try (EntityManager em = emf.createEntityManager()) {
            String jpql = "SELECT e FROM Estudiante e WHERE e.libretaUniversitaria = :lu";
            TypedQuery<Estudiante> query = em.createQuery(jpql, Estudiante.class);
            query.setParameter("lu", lu);
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        } catch (PersistenceException e) {
            System.err.println("Error al buscar estudiante por LU: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public List<Estudiante> getEstudiantesByGenero(String genero) {
        try (EntityManager em = emf.createEntityManager()) {
            String jpql = "SELECT e FROM Estudiante e WHERE e.genero = :genero";
            TypedQuery<Estudiante> query = em.createQuery(jpql, Estudiante.class);
            query.setParameter("genero", genero);
            return query.getResultList();
        } catch (PersistenceException e) {
            System.err.println("Error al recuperar estudiantes por género: " + e.getMessage());
            return List.of();
        }
    }

    // 2.g) Estudiantes de una carrera, filtrados por ciudad de residencia
    @Override
    public List<Estudiante> getEstudiantesByCarreraAndCiudad(int idCarrera, String ciudad) {
        try (EntityManager em = emf.createEntityManager()) {
            String jpql =
                    "SELECT DISTINCT e FROM Estudiante e " +
                    "JOIN e.inscripciones i " +
                    "WHERE i.carrera.idCarrera = :idCarrera " +
                    "AND e.ciudadResidencia = :ciudad " +
                    "ORDER BY e.apellido ASC, e.nombre ASC";
            TypedQuery<Estudiante> query = em.createQuery(jpql, Estudiante.class);
            query.setParameter("idCarrera", idCarrera);
            query.setParameter("ciudad", ciudad);
            return query.getResultList();
        } catch (PersistenceException e) {
            System.err.println("Error al recuperar estudiantes por carrera y ciudad: " + e.getMessage());
            return List.of();
        }
    }
}

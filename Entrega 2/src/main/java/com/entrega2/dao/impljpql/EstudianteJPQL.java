package com.entrega2.dao.impljpql;

import com.entrega2.dao.EstudianteDao;
import com.entrega2.entitys.Estudiante;
import jakarta.persistence.*;

import java.util.List;

public class EstudianteJPQL implements EstudianteDao {
    private final EntityManagerFactory emf;

    public EstudianteJPQL(EntityManagerFactory emf) {
        this.emf = emf;
    }

    @Override
    public List<Estudiante> getEstudiantesOrdenados() {
        // Uso de try-with-resources compatible con Java 21 para cerrar el EntityManager automáticamente
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
            // Retorna null si no encuentra ningún estudiante con esa LU
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

            TypedQuery<Estudiante> query = em.createQuery(
                    jpql,
                    Estudiante.class
            );

            query.setParameter("genero", genero);

            return query.getResultList();
        } catch (PersistenceException e) {
            System.err.println("Error al recuperar estudiantes por género: " + e.getMessage());
            return List.of();
        }
    }
}

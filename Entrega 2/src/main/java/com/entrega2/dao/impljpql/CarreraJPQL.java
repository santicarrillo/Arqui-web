package com.entrega2.dao.impljpql;

import com.entrega2.dao.CarreraDao;
import com.entrega2.entitys.Carrera;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.TypedQuery;

import java.util.List;

public class CarreraJPQL implements CarreraDao {

    private final EntityManagerFactory emf;

    public CarreraJPQL(EntityManagerFactory emf) {
        this.emf = emf;
    }

    @Override
    public List<Carrera> getCarrerasOrdenadasPorInscriptos() {
        try (EntityManager em = emf.createEntityManager()) {
            String jpql =
                    "SELECT c FROM Carrera c " +
                            "JOIN c.inscripciones i " +
                            "GROUP BY c " +
                            "ORDER BY COUNT(i) DESC";
            TypedQuery<Carrera> query = em.createQuery(jpql, Carrera.class);
            return query.getResultList();
        }
    }

    // Inciso 3) Inscriptos por carrera y año de inscripción
    @Override
    public List<Object[]> getInscriptosPorCarreraYAnio() {
        try (EntityManager em = emf.createEntityManager()) {
            String jpql =
                    "SELECT c.nombre, i.fechaInscripcion, COUNT(i) " +
                    "FROM Inscripcion i JOIN i.carrera c " +
                    "GROUP BY c.nombre, i.fechaInscripcion " +
                    "ORDER BY c.nombre ASC, i.fechaInscripcion ASC";
            return em.createQuery(jpql, Object[].class).getResultList();
        }
    }

    // Inciso 3) Egresados por carrera y año de egreso (graduacion = 0 => no egresó)
    @Override
    public List<Object[]> getEgresadosPorCarreraYAnio() {
        try (EntityManager em = emf.createEntityManager()) {
            String jpql =
                    "SELECT c.nombre, i.fechaGraduacion, COUNT(i) " +
                    "FROM Inscripcion i JOIN i.carrera c " +
                    "WHERE i.fechaGraduacion <> 0 " +
                    "GROUP BY c.nombre, i.fechaGraduacion " +
                    "ORDER BY c.nombre ASC, i.fechaGraduacion ASC";
            return em.createQuery(jpql, Object[].class).getResultList();
        }
    }
}

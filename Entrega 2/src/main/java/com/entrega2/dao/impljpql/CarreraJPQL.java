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

            TypedQuery<Carrera> query =
                    em.createQuery(jpql, Carrera.class);

            return query.getResultList();

        }
    }
}
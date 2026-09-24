package com.entrega2;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

public class PersistenceManager {
    
    private static EntityManagerFactory emf;
    private static ThreadLocal<EntityManager> threadLocal = new ThreadLocal<>();
    
    static {
        emf = Persistence.createEntityManagerFactory("my_persistence_unit");
    }
    
    /**
     * Obtiene el EntityManager para la thread actual.
     * Si no existe, crea uno nuevo.
     */
    public static EntityManager getEntityManager() {
        EntityManager em = threadLocal.get();
        if (em == null || !em.isOpen()) {
            em = emf.createEntityManager();
            threadLocal.set(em);
        }
        return em;
    }
    
    /**
     * Cierra el EntityManager de la thread actual.
     */
    public static void closeEntityManager() {
        EntityManager em = threadLocal.get();
        if (em != null && em.isOpen()) {
            em.close();
            threadLocal.remove();
        }
    }
    
    /**
     * Cierra el EntityManagerFactory (llamar una sola vez al finalizar la app).
     */
    public static void closeEntityManagerFactory() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }
}

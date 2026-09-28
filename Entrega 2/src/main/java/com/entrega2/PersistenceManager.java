package com.entrega2;
// import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;//
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

public class PersistenceManager {
  //((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.WARN);
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
     * Devuelve el EntityManagerFactory único de la aplicación.
     */
    public static EntityManagerFactory getEntityManagerFactory() {
        return emf;
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
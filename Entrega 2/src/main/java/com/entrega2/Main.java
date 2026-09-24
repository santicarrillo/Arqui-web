package com.entrega2;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import java.time.LocalDateTime;

public class Main {
    public static void main(String[] args) {
        try {
            System.out.println("=== Iniciando TP2 - JPA ===\n");

            // 1. CREAR DIRECCIONES
            crearDirecciones();

            // 2. CREAR PERSONAS
            crearPersonas();

            // 3. CREAR TURNOS
            crearTurnos();

            // 4. ASIGNAR PERSONAS A TURNOS
            asignarPersonasATurnos();

            // 5. CREAR SOCIOS
            crearSocios();

            // 6. CONSULTAR DATOS
            consultarDatos();

            // 7. MODIFICAR DATOS
            modificarDatos();

            // 8. ELIMINAR DATOS
            eliminarDatos();

            System.out.println("\n=== Fin del programa ===");

        } finally {
            PersistenceManager.closeEntityManagerFactory();
        }
    }

    private static void crearDirecciones() {
        System.out.println("--- CREANDO DIRECCIONES ---");
        EntityManager em = PersistenceManager.getEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            Direccion dir1 = new Direccion("Calle 1, 123", "Buenos Aires");
            Direccion dir2 = new Direccion("Avenida Siempre Viva, 742", "La Plata");
            Direccion dir3 = new Direccion("Calle Principal, 456", "Tandil");

            em.persist(dir1);
            em.persist(dir2);
            em.persist(dir3);

            tx.commit();
            System.out.println("✓ 3 direcciones creadas\n");

        } catch (Exception e) {
            tx.rollback();
            System.err.println("Error al crear direcciones: " + e.getMessage());
        }
    }

    private static void crearPersonas() {
        System.out.println("--- CREANDO PERSONAS ---");
        EntityManager em = PersistenceManager.getEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            // Obtener las direcciones creadas
            Direccion dir1 = em.find(Direccion.class, 1);
            Direccion dir2 = em.find(Direccion.class, 2);
            Direccion dir3 = em.find(Direccion.class, 3);

            Persona p1 = new Persona("Juan", 25, dir1);
            Persona p2 = new Persona("María", 28, dir2);
            Persona p3 = new Persona("Pedro", 30, dir1);
            Persona p4 = new Persona("Laura", 22, dir3);

            em.persist(p1);
            em.persist(p2);
            em.persist(p3);
            em.persist(p4);

            tx.commit();
            System.out.println("✓ 4 personas creadas\n");

        } catch (Exception e) {
            tx.rollback();
            System.err.println("Error al crear personas: " + e.getMessage());
        }
    }

    private static void crearTurnos() {
        System.out.println("--- CREANDO TURNOS ---");
        EntityManager em = PersistenceManager.getEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            Turno t1 = new Turno(LocalDateTime.of(2024, 1, 15, 9, 0));
            Turno t2 = new Turno(LocalDateTime.of(2024, 1, 15, 14, 0));
            Turno t3 = new Turno(LocalDateTime.of(2024, 1, 16, 9, 0));

            em.persist(t1);
            em.persist(t2);
            em.persist(t3);

            tx.commit();
            System.out.println("✓ 3 turnos creados\n");

        } catch (Exception e) {
            tx.rollback();
            System.err.println("Error al crear turnos: " + e.getMessage());
        }
    }

    private static void asignarPersonasATurnos() {
        System.out.println("--- ASIGNANDO PERSONAS A TURNOS ---");
        EntityManager em = PersistenceManager.getEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            Persona p1 = em.find(Persona.class, 1);
            Persona p2 = em.find(Persona.class, 2);
            Persona p3 = em.find(Persona.class, 3);
            Persona p4 = em.find(Persona.class, 4);

            Turno t1 = em.find(Turno.class, 1);
            Turno t2 = em.find(Turno.class, 2);

            t1.agregarPersona(p1);
            t1.agregarPersona(p2);
            t2.agregarPersona(p3);
            t2.agregarPersona(p4);

            tx.commit();
            System.out.println("✓ Personas asignadas a turnos\n");

        } catch (Exception e) {
            tx.rollback();
            System.err.println("Error al asignar personas a turnos: " + e.getMessage());
        }
    }

    private static void crearSocios() {
        System.out.println("--- CREANDO SOCIOS ---");
        EntityManager em = PersistenceManager.getEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            Persona p1 = em.find(Persona.class, 1);
            Persona p2 = em.find(Persona.class, 2);

            Socio s1 = new Socio("Socio Activo", p1);
            Socio s2 = new Socio("Socio Pasivo", p2);

            em.persist(s1);
            em.persist(s2);

            tx.commit();
            System.out.println("✓ 2 socios creados\n");

        } catch (Exception e) {
            tx.rollback();
            System.err.println("Error al crear socios: " + e.getMessage());
        }
    }

    private static void consultarDatos() {
        System.out.println("--- CONSULTANDO DATOS ---");
        EntityManager em = PersistenceManager.getEntityManager();

        try {
            // Consultar todas las personas
            var personas = em.createQuery("SELECT p FROM Persona p", Persona.class)
                    .getResultList();
            System.out.println("Personas en la BD:");
            for (Persona p : personas) {
                System.out.println("  - " + p.getNombre() + " (" + p.getAnos() + " años) - " + p.getDireccion().getCiudad());
            }

            // Consultar todas las direcciones
            var direcciones = em.createQuery("SELECT d FROM Direccion d", Direccion.class)
                    .getResultList();
            System.out.println("\nDirecciones en la BD:");
            for (Direccion d : direcciones) {
                System.out.println("  - " + d.getCalle() + " (" + d.getCiudad() + ")");
            }

            System.out.println();

        } catch (Exception e) {
            System.err.println("Error al consultar datos: " + e.getMessage());
        }
    }

    private static void modificarDatos() {
        System.out.println("--- MODIFICANDO DATOS ---");
        EntityManager em = PersistenceManager.getEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            Persona p1 = em.find(Persona.class, 1);
            if (p1 != null) {
                p1.setAnos(26); // Aumentar edad
                System.out.println("✓ Edad de " + p1.getNombre() + " actualizada a " + p1.getAnos());
            }

            tx.commit();
            System.out.println();

        } catch (Exception e) {
            tx.rollback();
            System.err.println("Error al modificar datos: " + e.getMessage());
        }
    }

    private static void eliminarDatos() {
        System.out.println("--- ELIMINANDO DATOS ---");
        EntityManager em = PersistenceManager.getEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            // Eliminar un socio
            Socio socio = em.find(Socio.class, 2);
            if (socio != null) {
                em.remove(socio);
                System.out.println("✓ Socio eliminado");
            }

            tx.commit();
            System.out.println();

        } catch (Exception e) {
            tx.rollback();
            System.err.println("Error al eliminar datos: " + e.getMessage());
        }
    }
}

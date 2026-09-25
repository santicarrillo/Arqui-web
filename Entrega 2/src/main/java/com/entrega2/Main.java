package com.entrega2;

import com.entrega2.utils.HelperMySQL;


public class Main {
    public static void main(String[] args) {
        try {
            System.out.println("=== Iniciando TP2 - JPA ===\n");

            HelperMySQL helper = new com.entrega2.utils.HelperMySQL();
            helper.populateDB();
            System.out.println("¡Base de datos cargada exitosamente!");

            System.out.println("\n=== Fin del programa ===");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            PersistenceManager.closeEntityManager();
            PersistenceManager.closeEntityManagerFactory();
        }
    }
}

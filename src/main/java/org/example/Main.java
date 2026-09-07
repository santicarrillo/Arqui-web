package org.example;

import org.example.factory.AbstractFactory;
import org.example.dao.ProductoDAO;
import org.example.dao.ClienteDAO;
import org.example.entities.Producto;
import org.example.entities.Cliente;
import org.example.utils.HelperMySQL;

import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        // Incisos 1 y 2
        HelperMySQL db = new HelperMySQL();
        db.dropTables();
        db.createTables();   // <- inciso 1
        db.populateDB();     // <- inciso 2
        db.closeConnection();

        //Abstract Factory + Singleton
        AbstractFactory factory = AbstractFactory.getDAOFactory(AbstractFactory.MYSQL_JDBC);

        // Inciso 3
        ProductoDAO productoDAO = factory.getProductoDAO();
        Producto top = productoDAO.obtenerQueMasRecaudo();
        System.out.println("\nProducto que mas recaudo:");
        System.out.println(top);

        // Inciso 4
        ClienteDAO clienteDAO = factory.getClienteDAO();
        List<Cliente> clientes = clienteDAO.obtenerPorFacturacion();
        System.out.println("\nClientes ordenados por facturacion:");
        for (Cliente c : clientes) {
            System.out.println(c);
        }
    }
}

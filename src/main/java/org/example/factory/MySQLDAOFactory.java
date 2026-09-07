package org.example.factory;

import org.example.dao.ProductoDAO;
import org.example.dao.ClienteDAO;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MySQLDAOFactory extends AbstractFactory {
    private static MySQLDAOFactory instance = null;
    public static final String URI = "jdbc:mysql://localhost:3306/integrador";
    public static Connection conn;

    private MySQLDAOFactory() {
    }

    public static synchronized MySQLDAOFactory getInstance() {
        if (instance == null) {
            instance = new MySQLDAOFactory();
        }
        return instance;
    }

    public static Connection createConnection() {
        if (conn != null) {
            return conn;
        }
        try {
            conn = DriverManager.getConnection(URI, "root", "root");
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return conn;
    }

    public void closeConnection() {
        try {
            if (conn != null) conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public ProductoDAO getProductoDAO() {
        return new ProductoDAO(createConnection());
    }

    @Override
    public ClienteDAO getClienteDAO() {
        return new ClienteDAO(createConnection());
    }
}

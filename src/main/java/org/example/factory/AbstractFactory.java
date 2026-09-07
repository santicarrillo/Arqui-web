package org.example.factory;

import org.example.dao.ProductoDAO;
import org.example.dao.ClienteDAO;

public abstract class AbstractFactory {
    public static final int MYSQL_JDBC = 1;
    public static final int DERBY_JDBC = 2;

    public abstract ProductoDAO getProductoDAO();
    public abstract ClienteDAO getClienteDAO();

    public static AbstractFactory getDAOFactory(int whichFactory) {
        switch (whichFactory) {
            case MYSQL_JDBC:
                return MySQLDAOFactory.getInstance();
            case DERBY_JDBC:
                return null;
            default:
                return null;
        }
    }
}

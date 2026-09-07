package org.example.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class HelperMySQL {
    private Connection conn = null;
    protected static final String RES = "src/main/resources/";

    public HelperMySQL() {
        String uri = "jdbc:mysql://localhost:3306/integrador";
        try {
            conn = DriverManager.getConnection(uri, "root", "root");
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    protected Connection getConn() {
        return conn;
    }

    public void closeConnection() {
        try {
            if (conn != null) conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Borra en orden inverso a las FK para poder re-ejecutar.
    public void dropTables() throws SQLException {
        conn.prepareStatement("DROP TABLE IF EXISTS Factura_Producto").execute();
        conn.prepareStatement("DROP TABLE IF EXISTS Factura").execute();
        conn.prepareStatement("DROP TABLE IF EXISTS Producto").execute();
        conn.prepareStatement("DROP TABLE IF EXISTS Cliente").execute();
        conn.commit();
    }

    // INCISO 1: crea las 4 tablas en orden de FK (padres primero).
    public void createTables() throws SQLException {
        conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS Cliente (" +
                "idCliente INT PRIMARY KEY, nombre VARCHAR(500), email VARCHAR(150))").execute();
        conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS Producto (" +
                "idProducto INT PRIMARY KEY, nombre VARCHAR(45), valor FLOAT)").execute();
        conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS Factura (" +
                "idFactura INT PRIMARY KEY, idCliente INT, " +
                "FOREIGN KEY (idCliente) REFERENCES Cliente(idCliente))").execute();
        conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS Factura_Producto (" +
                "idFactura INT, idProducto INT, cantidad INT, " +
                "PRIMARY KEY (idFactura, idProducto), " +
                "FOREIGN KEY (idFactura) REFERENCES Factura(idFactura), " +
                "FOREIGN KEY (idProducto) REFERENCES Producto(idProducto))").execute();
        conn.commit();
    }

    public void populateDB() throws Exception {
        // INCISO 2: leer los 4 CSV de src/main/resources (clientes, productos, facturas,
        // facturas-productos) con Apache Commons CSV e insertarlos con PreparedStatement,
        // EN ORDEN DE FK (Cliente, Producto, Factura, Factura_Producto). Hacer conn.commit().
        // Los CSV ya estan en la carpeta resources.
    }
}

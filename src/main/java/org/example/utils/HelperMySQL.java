package org.example.utils;

import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.example.entities.Cliente;
import org.example.entities.Factura;
import org.example.entities.FacturaProducto;
import org.example.entities.Producto;

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
        // Clientes
        PreparedStatement ps = conn.prepareStatement("INSERT INTO Cliente (idCliente, nombre, email) VALUES (?, ?, ?)");
        for (CSVRecord row : CSVFormat.DEFAULT.withHeader().parse(new FileReader(RES + "clientes.csv"))) {
            Cliente c = new Cliente(Integer.parseInt(row.get("idCliente")), row.get("nombre"), row.get("email"));
            ps.setInt(1, c.getIdCliente());
            ps.setString(2, c.getNombre());
            ps.setString(3, c.getEmail());
            ps.executeUpdate();
        }
        conn.commit();

        // Productos
        ps = conn.prepareStatement("INSERT INTO Producto (idProducto, nombre, valor) VALUES (?, ?, ?)");
        for (CSVRecord row : CSVFormat.DEFAULT.withHeader().parse(new FileReader(RES + "productos.csv"))) {
            Producto p = new Producto(Integer.parseInt(row.get("idProducto")), row.get("nombre"), Double.parseDouble(row.get("valor")));
            ps.setInt(1, p.getIdProducto());
            ps.setString(2, p.getNombre());
            ps.setDouble(3, p.getValor());
            ps.executeUpdate();
        }
        conn.commit();

        // Facturas
        ps = conn.prepareStatement("INSERT INTO Factura (idFactura, idCliente) VALUES (?, ?)");
        for (CSVRecord row : CSVFormat.DEFAULT.withHeader().parse(new FileReader(RES + "facturas.csv"))) {
            Factura f = new Factura(Integer.parseInt(row.get("idFactura")), Integer.parseInt(row.get("idCliente")));
            ps.setInt(1, f.getIdFactura());
            ps.setInt(2, f.getIdCliente());
            ps.executeUpdate();
        }
        conn.commit();

        // Factura_Producto
        ps = conn.prepareStatement("INSERT INTO Factura_Producto (idFactura, idProducto, cantidad) VALUES (?, ?, ?)");
        for (CSVRecord row : CSVFormat.DEFAULT.withHeader().parse(new FileReader(RES + "facturas-productos.csv"))) {
            FacturaProducto fp = new FacturaProducto(
                    Integer.parseInt(row.get("idFactura")),
                    Integer.parseInt(row.get("idProducto")),
                    Integer.parseInt(row.get("cantidad")));
            ps.setInt(1, fp.getIdFactura());
            ps.setInt(2, fp.getIdProducto());
            ps.setInt(3, fp.getCantidad());
            ps.executeUpdate();
        }
        conn.commit();

        System.out.println("Base cargada.");
    }
}

package org.example.dao;

import org.example.entities.Cliente;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ClienteDAO {
    private Connection conn;

    public ClienteDAO(Connection conn) {
        this.conn = conn;
    }

    public List<Cliente> obtenerPorFacturacion() {
        String query = "SELECT c.idCliente, c.nombre, c.email " +
                "FROM Cliente c " +
                "JOIN Factura f ON c.idCliente = f.idCliente " +
                "JOIN Factura_Producto fp ON f.idFactura = fp.idFactura " +
                "JOIN Producto p ON fp.idProducto = p.idProducto " +
                "GROUP BY c.idCliente, c.nombre, c.email " +
                "ORDER BY SUM(fp.cantidad * p.valor) DESC";
        List<Cliente> listado = new ArrayList<>();
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(query);
            rs = ps.executeQuery();
            while (rs.next()) {
                listado.add(new Cliente(
                        rs.getInt("idCliente"),
                        rs.getString("nombre"),
                        rs.getString("email")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
                conn.commit();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return listado;
    }
}

package org.example.dao;

import org.example.entities.Producto;

import java.sql.Connection;

public class ProductoDAO {
    private Connection conn;

    public ProductoDAO(Connection conn) {
        this.conn = conn;
    }

    public Producto obtenerQueMasRecaudo() {
        // INCISO 3: devolver el producto que mas recaudo.
        return null;
    }
}

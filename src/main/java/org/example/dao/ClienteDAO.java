package org.example.dao;

import org.example.entities.Cliente;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

public class ClienteDAO {
    private Connection conn;

    public ClienteDAO(Connection conn) {
        this.conn = conn;
    }

    public List<Cliente> obtenerPorFacturacion() {
        // INCISO 4: devolver la lista de clientes ordenada por cuanto se le facturo.
        return new ArrayList<>();
    }
}

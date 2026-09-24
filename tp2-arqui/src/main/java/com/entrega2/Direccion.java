package com.entrega2;

import jakarta.persistence.*;

@Entity
@Table(name = "Direccion")
public class Direccion {
 @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
 @Column(name = "calle",length = 255,nullable = false)
    private String calle;
    @Column(name = "ciudad", length = 255, nullable = false)
    private String ciudad;

    public Direccion() {
    }

    public Direccion(String calle, String ciudad) {
        this.calle = calle;
        this.ciudad = ciudad;
    }

    public String getCalle() {
        return calle;
    }

    public void setCalle(String calle) {
        this.calle = calle;
    }

    public String getCiudad() {
        return ciudad;
    }

    public void setCiudad(String ciudad) {
        this.ciudad = ciudad;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
    @Override
    public String toString() {
        return "Direccion{" +
                "id=" + id +
                ", calle='" + calle + '\'' +
                ", ciudad='" + ciudad + '\'' +
                '}';
    }
}

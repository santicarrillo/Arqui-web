package com.entrega2.entitys;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import java.util.List;

@Entity
public class Carrera {

    @Id
    @Column(name = "id_carrera")
    private int idCarrera;

    @Column(name = "nombre_car", length = 60)
    private String nombre;

    @Column
    private int duracion;

    @OneToMany(mappedBy = "carrera", cascade = CascadeType.ALL)
    private List<Inscripcion> inscripciones;

    public Carrera() {
    }

    public Carrera(int idCarrera, String nombre, int duracion) {
        this.idCarrera = idCarrera;
        this.nombre = nombre;
        this.duracion = duracion;
    }

    public int getIdCarrera() {
        return idCarrera;
    }

    public void setIdCarrera(int idCarrera) {
        this.idCarrera = idCarrera;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public int getDuracion() {
        return duracion;
    }

    public void setDuracion(int duracion) {
        this.duracion = duracion;
    }

    public List<Inscripcion> getInscripciones() {
        return inscripciones;
    }

    @Override
    public String toString() {
        return "Carrera{" +
                "idCarrera=" + idCarrera +
                ", nombre='" + nombre + '\'' +
                '}';
    }
}

package com.example.entity;
import jakarta.persistence.*;

import java.util.ArrayList;
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
    private List<Inscripcion> inscripciones = new ArrayList<>();

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
        return "Carrera{idCarrera=" + idCarrera + ", nombre='" + nombre + "', duracion=" + duracion + "}";
    }
}
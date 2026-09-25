package com.entrega2.entitys;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Inscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "dni_estudiante")
    private Estudiante estudiante;

    @ManyToOne
    @JoinColumn(name = "id_carrera")
    private Carrera carrera;

    @Column(name = "inscripcion")
    private int fechaInscripcion;

    @Column(name = "graduacion")
    private int fechaGraduacion;

    @Column
    private int antiguedad;

    public Inscripcion() {
    }

    public Inscripcion(int id, Estudiante estudiante, Carrera carrera, int fechaInscripcion, int fechaGraduacion,
            int antiguedad) {
        this.id = id;
        this.estudiante = estudiante;
        this.carrera = carrera;
        this.fechaInscripcion = fechaInscripcion;
        this.fechaGraduacion = fechaGraduacion;
        this.antiguedad = antiguedad;
    }

    public int getId() {
        return id;
    }

    public Estudiante getEstudiante() {
        return estudiante;
    }

    public void setEstudiante(Estudiante estudiante) {
        this.estudiante = estudiante;
    }

    public Carrera getCarrera() {
        return carrera;
    }

    public void setCarrera(Carrera carrera) {
        this.carrera = carrera;
    }

    public int getFechaInscripcion() {
        return fechaInscripcion;
    }

    public void setFechaInscripcion(int fechaInscripcion) {
        this.fechaInscripcion = fechaInscripcion;
    }

    public int getFechaGraduacion() {
        return fechaGraduacion;
    }

    public void setFechaGraduacion(int fechaGraduacion) {
        this.fechaGraduacion = fechaGraduacion;
    }

    public int getAntiguedad() {
        return antiguedad;
    }

    public void setAntiguedad(int antiguedad) {
        this.antiguedad = antiguedad;
    }

    @Override
    public String toString() {
        return "Inscripcion{" +
                "id=" + id +
                ", estudiante=" + (estudiante != null ? estudiante.getNumeroDocumento() : null) +
                ", carrera=" + (carrera != null ? carrera.getNombre() : null) +
                ", fechaInscripcion=" + fechaInscripcion +
                ", fechaGraduacion=" + fechaGraduacion +
                ", antiguedad=" + antiguedad +
                '}';
    }
}

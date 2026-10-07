package com.example.entity;


import jakarta.persistence.*;

/**
 * Tabla intermedia entre Estudiante y Carrera (relación muchos a muchos con atributos propios).
 * Guarda la antigüedad en la carrera y si el estudiante se graduó o no.
 */
@Entity
public class Inscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "dni_estudiante")
    private Estudiante estudiante;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_carrera")
    private Carrera carrera;

    // Año de inscripción
    @Column(name = "inscripcion")
    private int fechaInscripcion;

    // Año de graduación (0 = todavía no se graduó)
    @Column(name = "graduacion")
    private int fechaGraduacion;

    @Column
    private int antiguedad;

    public Inscripcion() {
    }

    public Inscripcion(Estudiante estudiante, Carrera carrera, int fechaInscripcion, int fechaGraduacion,
                       int antiguedad) {
        this.estudiante = estudiante;
        this.carrera = carrera;
        this.fechaInscripcion = fechaInscripcion;
        this.fechaGraduacion = fechaGraduacion;
        this.antiguedad = antiguedad;
    }

    public int getId() { return id; }

    public Estudiante getEstudiante() { return estudiante; }
    public void setEstudiante(Estudiante estudiante) { this.estudiante = estudiante; }

    public Carrera getCarrera() { return carrera; }
    public void setCarrera(Carrera carrera) { this.carrera = carrera; }

    public int getFechaInscripcion() { return fechaInscripcion; }
    public void setFechaInscripcion(int fechaInscripcion) { this.fechaInscripcion = fechaInscripcion; }

    public int getFechaGraduacion() { return fechaGraduacion; }
    public void setFechaGraduacion(int fechaGraduacion) { this.fechaGraduacion = fechaGraduacion; }

    public int getAntiguedad() { return antiguedad; }
    public void setAntiguedad(int antiguedad) { this.antiguedad = antiguedad; }

    // "Si se graduó o no" se deriva del año de graduación
    public boolean isGraduado() {
        return fechaGraduacion != 0;
    }

    @Override
    public String toString() {
        return "Inscripcion{id=" + id
                + ", estudiante=" + (estudiante != null ? estudiante.getNumeroDocumento() : null)
                + ", carrera=" + (carrera != null ? carrera.getNombre() : null)
                + ", inscripcion=" + fechaInscripcion + ", graduacion=" + fechaGraduacion
                + ", antiguedad=" + antiguedad + "}";
    }
}

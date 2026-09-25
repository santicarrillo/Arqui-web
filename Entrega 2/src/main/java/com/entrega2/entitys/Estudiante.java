package com.entrega2.entitys;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import java.util.List;

@Entity
public class Estudiante {

    @Id
    @Column(name = "dni")
    private int numeroDocumento;

    @Column(name = "LU", unique = true)
    private int libretaUniversitaria;

    @Column(length = 60)
    private String nombre;

    @Column(length = 60)
    private String apellido;

    @Column
    private int edad;

    @Column(length = 30)
    private String genero;

    @Column(name = "ciudad", length = 80)
    private String ciudadResidencia;

    @OneToMany(mappedBy = "estudiante", cascade = CascadeType.ALL)
    private List<Inscripcion> inscripciones;

    public Estudiante() {
    }

    public Estudiante(int libretaUniversitaria, int numeroDocumento, String nombres, String apellido, int edad,
            String genero, String ciudadResidencia) {
        this.libretaUniversitaria = libretaUniversitaria;
        this.numeroDocumento = numeroDocumento;
        this.nombre = nombres;
        this.apellido = apellido;
        this.edad = edad;
        this.genero = genero;
        this.ciudadResidencia = ciudadResidencia;
    }

    public int getLibretaUniversitaria() {
        return libretaUniversitaria;
    }

    public int getNumeroDocumento() {
        return numeroDocumento;
    }

    public void setNumeroDocumento(int numeroDocumento) {
        this.numeroDocumento = numeroDocumento;
    }

    public String getNombres() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    public int getEdad() {
        return edad;
    }

    public void setEdad(int edad) {
        this.edad = edad;
    }

    public String getGenero() {
        return genero;
    }

    public void setGenero(String genero) {
        this.genero = genero;
    }

    public String getCiudadResidencia() {
        return ciudadResidencia;
    }

    public void setCiudadResidencia(String ciudadResidencia) {
        this.ciudadResidencia = ciudadResidencia;
    }

    public List<Inscripcion> getInscripciones() {
        return inscripciones;
    }

    public void setInscripciones(List<Inscripcion> inscripciones) {
        this.inscripciones = inscripciones;
    }

    @Override
    public String toString() {
        return "Estudiante{" +
                "libretaUniversitaria=" + libretaUniversitaria +
                ", numeroDocumento=" + numeroDocumento +
                ", nombres='" + nombre + '\'' +
                ", apellido='" + apellido + '\'' +
                ", edad=" + edad +
                ", genero='" + genero + '\'' +
                ", ciudadResidencia='" + ciudadResidencia + '\'' +
                '}';
    }
}

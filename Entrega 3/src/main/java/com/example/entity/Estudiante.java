package com.example.entity;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Estudiante {

    // Se usa el DNI como clave primaria (igual que en la Entrega 2)
    @Id
    @Column(name = "dni")
    private int numeroDocumento;

    // La libreta universitaria es única, pero no es la PK
    @Column(name = "LU", unique = true, nullable = false)
    private int libretaUniversitaria;

    @Column(length = 60, nullable = false)
    private String nombre;

    @Column(length = 60, nullable = false)
    private String apellido;

    @Column
    private int edad;

    @Column(length = 30)
    private String genero;

    @Column(name = "ciudad", length = 80)
    private String ciudadResidencia;

    @OneToMany(mappedBy = "estudiante", cascade = CascadeType.ALL)
    private List<Inscripcion> inscripciones = new ArrayList<>();

    public Estudiante() {
    }

    public Estudiante(int libretaUniversitaria, int numeroDocumento, String nombre, String apellido, int edad,
                      String genero, String ciudadResidencia) {
        this.libretaUniversitaria = libretaUniversitaria;
        this.numeroDocumento = numeroDocumento;
        this.nombre = nombre;
        this.apellido = apellido;
        this.edad = edad;
        this.genero = genero;
        this.ciudadResidencia = ciudadResidencia;
    }

    public int getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(int numeroDocumento) { this.numeroDocumento = numeroDocumento; }

    public int getLibretaUniversitaria() { return libretaUniversitaria; }
    public void setLibretaUniversitaria(int libretaUniversitaria) { this.libretaUniversitaria = libretaUniversitaria; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

    public int getEdad() { return edad; }
    public void setEdad(int edad) { this.edad = edad; }

    public String getGenero() { return genero; }
    public void setGenero(String genero) { this.genero = genero; }

    public String getCiudadResidencia() { return ciudadResidencia; }
    public void setCiudadResidencia(String ciudadResidencia) { this.ciudadResidencia = ciudadResidencia; }

    public List<Inscripcion> getInscripciones() { return inscripciones; }

    @Override
    public String toString() {
        return "Estudiante{LU=" + libretaUniversitaria + ", dni=" + numeroDocumento + ", nombre='" + nombre
                + "', apellido='" + apellido + "', edad=" + edad + ", genero='" + genero
                + "', ciudad='" + ciudadResidencia + "'}";
    }
}


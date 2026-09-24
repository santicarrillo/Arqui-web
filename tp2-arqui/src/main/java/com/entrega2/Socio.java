package com.entrega2;

import jakarta.persistence.*;

@Entity
@Table(name = "Socio")
public class Socio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "tipo", length = 255, nullable = false)
    private String tipo;

    @OneToOne
    @JoinColumn(name = "persona_id", nullable = false, unique = true)
    private Persona persona;

    // Constructores
    public Socio() {
    }

    public Socio(String tipo, Persona persona) {
        this.tipo = tipo;
        this.persona = persona;
    }

    // Getters y Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Persona getPersona() {
        return persona;
    }

    public void setPersona(Persona persona) {
        this.persona = persona;
    }

    @Override
    public String toString() {
        return "Socio{" +
                "id=" + id +
                ", tipo='" + tipo + '\'' +
                ", persona=" + persona +
                '}';
    }
}
package com.entrega2;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "Turno")
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    // Relación ManyToMany con Persona (lado inverso)
    @ManyToMany(mappedBy = "turnos")
    private Set<Persona> personas = new HashSet<>();

    // Constructores
    public Turno() {
    }

    public Turno(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    // Getters y Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public Set<Persona> getPersonas() {
        return personas;
    }

    public void setPersonas(Set<Persona> personas) {
        this.personas = personas;
    }

    public void agregarPersona(Persona persona) {
        personas.add(persona);
        persona.getTurnos().add(this);
    }

    public void removerPersona(Persona persona) {
        personas.remove(persona);
        persona.getTurnos().remove(this);
    }

    @Override
    public String toString() {
        return "Turno{" +
                "id=" + id +
                ", fecha=" + fecha +
                ", cantidadPersonas=" + personas.size() +
                '}';
    }
}
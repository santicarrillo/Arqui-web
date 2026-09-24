package com.entrega2;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
@Entity
@Table(name = "Persona")
public class Persona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "nombre", length = 255, nullable = false)
    private String nombre;

    @Column(name = "años", nullable = false)
    private int anos;

    @ManyToOne
    @JoinColumn(name = "domicilio_id", nullable = false)
    private Direccion direccion;

    // Relación ManyToMany con Turno
    @ManyToMany
    @JoinTable(
            name = "Turno_Persona",
            joinColumns = @JoinColumn(name = "persona_id"),
            inverseJoinColumns = @JoinColumn(name = "turno_id")
    )
    private Set<Turno> turnos = new HashSet<>();

    // Relación OneToOne con Socio (unidireccional)
    // Si quisieras bidireccional, agregarías @OneToOne(mappedBy = "persona") en Socio

    // Constructores
    public Persona() {
    }

    public Persona(String nombre, int anos, Direccion direccion) {
        this.nombre = nombre;
        this.anos = anos;
        this.direccion = direccion;
    }

    // Getters y Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public int getAnos() {
        return anos;
    }

    public void setAnos(int anos) {
        this.anos = anos;
    }

    public Direccion getDireccion() {
        return direccion;
    }

    public void setDireccion(Direccion direccion) {
        this.direccion = direccion;
    }

    public Set<Turno> getTurnos() {
        return turnos;
    }

    public void setTurnos(Set<Turno> turnos) {
        this.turnos = turnos;
    }

    public void agregarTurno(Turno turno) {
        turnos.add(turno);
        turno.getPersonas().add(this);
    }

    public void removerTurno(Turno turno) {
        turnos.remove(turno);
        turno.getPersonas().remove(this);
    }

    @Override
    public String toString() {
        return "Persona{" +
                "id=" + id +
                ", nombre='" + nombre + '\'' +
                ", anos=" + anos +
                ", direccion=" + direccion +
                '}';
    }
}
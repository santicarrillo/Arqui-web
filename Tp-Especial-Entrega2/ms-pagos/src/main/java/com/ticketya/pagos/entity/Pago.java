package com.ticketya.pagos.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pago")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // UNIQUE
    @Column(name = "compra_id", unique = true, nullable = false)
    private Long compraId;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(nullable = false)
    private Double monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPago estado;

    @Column(nullable = false)
    private LocalDateTime fecha;

    public Pago() {
    }

    public Pago(Long compraId, Long clienteId, Double monto, EstadoPago estado, LocalDateTime fecha) {
        this.compraId = compraId;
        this.clienteId = clienteId;
        this.monto = monto;
        this.estado = estado;
        this.fecha = fecha;
    }


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompraId() { return compraId; }
    public void setCompraId(Long compraId) { this.compraId = compraId; }

    public Long getClienteId() { return clienteId; }
    public void setClienteId(Long clienteId) { this.clienteId = clienteId; }

    public Double getMonto() { return monto; }
    public void setMonto(Double monto) { this.monto = monto; }

    public EstadoPago getEstado() { return estado; }
    public void setEstado(EstadoPago estado) { this.estado = estado; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
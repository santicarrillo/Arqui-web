package com.ticketya.pagos.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reintegro")
public class Reintegro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pago_id", nullable = false)
    private Pago pago;

    @Column(name = "detalle_compra_id", nullable = false)
    private Long detalleCompraId;

    @Column(nullable = false)
    private Double monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoReintegro estado;

    @Column(nullable = false)
    private LocalDateTime fecha;

    public Reintegro() {
    }

    public Reintegro(Pago pago, Long detalleCompraId, Double monto, EstadoReintegro estado, LocalDateTime fecha) {
        this.pago = pago;
        this.detalleCompraId = detalleCompraId;
        this.monto = monto;
        this.estado = estado;
        this.fecha = fecha;
    }


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Pago getPago() { return pago; }
    public void setPago(Pago pago) { this.pago = pago; }

    public Long getDetalleCompraId() { return detalleCompraId; }
    public void setDetalleCompraId(Long detalleCompraId) { this.detalleCompraId = detalleCompraId; }

    public Double getMonto() { return monto; }
    public void setMonto(Double monto) { this.monto = monto; }

    public EstadoReintegro getEstado() { return estado; }
    public void setEstado(EstadoReintegro estado) { this.estado = estado; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
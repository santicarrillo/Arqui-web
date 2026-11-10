package com.ticketya.pagos.repository;

import com.ticketya.pagos.entity.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {
    // para evitar el doble cobbro y para que Ventas consulte si ya se pago
    Optional<Pago> findByCompraId(Long compraId);
}
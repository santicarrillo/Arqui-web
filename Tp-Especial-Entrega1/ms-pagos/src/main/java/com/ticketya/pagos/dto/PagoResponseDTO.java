package com.ticketya.pagos.dto;

import com.ticketya.pagos.entity.EstadoPago;
import java.time.LocalDateTime;

public record PagoResponseDTO(
        Long id,
        Long compraId,
        Double monto,
        EstadoPago estado,
        LocalDateTime fecha
) {}
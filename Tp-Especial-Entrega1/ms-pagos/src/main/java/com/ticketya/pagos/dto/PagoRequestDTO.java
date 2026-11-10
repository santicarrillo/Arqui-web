package com.ticketya.pagos.dto;

public record PagoRequestDTO(
        Long compraId,
        Long clienteId,
        Double monto
) {}
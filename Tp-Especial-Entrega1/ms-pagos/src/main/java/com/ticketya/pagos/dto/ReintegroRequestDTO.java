package com.ticketya.pagos.dto;

public record ReintegroRequestDTO(
        Long detalleCompraId,
        Double monto
) {}
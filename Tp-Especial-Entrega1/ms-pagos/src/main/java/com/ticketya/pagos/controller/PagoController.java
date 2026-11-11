package com.ticketya.pagos.controller;

import com.ticketya.pagos.dto.PagoRequestDTO;
import com.ticketya.pagos.dto.PagoResponseDTO;
import com.ticketya.pagos.entity.EstadoPago;
import com.ticketya.pagos.entity.Pago;
import com.ticketya.pagos.service.PagoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pagos")
public class PagoController {

    private final PagoService pagoService;

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    // POST /pagos (Ventas llama acá para cobrar)
    @PostMapping
    public ResponseEntity<PagoResponseDTO> cobrar(@RequestBody PagoRequestDTO request) {
        Pago pago = pagoService.procesarPago(request);

        PagoResponseDTO response = new PagoResponseDTO(
                pago.getId(),
                pago.getCompraId(),
                pago.getMonto(),
                pago.getEstado(),
                pago.getFecha()
        );

        // Si fue rechazado (por si lo cambiás a futuro), devuelve 402, si no 200.
        if (pago.getEstado() == EstadoPago.RECHAZADO) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response);
        }

        return ResponseEntity.ok(response);
    }

    // GET /pagos?compraId=123 (Ventas llama acá al reintentar un pago)
    @GetMapping
    public ResponseEntity<PagoResponseDTO> consultarPago(@RequestParam Long compraId) {
        // Si no hay pago para esa compra devolvemos 404 (no 500): ventas lo toma como "todavia no se cobro".
        return pagoService.buscarOpcionalPorCompraId(compraId)
                .map(pago -> ResponseEntity.ok(new PagoResponseDTO(
                        pago.getId(), pago.getCompraId(), pago.getMonto(), pago.getEstado(), pago.getFecha())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // POST /pagos/{id}/reintegro (Ventas llama acá cuando se aprueba una devolución)
    @PostMapping("/{id}/reintegro")
    public ResponseEntity<Void> reintegrar(@PathVariable Long id, @RequestBody com.ticketya.pagos.dto.ReintegroRequestDTO request) {
        pagoService.procesarReintegro(id, request);
        return ResponseEntity.ok().build();
    }
}
package com.ticketya.pagos.service;

import com.ticketya.pagos.dto.PagoRequestDTO;
import com.ticketya.pagos.dto.ReintegroRequestDTO;
import com.ticketya.pagos.entity.EstadoPago;
import com.ticketya.pagos.entity.EstadoReintegro;
import com.ticketya.pagos.entity.Pago;
import com.ticketya.pagos.entity.Reintegro;
import com.ticketya.pagos.repository.PagoRepository;
import com.ticketya.pagos.repository.ReintegroRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PagoService {

    private final PagoRepository pagoRepository;
    private final ReintegroRepository reintegroRepository;

    public PagoService(PagoRepository pagoRepository, ReintegroRepository reintegroRepository) {
        this.pagoRepository = pagoRepository;
        this.reintegroRepository = reintegroRepository;
    }

    @Transactional
    public Pago procesarPago(PagoRequestDTO request) {
        //  verificar si ya existe un pago para esta compra (Idempotencia)
        Optional<Pago> pagoExistente = pagoRepository.findByCompraId(request.compraId());
        if (pagoExistente.isPresent()) {
            return pagoExistente.get();
        }

        // si no existe, crear un nuevo pago. Como es un mock, lo aprobamos siempre
        Pago nuevoPago = new Pago(
                request.compraId(),
                request.clienteId(),
                request.monto(),
                EstadoPago.APROBADO,
                LocalDateTime.now()
        );

        return pagoRepository.save(nuevoPago);
    }

    @Transactional(readOnly = true)
    public Pago buscarPorCompraId(Long compraId) {
        return pagoRepository.findByCompraId(compraId)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado para la compra: " + compraId));
    }

    @Transactional(readOnly = true)
    public Optional<Pago> buscarOpcionalPorCompraId(Long compraId) {
        return pagoRepository.findByCompraId(compraId);
    }

    @Transactional
    public void procesarReintegro(Long pagoId, ReintegroRequestDTO request) {
        Pago pago = pagoRepository.findById(pagoId)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado: " + pagoId));

        Reintegro nuevoReintegro = new Reintegro(
                pago,
                request.detalleCompraId(),
                request.monto(),
                EstadoReintegro.SOLICITADO,
                LocalDateTime.now()
        );

        reintegroRepository.save(nuevoReintegro);
    }
}
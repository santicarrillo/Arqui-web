package com.example.service;

import com.example.entity.Carrera;
import com.example.repository.CarreraRepository;
import com.example.service.dto.Carrera.CarreraInscriptosResponseDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CarreraService {

    private final CarreraRepository carreraRepository;

    public CarreraService(CarreraRepository carreraRepository) {
        this.carreraRepository = carreraRepository;
    }

    public List<CarreraInscriptosResponseDTO> findCarrerasOrdenadasPorInscriptos() {
        return carreraRepository.findCarrerasOrdenadasPorInscriptos()
                .stream()
                .map(resultado -> {
                    Carrera carrera = (Carrera) resultado[0];
                    long cantidad = (Long) resultado[1];

                    return new CarreraInscriptosResponseDTO(
                            carrera.getIdCarrera(),
                            carrera.getNombre(),
                            carrera.getDuracion(),
                            cantidad
                    );
                })
                .toList();
    }
}
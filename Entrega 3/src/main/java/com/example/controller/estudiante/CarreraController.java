package com.example.controller.estudiante;

import com.example.service.CarreraService;
import com.example.service.dto.Carrera.CarreraInscriptosResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/carreras")
public class CarreraController {

    private final CarreraService carreraService;

    public CarreraController(CarreraService carreraService) {
        this.carreraService = carreraService;
    }

    @GetMapping("/inscriptos")
    public ResponseEntity<List<CarreraInscriptosResponseDTO>> findCarrerasOrdenadasPorInscriptos() {
        return ResponseEntity.ok(
                carreraService.findCarrerasOrdenadasPorInscriptos()
        );
    }
}
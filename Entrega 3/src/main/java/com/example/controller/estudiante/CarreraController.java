package com.example.controller.estudiante;

import com.example.service.CarreraService;
import com.example.service.dto.Carrera.CarreraInscriptosResponseDTO;
import com.example.service.dto.Carrera.CarreraReporteResponseDTO;
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

    // 2.h) Reporte de inscriptos y egresados por anio, por carrera
    @GetMapping("/reporte")
    public ResponseEntity<List<CarreraReporteResponseDTO>> generarReporte() {
        return ResponseEntity.ok(carreraService.generarReporte());
    }
}

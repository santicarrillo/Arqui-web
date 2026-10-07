package com.example.controller.estudiante;

import com.example.service.EstudianteService;
import com.example.service.dto.Estudiante.Request.EstudianteRequestDTO;
import com.example.service.dto.Estudiante.response.EstudianteResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/estudiantes")
public class EstudianteController {
    private final EstudianteService estudianteService;

    public EstudianteController(EstudianteService estudianteService) {
        this.estudianteService = estudianteService;
    }
    public ResponseEntity<EstudianteResponseDTO> save(@RequestBody @Valid EstudianteRequestDTO request) {
        final var result = this.estudianteService.save(request);

        // Location: /api/estudiantes/{dni} -> dónde queda el recurso creado (principio REST)
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{dni}")
                .buildAndExpand(result.numeroDocumento())
                .toUri();

        return ResponseEntity.created(ubicacion).body(result);
    }
}

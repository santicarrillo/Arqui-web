package com.example.controller.estudiante;

import com.example.service.EstudianteService;
import com.example.service.dto.Estudiante.Request.EstudianteRequestDTO;
import com.example.service.dto.Estudiante.response.EstudianteResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import com.example.service.dto.Inscripcion.Request.InscripcionRequestDTO;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/estudiantes")
public class EstudianteController {
    private final EstudianteService estudianteService;

    public EstudianteController(EstudianteService estudianteService) {
        this.estudianteService = estudianteService;
    }
    @PostMapping("")
    public ResponseEntity<EstudianteResponseDTO> save(@RequestBody @Valid EstudianteRequestDTO request) {
        final var result = this.estudianteService.save(request);

        // Location: /api/estudiantes/{dni} -> dónde queda el recurso creado (principio REST)
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{dni}")
                .buildAndExpand(result.numeroDocumento())
                .toUri();

        return ResponseEntity.created(ubicacion).body(result);
    }

    // 2.b) matricular un estudiante en una carrera
    // POST /api/estudiantes/matricular
    @PostMapping("/matricular")
    public ResponseEntity<Void> matricularEstudiante(@RequestBody @Valid InscripcionRequestDTO request) {
        this.estudianteService.matricularEstudiante(request.numeroDocumento(), request.idCarrera());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // 2.c) recuperar todos los estudiantes con ordenamiento opcional
    // GET /api/estudiantes?criterio=apellido&orden=asc
    @GetMapping("")
    public ResponseEntity<List<EstudianteResponseDTO>> findAllOrdenados(
            @RequestParam(defaultValue = "apellido") String criterio,
            @RequestParam(defaultValue = "asc") String orden) {
        return ResponseEntity.ok(this.estudianteService.findAllOrdenados(criterio, orden));
    }

    //2d
    @GetMapping("/libreta/{libretaUniversitaria}")
    public ResponseEntity<EstudianteResponseDTO> findByLibretaUniversitaria(@PathVariable int libretaUniversitaria){
        return ResponseEntity.ok(this.estudianteService.findByLibretaUniversitaria(libretaUniversitaria));
    }
    //2e
    @GetMapping("/genero/{genero}")
    public ResponseEntity<List<EstudianteResponseDTO>> findByGenero(@PathVariable String genero){
        return ResponseEntity.ok(this.estudianteService.findByGenero(genero));
    }

    // 2.g
    @GetMapping("/carrera/{idCarrera}/ciudad/{ciudad}")
    public ResponseEntity<List<EstudianteResponseDTO>> findByCarreraAndCiudad(
            @PathVariable int idCarrera, @PathVariable String ciudad) {
        return ResponseEntity.ok(this.estudianteService.findByCarreraAndCiudad(idCarrera, ciudad));
    }
}

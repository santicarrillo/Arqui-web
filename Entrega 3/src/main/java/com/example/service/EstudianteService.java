package com.example.service;
import com.example.entity.Estudiante;
import com.example.repository.EstudianteRepository;
import com.example.service.dto.Estudiante.Request.EstudianteRequestDTO;
import com.example.service.dto.Estudiante.response.EstudianteResponseDTO;
import com.example.service.exception.EstudianteException;
import com.example.service.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
        * Lógica de negocio de los estudiantes.
        * El repository lo inyecta Spring por el constructor (inyección de dependencias):
        * ya no hace falta crear el EntityManagerFactory ni el DAO a mano como en la Entrega 2.
        *
        * Por defecto todos los métodos son de solo lectura; los que escriben llevan su propio @Transactional.
 */
@Service
@Transactional(readOnly = true)
public class EstudianteService {

    private final EstudianteRepository estudianteRepository;

    public EstudianteService(EstudianteRepository estudianteRepository) {
        this.estudianteRepository = estudianteRepository;
    }

    // 2.a) Dar de alta un estudiante
    @Transactional
    public EstudianteResponseDTO save(EstudianteRequestDTO request) {
        if (this.estudianteRepository.existsById(request.numeroDocumento())) {
            throw new EstudianteException(
                    "Ya existe un estudiante con DNI " + request.numeroDocumento());
        }
        if (this.estudianteRepository.existsByLibretaUniversitaria(request.libretaUniversitaria())) {
            throw new EstudianteException(
                    "Ya existe un estudiante con libreta universitaria " + request.libretaUniversitaria());
        }

        Estudiante estudiante = new Estudiante(
                request.libretaUniversitaria(),
                request.numeroDocumento(),
                request.nombre().trim(),
                request.apellido().trim(),
                request.edad(),
                request.genero().trim(),
                request.ciudadResidencia().trim());

        Estudiante result = this.estudianteRepository.save(estudiante);
        return new EstudianteResponseDTO(result);
    }

    // ---- Acá van los métodos de los incisos 2.b, 2.c, 2.d, 2.e y 2.g ----
    //2.d
    public EstudianteResponseDTO findByLibretaUniversitaria(int libretaUniversitaria){
        return estudianteRepository.findByLibretaUniversitaria(libretaUniversitaria)
                .map(estudiante -> new EstudianteResponseDTO(estudiante))
                .orElseThrow(() -> new NotFoundException("Estudiante", libretaUniversitaria));
    }
    //2.e
    public List<EstudianteResponseDTO> findByGenero(String genero){
        return this.estudianteRepository.findByGenero(genero).stream()
                .map(estudiante -> new EstudianteResponseDTO(estudiante)).toList();
    }
}

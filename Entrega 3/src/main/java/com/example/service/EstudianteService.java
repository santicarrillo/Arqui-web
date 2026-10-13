package com.example.service;
import com.example.entity.Estudiante;
import com.example.repository.EstudianteRepository;
import com.example.service.dto.Estudiante.Request.EstudianteRequestDTO;
import com.example.service.dto.Estudiante.response.EstudianteResponseDTO;
import com.example.service.exception.EstudianteException;
import com.example.service.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.entity.Inscripcion;
import com.example.repository.CarreraRepository;
import com.example.repository.InscripcionRepository;
import org.springframework.data.domain.Sort;

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
    private final CarreraRepository carreraRepository;
    private final InscripcionRepository inscripcionRepository;

    public EstudianteService(EstudianteRepository estudianteRepository,
                             CarreraRepository carreraRepository,
                             InscripcionRepository inscripcionRepository) {
        this.estudianteRepository = estudianteRepository;
        this.carreraRepository = carreraRepository;
        this.inscripcionRepository = inscripcionRepository;
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


// 2.b) Matricular un estudiante en una carrera

    @Transactional
    public void matricularEstudiante(int numeroDocumento, int idCarrera) {
        var estudiante = this.estudianteRepository.findById(numeroDocumento)
                .orElseThrow(() -> new NotFoundException("Estudiante", numeroDocumento));

        var carrera = this.carreraRepository.findById(idCarrera)
                .orElseThrow(() -> new NotFoundException("Carrera", idCarrera));

        if (this.inscripcionRepository.existsByEstudianteNumeroDocumentoAndCarreraIdCarrera(numeroDocumento, idCarrera)) {
            throw new EstudianteException("El estudiante ya se encuentra matriculado en esta carrera");
        }

        int anioActual = java.time.LocalDate.now().getYear();

        Inscripcion nuevaInscripcion = new Inscripcion(estudiante, carrera, anioActual, 0, 0);
        this.inscripcionRepository.save(nuevaInscripcion);
    }


    // 2.c) Recuperar todos los estudiantes ordenados
    public List<EstudianteResponseDTO> findAllOrdenados(String criterio, String orden) {
        Sort.Direction direction = "desc".equalsIgnoreCase(orden) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, criterio);

        return this.estudianteRepository.findAll(sort).stream()
                .map(EstudianteResponseDTO::new)
                .toList();
    }
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

    // 2.g
    public List<EstudianteResponseDTO> findByCarreraAndCiudad(
            int idCarrera, String ciudad) {

        return this.estudianteRepository.findByCarreraAndCiudad(idCarrera, ciudad).stream()
                .map(estudiante -> new EstudianteResponseDTO(estudiante)).toList();
    }

}

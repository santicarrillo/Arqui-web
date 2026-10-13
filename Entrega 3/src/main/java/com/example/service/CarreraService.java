package com.example.service;

import com.example.entity.Carrera;
import com.example.repository.CarreraRepository;
import com.example.service.dto.Carrera.CarreraInscriptosResponseDTO;
import com.example.service.dto.Carrera.CarreraReporteResponseDTO;
import com.example.service.dto.Carrera.ReporteAnioDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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

    // 2.h) Reporte por carrera de inscriptos y egresados por anio.
    // El agrupado lo hacen dos consultas JPQL; aca se combinan en una estructura
    // ordenada (carreras alfabeticas, anios cronologicos) y se arman los DTOs.
    public List<CarreraReporteResponseDTO> generarReporte() {
        // carrera -> (anio -> [inscriptos, egresados])
        Map<String, TreeMap<Integer, long[]>> datos = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (Object[] fila : carreraRepository.findInscriptosPorCarreraYAnio()) {
            String carrera = (String) fila[0];
            int anio = ((Number) fila[1]).intValue();
            long cantidad = ((Number) fila[2]).longValue();
            datos.computeIfAbsent(carrera, k -> new TreeMap<>())
                 .computeIfAbsent(anio, k -> new long[2])[0] += cantidad;
        }

        for (Object[] fila : carreraRepository.findEgresadosPorCarreraYAnio()) {
            String carrera = (String) fila[0];
            int anio = ((Number) fila[1]).intValue();
            long cantidad = ((Number) fila[2]).longValue();
            datos.computeIfAbsent(carrera, k -> new TreeMap<>())
                 .computeIfAbsent(anio, k -> new long[2])[1] += cantidad;
        }

        return datos.entrySet().stream()
                .map(carreraEntry -> {
                    List<ReporteAnioDTO> detalle = carreraEntry.getValue().entrySet().stream()
                            .map(anioEntry -> new ReporteAnioDTO(
                                    anioEntry.getKey(),
                                    anioEntry.getValue()[0],
                                    anioEntry.getValue()[1]))
                            .toList();
                    return new CarreraReporteResponseDTO(carreraEntry.getKey(), detalle);
                })
                .toList();
    }
}

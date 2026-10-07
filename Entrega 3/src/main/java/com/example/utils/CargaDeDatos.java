package com.example.utils;


import com.example.entity.Carrera;
import com.example.entity.Estudiante;
import com.example.entity.Inscripcion;
import com.example.repository.CarreraRepository;
import com.example.repository.EstudianteRepository;
import com.example.repository.InscripcionRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reemplaza al HelperMySQL de la Entrega 2: carga los CSV al arrancar la aplicación.
 * Lee los archivos desde el classpath (src/main/resources), así funciona sin importar
 * desde qué carpeta se ejecute el proyecto.
 */
@Component
public class CargaDeDatos implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CargaDeDatos.class);

    private final EstudianteRepository estudianteRepository;
    private final CarreraRepository carreraRepository;
    private final InscripcionRepository inscripcionRepository;

    public CargaDeDatos(EstudianteRepository estudianteRepository,
                        CarreraRepository carreraRepository,
                        InscripcionRepository inscripcionRepository) {
        this.estudianteRepository = estudianteRepository;
        this.carreraRepository = carreraRepository;
        this.inscripcionRepository = inscripcionRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws IOException {
        // Si la base ya tiene datos (por ej. con ddl-auto=update) no se vuelven a cargar
        if (estudianteRepository.count() > 0) {
            log.info("La base ya tiene datos, no se cargan los CSV");
            return;
        }

        List<Estudiante> estudiantes = new ArrayList<>();
        for (CSVRecord row : leerCsv("estudiantes.csv")) {
            estudiantes.add(new Estudiante(
                    Integer.parseInt(row.get("LU")),
                    Integer.parseInt(row.get("DNI")),
                    row.get("nombre"),
                    row.get("apellido"),
                    Integer.parseInt(row.get("edad")),
                    row.get("genero"),
                    row.get("ciudad")));
        }
        estudianteRepository.saveAll(estudiantes);

        List<Carrera> carreras = new ArrayList<>();
        for (CSVRecord row : leerCsv("carreras.csv")) {
            carreras.add(new Carrera(
                    Integer.parseInt(row.get("id_carrera")),
                    row.get("carrera"),
                    Integer.parseInt(row.get("duracion"))));
        }
        carreraRepository.saveAll(carreras);

        List<Inscripcion> inscripciones = new ArrayList<>();
        for (CSVRecord row : leerCsv("estudianteCarrera.csv")) {
            Estudiante estudiante = estudianteRepository.findById(Integer.parseInt(row.get("id_estudiante"))).orElse(null);
            Carrera carrera = carreraRepository.findById(Integer.parseInt(row.get("id_carrera"))).orElse(null);
            if (estudiante == null || carrera == null) {
                log.warn("Inscripción {} ignorada: no existe el estudiante o la carrera", row.get("id"));
                continue;
            }
            inscripciones.add(new Inscripcion(
                    estudiante,
                    carrera,
                    Integer.parseInt(row.get("inscripcion")),
                    Integer.parseInt(row.get("graduacion")),
                    Integer.parseInt(row.get("antiguedad"))));
        }
        inscripcionRepository.saveAll(inscripciones);

        log.info("Datos cargados: {} estudiantes, {} carreras, {} inscripciones",
                estudiantes.size(), carreras.size(), inscripciones.size());
    }

    private List<CSVRecord> leerCsv(String archivo) throws IOException {
        ClassPathResource recurso = new ClassPathResource(archivo);
        try (Reader reader = new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            return parser.getRecords();
        }
    }
}


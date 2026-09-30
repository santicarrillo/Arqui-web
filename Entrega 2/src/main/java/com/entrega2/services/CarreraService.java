package com.entrega2.services;

import com.entrega2.dao.CarreraDao;
import com.entrega2.entitys.Carrera;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CarreraService {

    private CarreraDao carreraDao;

    public CarreraService(CarreraDao carreraDao) {
        this.carreraDao = carreraDao;
    }

    // 2.f) Recuperar carreras con estudiantes inscriptos, ordenadas por cantidad de inscriptos
    public List<Carrera> getCarrerasOrdenadasPorInscriptos() {
        List<Carrera> carreras = carreraDao.getCarrerasOrdenadasPorInscriptos();
        if (!carreras.isEmpty()) {
            System.out.println("Se encontraron carreras con estudiantes inscriptos.");
            return carreras;
        } else {
            System.out.println("No se encontraron carreras con estudiantes inscriptos.");
            return null;
        }
    }

    // Inciso 3) Reporte por carrera de inscriptos y egresados por año
    public void imprimirReportePorCarrera() {
        Map<String, TreeMap<Integer, int[]>> reporte = new TreeMap<>();

        for (Object[] fila : carreraDao.getInscriptosPorCarreraYAnio()) {
            String carrera = (String) fila[0];
            int anio = ((Number) fila[1]).intValue();
            long cantidad = ((Number) fila[2]).longValue();
            reporte.computeIfAbsent(carrera, k -> new TreeMap<>())
                   .computeIfAbsent(anio, k -> new int[2])[0] += (int) cantidad;
        }

        for (Object[] fila : carreraDao.getEgresadosPorCarreraYAnio()) {
            String carrera = (String) fila[0];
            int anio = ((Number) fila[1]).intValue();
            long cantidad = ((Number) fila[2]).longValue();
            reporte.computeIfAbsent(carrera, k -> new TreeMap<>())
                   .computeIfAbsent(anio, k -> new int[2])[1] += (int) cantidad;
        }

        System.out.println("\n===== REPORTE POR CARRERA (inscriptos / egresados por año) =====");
        for (Map.Entry<String, TreeMap<Integer, int[]>> carreraEntry : reporte.entrySet()) {
            System.out.println("\nCarrera: " + carreraEntry.getKey());
            for (Map.Entry<Integer, int[]> anioEntry : carreraEntry.getValue().entrySet()) {
                int[] v = anioEntry.getValue();
                System.out.println("  Año " + anioEntry.getKey()
                        + " -> inscriptos: " + v[0] + " | egresados: " + v[1]);
            }
        }
    }
}

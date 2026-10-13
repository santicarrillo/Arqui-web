package com.example.service.dto.Carrera;

import java.util.List;

public record CarreraReporteResponseDTO(
        String carrera,
        List<ReporteAnioDTO> detallePorAnio
) {
}

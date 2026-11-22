package ventas.api;

import java.math.BigDecimal;
import java.util.List;

/** Respuestas de los reportes del organizador (F7). */
public final class ReporteDtos {

    private ReporteDtos() {
    }

    public record ReporteSectorView(Long sectorId, boolean numerado, int capacidad, long vendidas, long devueltas,
                                    long ingresados, double ocupacionPorcentaje, BigDecimal recaudacion) {
    }

    public record ReporteEventoView(Long eventoId, int capacidad, long vendidas, long devueltas, long ingresados,
                                    double ocupacionPorcentaje, BigDecimal recaudacion,
                                    List<ReporteSectorView> sectores) {
    }
}

package ventas.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ventas.Domains.ApiException;
import ventas.api.ReporteDtos.ReporteEventoView;
import ventas.api.ReporteDtos.ReporteSectorView;
import ventas.client.EventosClient;
import ventas.repository.ReporteRepository;
import ventas.repository.ReporteRepository.CapacidadSector;
import ventas.repository.ReporteRepository.VentasSector;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reportes del organizador (funcionalidad 12): entradas vendidas y devueltas por sector, ocupacion y
 * recaudacion. Un organizador solo ve los de sus eventos; un administrador ve todos.
 */
@Service
public class ReporteService {

    private final ReporteRepository repo;
    private final EventosClient eventos;

    public ReporteService(ReporteRepository repo, EventosClient eventos) {
        this.repo = repo;
        this.eventos = eventos;
    }

    public ReporteEventoView deEvento(long usuarioId, String rol, long eventoId) {
        String r = rol == null ? "" : rol.toUpperCase();
        if (!r.equals("ORGANIZADOR") && !r.equals("ADMINISTRADOR")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SOLO_ORGANIZADOR",
                    "Solo un organizador o un administrador puede ver reportes");
        }
        // El dueno del evento lo sabe eventos-service
        Long organizador = eventos.organizadorDe(eventoId).orElseThrow(() ->
                ApiException.notFound("EVENTO_NO_ENCONTRADO", "El evento " + eventoId + " no existe"));
        if (r.equals("ORGANIZADOR") && organizador.longValue() != usuarioId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NO_ES_TU_EVENTO",
                    "Solo podes ver los reportes de tus propios eventos");
        }

        Map<Long, VentasSector> ventas = repo.ventasPorSector(eventoId).stream()
                .collect(Collectors.toMap(VentasSector::sectorId, Function.identity()));
        Map<Long, CapacidadSector> capacidades = new TreeMap<>();
        repo.capacidades(eventoId).forEach(c -> capacidades.put(c.sectorId(), c));

        List<ReporteSectorView> sectores = new ArrayList<>();
        int capacidadTotal = 0;
        long vendidas = 0, devueltas = 0, ingresados = 0;
        BigDecimal recaudacion = BigDecimal.ZERO;
        for (CapacidadSector c : capacidades.values()) {
            VentasSector v = ventas.getOrDefault(c.sectorId(),
                    new VentasSector(c.sectorId(), 0, 0, 0, BigDecimal.ZERO));
            sectores.add(new ReporteSectorView(c.sectorId(), c.numerado(), c.capacidad(), v.vendidas(),
                    v.devueltas(), v.ingresados(), porcentaje(v.vendidas(), c.capacidad()), v.recaudacion()));
            capacidadTotal += c.capacidad();
            vendidas += v.vendidas();
            devueltas += v.devueltas();
            ingresados += v.ingresados();
            recaudacion = recaudacion.add(v.recaudacion());
        }
        return new ReporteEventoView(eventoId, capacidadTotal, vendidas, devueltas, ingresados,
                porcentaje(vendidas, capacidadTotal), recaudacion, sectores);
    }

    /** Porcentaje con un decimal (por ejemplo 33.3). */
    private static double porcentaje(long parte, int total) {
        return total == 0 ? 0.0 : Math.round(parte * 1000.0 / total) / 10.0;
    }
}

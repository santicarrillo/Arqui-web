package ventas.service;


import java.util.List;
import java.util.Map;
import java.util.Optional;

import ventas.Domains.ApiException;
import ventas.api.Dtos;
import ventas.client.ClientDtos;
import ventas.client.LugaresClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ventas.repository.VentasRepository;

/** Prepara y consulta la disponibilidad de lugares de cada evento. */
@Service
public class DisponibilidadService {

    private final VentasRepository repo;
    private final LugaresClient lugares;

    public DisponibilidadService(VentasRepository repo, LugaresClient lugares) {
        this.repo = repo;
        this.lugares = lugares;
    }

    /**
     * Se llama cuando eventos publica un evento: crea un registro por asiento en los sectores
     * numerados y un cupo en las zonas. Se puede repetir sin duplicar nada.
     */
    @Transactional
    public Map<String, Integer> inicializar(Dtos.InicializarRequest pedido) {
        int asientos = 0;
        int zonas = 0;
        for (Dtos.SectorInit sector : pedido.sectores()) {
            if (sector.numerado()) {
                for (ClientDtos.AsientoInfo asiento : lugares.asientos(sector.sectorId())) {
                    repo.insertarAsiento(pedido.eventoId(), sector.sectorId(), asiento.id());
                    asientos++;
                }
            } else {
                if (sector.capacidad() == null || sector.capacidad() < 1) {
                    throw ApiException.badRequest("CAPACIDAD_INVALIDA",
                            "El sector " + sector.sectorId() + " es una zona: hay que indicar su capacidad");
                }
                repo.crearCupo(pedido.eventoId(), sector.sectorId(), sector.capacidad());
                zonas++;
            }
        }
        return Map.of("asientosCreados", asientos, "zonasCreadas", zonas);
    }

    @Transactional(readOnly = true)
    public Dtos.DisponibilidadView consultar(long eventoId, long sectorId) {
        Map<String, Long> porEstado = repo.asientosPorEstado(eventoId, sectorId);
        if (!porEstado.isEmpty()) {
            int libres = porEstado.getOrDefault("LIBRE", 0L).intValue();
            int reservados = porEstado.getOrDefault("RESERVADO", 0L).intValue();
            int vendidos = porEstado.getOrDefault("VENDIDO", 0L).intValue();
            List<Long> asientosLibres = repo.asientosLibres(eventoId, sectorId);
            return new Dtos.DisponibilidadView(eventoId, sectorId, true, libres + reservados + vendidos,
                    libres, reservados + vendidos, asientosLibres);
        }
        Optional<VentasRepository.Cupo> cupo = repo.cupo(eventoId, sectorId);
        return cupo
                .map(c -> new Dtos.DisponibilidadView(eventoId, sectorId, false, c.capacidad(),
                        c.capacidad() - c.ocupados(), c.ocupados(), null))
                .orElseThrow(() -> ApiException.notFound("DISPONIBILIDAD_NO_ENCONTRADA",
                        "No hay disponibilidad para el sector " + sectorId + " del evento " + eventoId));
    }
}

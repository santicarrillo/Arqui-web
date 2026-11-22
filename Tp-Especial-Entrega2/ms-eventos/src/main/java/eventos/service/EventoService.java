package eventos.service;

import eventos.Domains.ApiException;
import eventos.Domains.Evento;
import eventos.Domains.EventoSector;
import eventos.Domains.Participante;
import eventos.api.Dtos;
import eventos.client.LugaresClient;
import eventos.client.LugaresClient.SectorLugar;
import eventos.client.VentasClient;
import eventos.client.VentasClient.SectorDisponibilidad;
import eventos.repository.EventoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Logica de eventos. No lleva @Transactional: con MongoDB cada cambio toca un solo documento
 * (el evento con sus sectores y participantes adentro) y esa operacion es atomica por si sola.
 *
 * Publicar y cancelar son sagas con ventas-service (F12): ver documentacion/06-entrega2-fase1.md.
 */
@Service
public class EventoService {

    private static final Logger log = LoggerFactory.getLogger(EventoService.class);

    private final EventoRepository repo;
    private final LugaresClient lugares;
    private final VentasClient ventas;

    public EventoService(EventoRepository repo, LugaresClient lugares, VentasClient ventas) {
        this.repo = repo;
        this.lugares = lugares;
        this.ventas = ventas;
    }

    public long crear(Dtos.CrearEventoRequest r) {
        if (r.nombre() == null || r.nombre().isBlank())
            throw ApiException.badRequest("DATOS_INVALIDOS", "El nombre es obligatorio");
        if (r.tipo() == null || r.fechaHora() == null || r.lugarId() == null || r.organizadorId() == null)
            throw ApiException.badRequest("DATOS_INVALIDOS",
                    "Faltan datos obligatorios (tipo, fechaHora, lugar, organizador)");
        return repo.crear(r);
    }

    public void configurarSectores(long eventoId, List<Dtos.SectorPrecioRequest> sectores) {
        Evento e = obtenerOErr(eventoId);
        if (!"BORRADOR".equals(e.estado()))
            throw ApiException.conflict("ESTADO_INVALIDO", "Solo se configuran precios en estado BORRADOR");
        for (Dtos.SectorPrecioRequest s : sectores) {
            if (s.sectorId() == null || s.precio() == null || s.precio().compareTo(BigDecimal.ZERO) < 0)
                throw ApiException.badRequest("DATOS_INVALIDOS",
                        "Cada sector necesita sectorId y un precio mayor o igual a cero");
        }
        for (Dtos.SectorPrecioRequest s : sectores) {
            repo.guardarSector(eventoId, s);
        }
    }

    public void agregarParticipantes(long eventoId, List<Dtos.ParticipanteRequest> participantes) {
        obtenerOErr(eventoId);
        if (participantes.isEmpty()) return;
        repo.agregarParticipantes(eventoId, participantes.stream()
                .map(p -> new Participante(p.nombre(), p.tipo()))
                .toList());
    }

    /**
     * Saga de publicacion:
     *  1. se valida cada sector contra lugares (que exista en el lugar y coincida si es numerado) y se toma
     *     la capacidad de las zonas;
     *  2. ventas prepara asientos y cupos;
     *  3. recien entonces el evento pasa a PUBLICADO.
     * Si ventas falla en el paso 2, el evento sigue en BORRADOR y el organizador puede reintentar: nunca queda
     * un evento publicado sin lugares para vender. Si fallara el paso 3, la disponibilidad creada no molesta
     * (ventas no vende si el evento no esta PUBLICADO) y el reintento no la duplica, asi que no hace falta
     * compensar.
     */
    public void publicar(long eventoId) {
        Evento e = obtenerOErr(eventoId);
        if (!"BORRADOR".equals(e.estado()))
            throw ApiException.conflict("ESTADO_INVALIDO", "Solo se publica un evento en BORRADOR");
        if (e.sectores().isEmpty())
            throw ApiException.conflict("SIN_SECTORES", "No se puede publicar sin al menos un sector con precio");

        Map<Long, SectorLugar> delLugar = lugares.sectores(e.lugarId()).stream()
                .collect(Collectors.toMap(SectorLugar::id, Function.identity(), (a, b) -> a));
        List<SectorDisponibilidad> aPreparar = new ArrayList<>();
        for (EventoSector s : e.sectores()) {
            SectorLugar sector = delLugar.get(s.sectorId());
            if (sector == null)
                throw ApiException.conflict("SECTOR_FUERA_DEL_LUGAR",
                        "El sector " + s.sectorId() + " no pertenece al lugar " + e.lugarId());
            if (sector.numerado() != s.numerado())
                throw ApiException.conflict("SECTOR_INCONSISTENTE", "El sector " + s.sectorId()
                        + (sector.numerado() ? " es numerado" : " no es numerado") + " en lugares");
            aPreparar.add(new SectorDisponibilidad(s.sectorId(), sector.numerado(),
                    sector.numerado() ? null : sector.capacidad()));
        }

        ventas.prepararDisponibilidad(eventoId, aPreparar);

        if (!repo.publicar(eventoId))
            throw ApiException.conflict("ESTADO_INVALIDO", "El evento ya no esta en BORRADOR");
    }

    /**
     * Saga de cancelacion:
     *  1. el evento pasa a CANCELADO: desde ese momento ventas no acepta compras nuevas;
     *  2. se le avisa a ventas, que vence las compras pendientes y devuelve las entradas pagadas.
     * Si ventas no responde, la cancelacion queda pendiente y CancelacionesPendientesJob la reintenta
     * hasta que ventas la procese (consistencia eventual).
     */
    public Dtos.CancelacionView cancelar(long eventoId, String motivo) {
        Evento e = obtenerOErr(eventoId);
        if ("CANCELADO".equals(e.estado()) || !repo.cancelar(eventoId, motivo))
            throw ApiException.conflict("ESTADO_INVALIDO", "El evento ya está cancelado");
        boolean avisado = avisarCancelacionAVentas(eventoId);
        return new Dtos.CancelacionView(eventoId, "CANCELADO", avisado ? "PROCESADAS" : "PENDIENTES");
    }

    /** Devuelve false si ventas no respondio: el aviso queda pendiente para el reintento. */
    public boolean avisarCancelacionAVentas(long eventoId) {
        try {
            ventas.avisarCancelacion(eventoId);
            repo.marcarCancelacionNotificada(eventoId);
            return true;
        } catch (RuntimeException ex) {
            log.warn("No se pudo avisar a ventas la cancelacion del evento {}: {}. Se reintenta mas tarde.",
                    eventoId, ex.getMessage());
            return false;
        }
    }

    /** Lo llama la tarea programada. Devuelve cuantos avisos se pudieron entregar. */
    public int reintentarCancelacionesPendientes() {
        int entregados = 0;
        for (Long id : repo.cancelacionesPendientes()) {
            if (avisarCancelacionAVentas(id)) entregados++;
        }
        return entregados;
    }

    // busqueda por criterios -> lista resumida
    public List<Dtos.EventoResumenView> buscar(String tipo, Long organizadorId, String participante,
                                               String ciudad, LocalDate desde, LocalDate hasta, String orden) {
        return repo.buscar(tipo, organizadorId, participante, ciudad, desde, hasta, orden).stream()
                .map(e -> new Dtos.EventoResumenView(
                        e.id(), e.nombre(), e.tipo(), e.fechaHora(), e.ciudad(), e.estado()))
                .toList();
    }

    // >>> Lo que consume ventas: arma el EventoInfoView para GET /eventos/{id} <<<
    // Los sectores vienen dentro del documento: una sola lectura a la base.
    public Dtos.EventoInfoView obtenerInfo(long eventoId) {
        Evento e = obtenerOErr(eventoId);
        List<Dtos.SectorEventoView> sectores = e.sectores().stream()
                .map(s -> new Dtos.SectorEventoView(s.sectorId(), s.numerado(), s.precio()))
                .toList();
        return new Dtos.EventoInfoView(e.id(), e.estado(), e.fechaHora(), e.limitePorCliente(), sectores,
                e.organizadorId());
    }

    private Evento obtenerOErr(long eventoId) {
        return repo.buscarPorId(eventoId)
                .orElseThrow(() -> ApiException.notFound("EVENTO_NO_ENCONTRADO",
                        "El evento " + eventoId + " no existe"));
    }
}

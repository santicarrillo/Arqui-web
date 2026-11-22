package eventos.service;

import eventos.Domains.ApiException;
import eventos.Domains.Evento;
import eventos.Domains.Participante;
import eventos.api.Dtos;
import eventos.repository.EventoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Logica de eventos. Ya no lleva @Transactional: con MongoDB cada cambio toca un solo documento
 * (el evento con sus sectores y participantes adentro) y esa operacion es atomica por si sola.
 */
@Service
public class EventoService {

    private final EventoRepository repo;

    public EventoService(EventoRepository repo) {
        this.repo = repo;
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

    public void publicar(long eventoId) {
        Evento e = obtenerOErr(eventoId);
        if (!"BORRADOR".equals(e.estado()))
            throw ApiException.conflict("ESTADO_INVALIDO", "Solo se publica un evento en BORRADOR");
        if (e.sectores().isEmpty())
            throw ApiException.conflict("SIN_SECTORES", "No se puede publicar sin al menos un sector con precio");
        repo.cambiarEstado(eventoId, "PUBLICADO");
    }

    public void cancelar(long eventoId, String motivo) {
        Evento e = obtenerOErr(eventoId);
        if ("CANCELADO".equals(e.estado()))
            throw ApiException.conflict("ESTADO_INVALIDO", "El evento ya está cancelado");
        repo.cancelar(eventoId, motivo);
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
        return new Dtos.EventoInfoView(e.id(), e.estado(), e.fechaHora(), e.limitePorCliente(), sectores);
    }

    private Evento obtenerOErr(long eventoId) {
        return repo.buscarPorId(eventoId)
                .orElseThrow(() -> ApiException.notFound("EVENTO_NO_ENCONTRADO",
                        "El evento " + eventoId + " no existe"));
    }
}

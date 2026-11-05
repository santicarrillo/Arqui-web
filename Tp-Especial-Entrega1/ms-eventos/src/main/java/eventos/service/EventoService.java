package eventos.service;

import eventos.Domains.ApiException;
import eventos.Domains.Evento;
import eventos.api.Dtos;
import eventos.repository.EventoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class EventoService {

    private final EventoRepository repo;

    public EventoService(EventoRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public long crear(Dtos.CrearEventoRequest r) {
        if (r.nombre() == null || r.nombre().isBlank())
            throw ApiException.badRequest("DATOS_INVALIDOS", "El nombre es obligatorio");
        if (r.tipo() == null || r.fechaHora() == null || r.lugarId() == null || r.organizadorId() == null)
            throw ApiException.badRequest("DATOS_INVALIDOS",
                    "Faltan datos obligatorios (tipo, fechaHora, lugar, organizador)");
        return repo.crear(r);
    }

    @Transactional
    public void configurarSectores(long eventoId, List<Dtos.SectorPrecioRequest> sectores) {
        Evento e = obtenerOErr(eventoId);
        if (!"BORRADOR".equals(e.estado()))
            throw ApiException.conflict("ESTADO_INVALIDO", "Solo se configuran precios en estado BORRADOR");
        for (Dtos.SectorPrecioRequest s : sectores) {
            repo.guardarSector(eventoId, s);
        }
    }

    @Transactional
    public void agregarParticipantes(long eventoId, List<Dtos.ParticipanteRequest> participantes) {
        obtenerOErr(eventoId);
        for (Dtos.ParticipanteRequest p : participantes) {
            long id = repo.crearParticipante(p.nombre(), p.tipo());
            repo.vincularParticipante(eventoId, id);
        }
    }

    @Transactional
    public void publicar(long eventoId) {
        Evento e = obtenerOErr(eventoId);
        if (!"BORRADOR".equals(e.estado()))
            throw ApiException.conflict("ESTADO_INVALIDO", "Solo se publica un evento en BORRADOR");
        if (!repo.tieneSectores(eventoId))
            throw ApiException.conflict("SIN_SECTORES", "No se puede publicar sin al menos un sector con precio");
        repo.cambiarEstado(eventoId, "PUBLICADO");
    }

    @Transactional
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
    public Dtos.EventoInfoView obtenerInfo(long eventoId) {
        Evento e = obtenerOErr(eventoId);
        List<Dtos.SectorEventoView> sectores = repo.sectoresDe(eventoId).stream()
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

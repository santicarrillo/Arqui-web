package eventos.repository;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import eventos.Domains.Evento;
import eventos.Domains.EventoSector;
import eventos.Domains.Participante;
import eventos.api.Dtos;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Acceso a la coleccion "eventos" de MongoDB.
 * Cada metodo que modifica un evento es una sola operacion sobre un solo documento, y en MongoDB
 * eso es atomico: no hacen falta transacciones.
 */
@Repository
public class EventoRepository {

    private static final String CONTADORES = "contadores";

    private final MongoTemplate mongo;

    public EventoRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    // ---- evento ----

    public long crear(Dtos.CrearEventoRequest r) {
        long id = siguienteId("eventos");
        mongo.insert(new Evento(id, r.nombre(), r.descripcion(), r.tipo(), r.fechaHora(), "BORRADOR",
                r.lugarId(), r.ciudad(), r.organizadorId(),
                r.limitePorCliente() == null ? 6 : r.limitePorCliente(),
                null, List.of(), List.of()));
        return id;
    }

    public Optional<Evento> buscarPorId(long id) {
        return Optional.ofNullable(mongo.findById(id, Evento.class));
    }

    public void cambiarEstado(long id, String estado) {
        mongo.updateFirst(porId(id), new Update().set("estado", estado), Evento.class);
    }

    public void cancelar(long id, String motivo) {
        mongo.updateFirst(porId(id),
                new Update().set("estado", "CANCELADO").set("motivoCancelacion", motivo),
                Evento.class);
    }

    // ---- busqueda por multiples criterios (los filtros que vienen se suman al query) ----

    public List<Evento> buscar(String tipo, Long organizadorId, String participante,
                               String ciudad, LocalDate desde, LocalDate hasta, String orden) {
        Criteria c = Criteria.where("estado").is("PUBLICADO");

        if (tieneTexto(tipo))       c.and("tipo").is(tipo);
        if (organizadorId != null)  c.and("organizadorId").is(organizadorId);
        if (tieneTexto(ciudad))     c.and("ciudad").is(ciudad);
        if (desde != null || hasta != null) {
            Criteria fecha = c.and("fechaHora");
            if (desde != null) fecha.gte(desde.atStartOfDay());
            if (hasta != null) fecha.lt(hasta.plusDays(1).atStartOfDay());
        }
        if (tieneTexto(participante)) {
            // busca dentro del arreglo embebido: "contiene", sin importar mayusculas
            c.and("participantes.nombre")
             .regex(Pattern.compile(Pattern.quote(participante), Pattern.CASE_INSENSITIVE));
        }

        Query query = Query.query(c);
        if ("precio".equalsIgnoreCase(orden)) {
            // El precio mas bajo sale de los sectores embebidos. Para el volumen del TP se ordena aca;
            // con muchos eventos se haria en la base con una agregacion ($min sobre sectores.precio).
            Comparator<BigDecimal> menorPrimero = Comparator.nullsLast(Comparator.naturalOrder());
            return mongo.find(query, Evento.class).stream()
                    .sorted(Comparator.comparing(EventoRepository::precioMinimo, menorPrimero)
                            .thenComparing(Evento::fechaHora))
                    .toList();
        }
        // fecha (default) y relevancia
        query.with(Sort.by(Sort.Direction.ASC, "fechaHora"));
        return mongo.find(query, Evento.class);
    }

    // ---- precios por sector (arreglo "sectores" del documento) ----

    public void guardarSector(long eventoId, Dtos.SectorPrecioRequest s) {
        // 1) si el sector ya esta en el arreglo, se actualiza en su lugar ($ = el elemento que coincidio)
        UpdateResult actualizado = mongo.updateFirst(
                Query.query(Criteria.where("_id").is(eventoId).and("sectores.sectorId").is(s.sectorId())),
                new Update().set("sectores.$.numerado", s.numerado())
                            .set("sectores.$.precio", new Decimal128(s.precio())),
                Evento.class);

        // 2) si no estaba, se agrega. La condicion "ne" evita duplicarlo si dos pedidos llegan a la vez.
        if (actualizado.getMatchedCount() == 0) {
            mongo.updateFirst(
                    Query.query(Criteria.where("_id").is(eventoId).and("sectores.sectorId").ne(s.sectorId())),
                    new Update().push("sectores", new EventoSector(s.sectorId(), s.numerado(), s.precio())),
                    Evento.class);
        }
    }

    // ---- participantes (arreglo "participantes" del documento) ----

    public void agregarParticipantes(long eventoId, List<Participante> participantes) {
        mongo.updateFirst(porId(eventoId),
                new Update().push("participantes").each(participantes.toArray()),
                Evento.class);
    }

    // ---- auxiliares ----

    /**
     * MongoDB genera ids de tipo ObjectId, pero el resto del sistema usa ids numericos.
     * Se lleva un contador por coleccion: el incremento es atomico, asi que dos eventos creados
     * a la vez nunca reciben el mismo id.
     */
    private long siguienteId(String coleccion) {
        Document contador = mongo.getCollection(CONTADORES).findOneAndUpdate(
                Filters.eq("_id", coleccion),
                Updates.inc("valor", 1L),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
        return contador.get("valor", Number.class).longValue();
    }

    private static Query porId(long id) {
        return Query.query(Criteria.where("_id").is(id));
    }

    private static BigDecimal precioMinimo(Evento e) {
        return e.sectores().stream()
                .map(EventoSector::precio)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private static boolean tieneTexto(String s) {
        return s != null && !s.isBlank();
    }
}

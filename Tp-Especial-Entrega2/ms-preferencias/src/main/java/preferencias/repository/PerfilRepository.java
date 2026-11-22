package preferencias.repository;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import preferencias.Domains.Perfil;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Acceso a la coleccion "perfiles" con MongoTemplate (consultas explicitas, como JdbcClient en ventas).
 * Las escrituras son upserts parciales: crean el perfil si no existe y solo tocan los campos indicados.
 */
@Repository
public class PerfilRepository {

    private static final String COLECCION = "perfiles";

    private final MongoTemplate mongo;

    public PerfilRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    private static Query porId(long clienteId) {
        return Query.query(Criteria.where("_id").is(clienteId));
    }

    public Optional<Perfil> buscar(long clienteId) {
        return Optional.ofNullable(mongo.findById(clienteId, Perfil.class));
    }

    /** $set de lo que declara el cliente. No toca eventosComprados. */
    public void guardarPreferencias(long clienteId, List<String> tipos, List<String> artistas,
                                    List<String> artistasClave, String ciudad, LocalDateTime ahora) {
        Update cambios = new Update()
                .set("tiposEvento", tipos)
                .set("artistasFavoritos", artistas)
                .set("artistasClave", artistasClave)
                .set("ciudad", ciudad)
                .set("actualizadoEn", ahora);
        mongo.upsert(porId(clienteId), cambios, Perfil.class);
    }

    /** $addToSet: si ventas repite el aviso de la misma compra, el evento no queda dos veces. */
    public void registrarCompra(long clienteId, long eventoId, LocalDateTime ahora) {
        Update cambios = new Update()
                .addToSet("eventosComprados", eventoId)
                .set("actualizadoEn", ahora);
        mongo.upsert(porId(clienteId), cambios, Perfil.class);
    }

    /** Clientes con alguno de estos artistas (ya normalizados) entre sus favoritos. Usa el indice de artistasClave. */
    public List<Long> interesados(Collection<String> artistasClave) {
        Query query = Query.query(Criteria.where("artistasClave").in(artistasClave)).with(Sort.by("_id"));
        query.fields().include("_id");
        return mongo.find(query, Perfil.class).stream().map(Perfil::clienteId).toList();
    }

    /**
     * Filtrado colaborativo: entre los clientes que compraron alguno de mis eventos, que OTROS eventos
     * compraron, y cuantos de ellos compraron cada uno. Se resuelve entero en MongoDB:
     *
     *   $match   otros clientes que compraron alguno de mis eventos
     *   $unwind  un documento por cada evento que compraron
     *   $match   descartar los eventos que yo ya compre
     *   $group   por evento, contando clientes
     *   $sort    los mas compartidos primero
     *   $limit
     *
     * Devuelve eventoId -> cantidad de clientes parecidos que lo compraron, en orden.
     */
    public Map<Long, Long> compradosPorClientesParecidos(long clienteId, List<Long> misEventos, int limite) {
        Aggregation agregacion = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("_id").ne(clienteId).and("eventosComprados").in(misEventos)),
                Aggregation.unwind("eventosComprados"),
                Aggregation.match(Criteria.where("eventosComprados").nin(misEventos)),
                Aggregation.group("eventosComprados").count().as("clientes"),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "clientes").and(Sort.by(Sort.Direction.ASC, "_id"))),
                Aggregation.limit(limite));
        return conteo(mongo.aggregate(agregacion, COLECCION, Document.class).getMappedResults());
    }

    /** Los eventos comprados por mas clientes (para recomendarle algo a un cliente sin datos). */
    public Map<Long, Long> masComprados(int limite) {
        Aggregation agregacion = Aggregation.newAggregation(
                Aggregation.unwind("eventosComprados"),
                Aggregation.group("eventosComprados").count().as("clientes"),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "clientes").and(Sort.by(Sort.Direction.ASC, "_id"))),
                Aggregation.limit(limite));
        return conteo(mongo.aggregate(agregacion, COLECCION, Document.class).getMappedResults());
    }

    private static Map<Long, Long> conteo(List<Document> filas) {
        Map<Long, Long> resultado = new LinkedHashMap<>();
        for (Document fila : filas) {
            resultado.put(((Number) fila.get("_id")).longValue(), ((Number) fila.get("clientes")).longValue());
        }
        return resultado;
    }
}

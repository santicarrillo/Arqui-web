package notificaciones.repository;

import notificaciones.Domains.Notificacion;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class NotificacionRepository {

    private final MongoTemplate mongo;

    public NotificacionRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Inserta la notificacion. Devuelve false si ya existia una con ese _id (aviso repetido). */
    public boolean insertarSiNoExiste(Notificacion notificacion) {
        try {
            mongo.insert(notificacion);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public List<Notificacion> deCliente(long clienteId, boolean soloNoLeidas, int limite) {
        Criteria criterio = Criteria.where("clienteId").is(clienteId);
        if (soloNoLeidas) {
            criterio.and("leida").is(false);
        }
        Query query = Query.query(criterio).with(Sort.by(Sort.Direction.DESC, "creadaEn")).limit(limite);
        return mongo.find(query, Notificacion.class);
    }

    /** Solo si es de ese cliente: un usuario no puede marcar las de otro. */
    public boolean marcarLeida(String id, long clienteId) {
        Query query = Query.query(Criteria.where("_id").is(id).and("clienteId").is(clienteId));
        return mongo.updateFirst(query, new Update().set("leida", true), Notificacion.class).getMatchedCount() == 1;
    }
}

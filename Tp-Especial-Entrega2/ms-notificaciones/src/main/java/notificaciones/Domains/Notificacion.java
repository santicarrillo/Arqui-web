package notificaciones.Domains;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Una notificacion para un cliente: un documento de la coleccion "notificaciones".
 *
 * El _id no lo genera MongoDB: se arma con lo que se notifica y a quien, por ejemplo "compra-15-cliente-5"
 * o "publicado-7-cliente-8". Asi, si un servicio repite el mismo aviso (por un reintento), el segundo insert
 * choca con el _id existente y no se crea otra notificacion ni se manda otro email.
 *
 * El indice (clienteId, creadaEn) es el de GET /notificaciones: las de un cliente, de la mas nueva a la mas vieja.
 */
@Document(collection = "notificaciones")
@CompoundIndex(name = "cliente_fecha", def = "{'clienteId': 1, 'creadaEn': -1}")
public record Notificacion(
        @Id String id,
        Long clienteId,
        String tipo,          // COMPRA_CONFIRMADA, EVENTO_CANCELADO, EVENTO_PUBLICADO
        Long eventoId,
        String titulo,
        String mensaje,
        String canal,         // EMAIL (simulado: se escribe en el log)
        LocalDateTime creadaEn,
        Boolean leida
) {
}

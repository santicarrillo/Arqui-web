package eventos.Domains;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Un evento es un documento de la coleccion "eventos" de MongoDB.
 * Los precios por sector y los participantes van embebidos: siempre se leen junto con el evento,
 * asi que GET /eventos/{id} se resuelve con una sola lectura, y cada cambio sobre un evento
 * (precios, participantes, estado) es atomico porque toca un solo documento.
 *
 * El id sigue siendo numerico (Long) porque ventas y los demas servicios guardan eventoId como numero.
 */
@Document(collection = "eventos")
@CompoundIndex(name = "estado_fecha", def = "{'estado': 1, 'fechaHora': 1}")
public record Evento(
        @Id Long id,
        String nombre,
        String descripcion,
        @Indexed String tipo,
        LocalDateTime fechaHora,
        String estado,
        Long lugarId,
        @Indexed String ciudad,
        @Indexed Long organizadorId,
        Integer limitePorCliente,
        String motivoCancelacion,
        List<EventoSector> sectores,
        List<Participante> participantes,
        // F12: false mientras ventas no confirmo que proceso la cancelacion (devoluciones); se reintenta
        Boolean cancelacionNotificada
) {
    public Evento {
        // un documento sin estos arreglos se lee como listas vacias, nunca null
        sectores = sectores == null ? List.of() : List.copyOf(sectores);
        participantes = participantes == null ? List.of() : List.copyOf(participantes);
    }
}

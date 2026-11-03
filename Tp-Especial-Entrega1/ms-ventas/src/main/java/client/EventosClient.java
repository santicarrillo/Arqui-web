package client;

import java.util.Optional;

public interface EventosClient {
    /** Devuelve el evento con su estado, fecha, limite por cliente y precio de cada sector. */
    Optional<ClientDtos.EventoInfo> obtener(long eventoId);
}

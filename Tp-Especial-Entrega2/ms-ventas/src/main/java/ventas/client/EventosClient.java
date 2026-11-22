package ventas.client;

import java.util.Optional;

public interface EventosClient {
    /** Devuelve el evento con su estado, fecha, limite por cliente y precio de cada sector. */
    Optional<ClientDtos.EventoInfo> obtener(long eventoId);

    /** F7: el organizador dueno del evento, para que cada organizador vea solo sus reportes. */
    Optional<Long> organizadorDe(long eventoId);
}

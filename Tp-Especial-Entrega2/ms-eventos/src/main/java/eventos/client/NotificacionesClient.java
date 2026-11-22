package eventos.client;

import java.time.LocalDateTime;
import java.util.List;

/** F8: notificaciones-service avisa a los clientes que siguen a alguno de los artistas del evento. */
public interface NotificacionesClient {

    void eventoPublicado(long eventoId, String nombre, String ciudad, LocalDateTime fechaHora, List<String> artistas);
}

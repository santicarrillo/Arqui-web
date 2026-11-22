package notificaciones.client;

import java.util.List;

/** preferencias-service sabe que clientes tienen a cada artista de favorito. */
public interface PreferenciasClient {

    List<Long> interesados(List<String> artistas);
}

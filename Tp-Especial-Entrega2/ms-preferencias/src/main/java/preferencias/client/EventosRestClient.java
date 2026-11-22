package preferencias.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import preferencias.Domains.ApiException;

import java.time.LocalDate;
import java.util.List;

/** GET /eventos de eventos-service, con timeouts para no quedar colgados si eventos no responde. */
@Component
public class EventosRestClient implements EventosClient {

    private static final ParameterizedTypeReference<List<EventoResumen>> LISTA = new ParameterizedTypeReference<>() {};

    private final RestClient http;

    public EventosRestClient(RestClient.Builder builder, @Value("${servicios.eventos.url}") String url) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(1000);
        fabrica.setReadTimeout(3000);
        this.http = builder.baseUrl(url).requestFactory(fabrica).build();
    }

    @Override
    public List<EventoResumen> publicadosDesde(LocalDate desde) {
        try {
            List<EventoResumen> eventos = http.get()
                    .uri(u -> u.path("/eventos").queryParam("desde", desde).build())
                    .retrieve().body(LISTA);
            return eventos == null ? List.of() : eventos;
        } catch (RestClientException e) {
            throw caido(e);
        }
    }

    @Override
    public List<EventoResumen> publicadosConParticipante(String participante, LocalDate desde) {
        try {
            List<EventoResumen> eventos = http.get()
                    .uri(u -> u.path("/eventos").queryParam("participante", participante)
                            .queryParam("desde", desde).build())
                    .retrieve().body(LISTA);
            return eventos == null ? List.of() : eventos;
        } catch (RestClientException e) {
            throw caido(e);
        }
    }

    private static ApiException caido(RestClientException e) {
        return ApiException.unavailable("EVENTOS_NO_DISPONIBLE",
                "No se pudo consultar el catalogo de eventos: " + e.getMessage());
    }
}

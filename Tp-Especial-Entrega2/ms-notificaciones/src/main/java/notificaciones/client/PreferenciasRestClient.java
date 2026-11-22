package notificaciones.client;

import notificaciones.Domains.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;

/** GET /interno/interesados de preferencias-service, con timeouts. */
@Component
public class PreferenciasRestClient implements PreferenciasClient {

    private final RestClient http;

    public PreferenciasRestClient(RestClient.Builder builder, @Value("${servicios.preferencias.url}") String url) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(1000);
        fabrica.setReadTimeout(3000);
        this.http = builder.baseUrl(url).requestFactory(fabrica).build();
    }

    @Override
    public List<Long> interesados(List<String> artistas) {
        try {
            Long[] clientes = http.get()
                    .uri(u -> u.path("/interno/interesados").queryParam("artistas", artistas.toArray()).build())
                    .retrieve().body(Long[].class);
            return clientes == null ? List.of() : Arrays.asList(clientes);
        } catch (RestClientException e) {
            throw ApiException.unavailable("PREFERENCIAS_NO_DISPONIBLE",
                    "No se pudo consultar preferencias-service: " + e.getMessage());
        }
    }
}

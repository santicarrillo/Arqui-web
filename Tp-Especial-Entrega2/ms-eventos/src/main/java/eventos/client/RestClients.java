package eventos.client;

import eventos.Domains.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/** Implementaciones REST de los clientes, con timeouts cortos para no colgar al organizador. */
public final class RestClients {

    private RestClients() {
    }

    private static SimpleClientHttpRequestFactory conTimeouts() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(1000);
        fabrica.setReadTimeout(5000);
        return fabrica;
    }

    @Component
    public static class LugaresRestClient implements LugaresClient {

        private final RestClient http;

        public LugaresRestClient(RestClient.Builder builder, @Value("${servicios.lugares.url}") String url) {
            this.http = builder.baseUrl(url).requestFactory(conTimeouts()).build();
        }

        @Override
        public List<SectorLugar> sectores(long lugarId) {
            try {
                SectorLugar[] sectores = http.get().uri("/lugares/{id}/sectores", lugarId)
                        .retrieve().body(SectorLugar[].class);
                return sectores == null ? List.of() : List.of(sectores);
            } catch (HttpClientErrorException.NotFound e) {
                throw ApiException.conflict("LUGAR_NO_ENCONTRADO", "El lugar " + lugarId + " no existe en lugares-service");
            } catch (RestClientException e) {
                throw ApiException.unavailable("LUGARES_NO_DISPONIBLE",
                        "No se pudo consultar lugares-service: " + e.getMessage());
            }
        }
    }

    @Component
    public static class VentasRestClient implements VentasClient {

        private final RestClient http;

        public VentasRestClient(RestClient.Builder builder, @Value("${servicios.ventas.url}") String url) {
            this.http = builder.baseUrl(url).requestFactory(conTimeouts()).build();
        }

        @Override
        public void prepararDisponibilidad(long eventoId, List<SectorDisponibilidad> sectores) {
            try {
                http.post().uri("/interno/disponibilidad")
                        .body(Map.of("eventoId", eventoId, "sectores", sectores))
                        .retrieve().toBodilessEntity();
            } catch (HttpClientErrorException e) {
                // 4xx: ventas rechazo el pedido (por ejemplo un sector que lugares no conoce)
                throw ApiException.conflict("DISPONIBILIDAD_RECHAZADA",
                        "ventas-service rechazo la disponibilidad: " + e.getResponseBodyAsString());
            } catch (RestClientException e) {
                throw ApiException.unavailable("VENTAS_NO_DISPONIBLE",
                        "No se pudo preparar la disponibilidad en ventas-service: " + e.getMessage());
            }
        }

        @Override
        public void avisarCancelacion(long eventoId) {
            try {
                http.post().uri("/interno/eventos/{id}/cancelacion", eventoId).retrieve().toBodilessEntity();
            } catch (RestClientException e) {
                throw ApiException.unavailable("VENTAS_NO_DISPONIBLE",
                        "No se pudo avisar la cancelacion a ventas-service: " + e.getMessage());
            }
        }
    }
}

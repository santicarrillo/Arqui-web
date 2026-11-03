package client;

import Domains.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Implementaciones REST de los clientes. Estan detras de interfaces para poder
 * simularlas en las pruebas y para cambiarlas (por ejemplo por Feign) sin tocar el negocio.
 */
public final class RestClients {

    private RestClients() {
    }

    private static ApiException caido(String servicio, RestClientException e) {
        return ApiException.unavailable("SERVICIO_NO_DISPONIBLE",
                "No se pudo comunicar con " + servicio + ": " + e.getMessage());
    }

    @Component
    public static class EventosRestClient implements EventosClient {

        private final RestClient http;

        public EventosRestClient(RestClient.Builder builder, @Value("${servicios.eventos.url}") String url) {
            this.http = builder.baseUrl(url).build();
        }

        @Override
        public Optional<ClientDtos.EventoInfo> obtener(long eventoId) {
            try {
                return Optional.ofNullable(http.get().uri("/eventos/{id}", eventoId)
                        .retrieve().body(ClientDtos.EventoInfo.class));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            } catch (RestClientException e) {
                throw caido("eventos-service", e);
            }
        }
    }

    @Component
    public static class LugaresRestClient implements LugaresClient {

        private final RestClient http;

        public LugaresRestClient(RestClient.Builder builder, @Value("${servicios.lugares.url}") String url) {
            this.http = builder.baseUrl(url).build();
        }

        @Override
        public List<ClientDtos.AsientoInfo> asientos(long sectorId) throws ApiException {
            try {
                ClientDtos.AsientoInfo[] asientos = http.get().uri("/sectores/{id}/asientos", sectorId)
                        .retrieve().body(ClientDtos.AsientoInfo[].class);
                return asientos == null ? List.of() : Arrays.asList(asientos);
            } catch (HttpClientErrorException.NotFound e) {
                throw ApiException.notFound("SECTOR_NO_ENCONTRADO", "El sector " + sectorId + " no existe en lugares");
            } catch (RestClientException e) {
                throw caido("lugares-service", e);
            }
        }
    }

    @Component
    public static abstract class PagosRestClient implements PagosClient {

        private static final int PAGO_RECHAZADO = 402;

        private final RestClient http;

        public PagosRestClient(RestClient.Builder builder, @Value("${servicios.pagos.url}") String url) {
            this.http = builder.baseUrl(url).build();
        }

        @Override
        public Optional<ClientDtos.PagoInfo> obtenerPorCompra(long compraId) throws ApiException {
            try {
                return Optional.ofNullable(http.get().uri("/pagos?compraId={id}", compraId)
                        .retrieve().body(ClientDtos.PagoInfo.class));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            } catch (RestClientException e) {
                throw caido("pagos-service", e);
            }
        }

        @Override
        public ClientDtos.PagoInfo cobrar(ClientDtos.CobroRequest cobro) throws ApiException {
            try {
                return http.post().uri("/pagos").body(cobro).retrieve().body(ClientDtos.PagoInfo.class);
            } catch (HttpClientErrorException e) {
                // pagos responde 402 cuando el pago es rechazado: no es un error de comunicacion
                if (e.getStatusCode().value() == PAGO_RECHAZADO) {
                    ClientDtos.PagoInfo pago = e.getResponseBodyAs(ClientDtos.PagoInfo.class);
                    return pago != null ? pago : new ClientDtos.PagoInfo(null, cobro.compraId(), "RECHAZADO");
                }
                throw caido("pagos-service", e);
            } catch (RestClientException e) {
                throw caido("pagos-service", e);
            }
        }
    }
}
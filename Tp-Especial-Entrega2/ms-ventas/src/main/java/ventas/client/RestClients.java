package ventas.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import ventas.Domains.ApiException;
import ventas.Domains.ServicioNoDisponibleException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Implementaciones REST de los clientes. Estan detras de interfaces para poder
 * simularlas en las pruebas y para cambiarlas (por ejemplo por Feign) sin tocar el negocio.
 *
 * Todas tienen timeouts (F11): si otro servicio se cuelga, ventas no se queda esperando para siempre.
 */
public final class RestClients {

    private RestClients() {
    }

    static ServicioNoDisponibleException caido(String servicio, RestClientException e) {
        return new ServicioNoDisponibleException("SERVICIO_NO_DISPONIBLE",
                "No se pudo comunicar con " + servicio + ": " + e.getMessage());
    }

    static SimpleClientHttpRequestFactory conTimeouts(int leerMs) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(1000);
        fabrica.setReadTimeout(leerMs);
        return fabrica;
    }

    @Component
    public static class EventosRestClient implements EventosClient {

        private final RestClient http;

        public EventosRestClient(RestClient.Builder builder, @Value("${servicios.eventos.url}") String url) {
            this.http = builder.baseUrl(url).requestFactory(conTimeouts(3000)).build();
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

        @Override
        public Optional<Long> organizadorDe(long eventoId) {
            try {
                ClientDtos.EventoDueno evento = http.get().uri("/eventos/{id}", eventoId)
                        .retrieve().body(ClientDtos.EventoDueno.class);
                return Optional.ofNullable(evento).map(ClientDtos.EventoDueno::organizadorId);
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
            this.http = builder.baseUrl(url).requestFactory(conTimeouts(5000)).build();
        }

        @Override
        public List<ClientDtos.AsientoInfo> asientos(long sectorId) {
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

    /**
     * Cliente de pagos con resiliencia (F11). Cada llamada pasa por:
     *  - timeout de conexion (1 s) y de lectura (3 s);
     *  - circuit breaker "pagos": si fallan la mitad de las ultimas llamadas, se abre y durante 20 s no se
     *    llama mas a pagos (se responde enseguida en lugar de esperar timeouts);
     *  - reintento "pagos": hasta 3 intentos, solo ante ServicioNoDisponibleException. Va por fuera del
     *    circuito, asi cada intento cuenta como una llamada.
     * Reintentar el cobro es seguro: pagos es idempotente por compra (un segundo POST devuelve el mismo pago).
     * La configuracion esta en application.yml (resilience4j.*).
     */
    @Component
    public static class PagosRestClient implements PagosClient {

        private static final int PAGO_RECHAZADO = 402;

        private final RestClient http;
        private final Retry reintento;
        private final CircuitBreaker circuito;

        public PagosRestClient(RestClient.Builder builder, @Value("${servicios.pagos.url}") String url,
                               RetryRegistry reintentos, CircuitBreakerRegistry circuitos) {
            this.http = builder.baseUrl(url).requestFactory(conTimeouts(3000)).build();
            this.reintento = reintentos.retry("pagos");
            this.circuito = circuitos.circuitBreaker("pagos");
        }

        private <T> T protegido(Supplier<T> llamada) {
            Supplier<T> conCircuito = CircuitBreaker.decorateSupplier(circuito, llamada);
            Supplier<T> conReintentos = Retry.decorateSupplier(reintento, conCircuito);
            try {
                return conReintentos.get();
            } catch (CallNotPermittedException e) {
                // Fallback: el circuito esta abierto. No se llama a pagos y la compra sigue reservada.
                throw new ServicioNoDisponibleException("PAGOS_NO_DISPONIBLE",
                        "El servicio de pagos no esta disponible. Tu reserva sigue en pie: "
                                + "proba pagar de nuevo en unos segundos");
            }
        }

        @Override
        public Optional<ClientDtos.PagoInfo> obtenerPorCompra(long compraId) {
            return protegido(() -> {
                try {
                    return Optional.ofNullable(http.get().uri("/pagos?compraId={id}", compraId)
                            .retrieve().body(ClientDtos.PagoInfo.class));
                } catch (HttpClientErrorException.NotFound e) {
                    return Optional.empty();
                } catch (RestClientException e) {
                    throw caido("pagos-service", e);
                }
            });
        }

        @Override
        public ClientDtos.PagoInfo cobrar(ClientDtos.CobroRequest cobro) {
            return protegido(() -> {
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
            });
        }

        @Override
        public void reintegrar(long pagoId, ClientDtos.ReintegroRequest reintegro) {
            protegido(() -> {
                try {
                    http.post().uri("/pagos/{id}/reintegro", pagoId).body(reintegro).retrieve().toBodilessEntity();
                    return null;
                } catch (RestClientException e) {
                    throw caido("pagos-service", e);
                }
            });
        }
    }
}

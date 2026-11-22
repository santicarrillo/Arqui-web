package client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import ventas.Domains.ServicioNoDisponibleException;
import ventas.client.ClientDtos.CobroRequest;
import ventas.client.ClientDtos.PagoInfo;
import ventas.client.PagosClient;
import ventas.client.RestClients;

/**
 * Resiliencia del cliente de pagos (F11), sin Spring ni Docker: se levanta un pagos-service falso con el
 * servidor HTTP que trae Java y se le hace fallar a proposito.
 */
class PagosResilienciaTest {

    private static final CobroRequest COBRO = new CobroRequest(1L, 5L, new BigDecimal("1000"));

    private HttpServer pagosFalso;
    private final AtomicInteger llamadas = new AtomicInteger();
    /** Cuantas respuestas 500 da antes de contestar bien. */
    private volatile int fallasRestantes;
    /** Lo que contesta cuando no falla: 200 (aprobado) o 402 (rechazado). */
    private volatile int codigoFinal = 200;

    private RetryRegistry reintentos;
    private CircuitBreakerRegistry circuitos;

    @BeforeEach
    void levantarPagosFalso() throws IOException {
        pagosFalso = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        pagosFalso.createContext("/pagos", intercambio -> {
            llamadas.incrementAndGet();
            int codigo;
            String cuerpo;
            if (fallasRestantes > 0) {
                fallasRestantes--;
                codigo = 500;
                cuerpo = "{\"error\":\"caido\"}";
            } else {
                codigo = codigoFinal;
                cuerpo = "{\"id\":7,\"compraId\":1,\"estado\":\"" + (codigo == 200 ? "APROBADO" : "RECHAZADO") + "\"}";
            }
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(codigo, bytes.length);
            try (OutputStream out = intercambio.getResponseBody()) {
                out.write(bytes);
            }
        });
        pagosFalso.start();

        // misma idea que application.yml, con esperas cortas para que el test sea rapido
        reintentos = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .retryExceptions(ServicioNoDisponibleException.class)
                .build());
        circuitos = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .recordExceptions(ServicioNoDisponibleException.class)
                .build());
    }

    @AfterEach
    void bajarPagosFalso() {
        pagosFalso.stop(0);
    }

    private PagosClient cliente() {
        return new RestClients.PagosRestClient(RestClient.builder(),
                "http://localhost:" + pagosFalso.getAddress().getPort(), reintentos, circuitos);
    }

    @Test
    void siPagosFallaUnaVezElReintentoCobraIgual() {
        fallasRestantes = 1;

        PagoInfo pago = cliente().cobrar(COBRO);

        assertEquals("APROBADO", pago.estado());
        assertEquals(2, llamadas.get(), "un intento fallido y uno bueno");
    }

    @Test
    void siPagosSigueCaidoSeRindeAlTercerIntento() {
        fallasRestantes = 100;

        assertThrows(ServicioNoDisponibleException.class, () -> cliente().cobrar(COBRO));

        assertEquals(3, llamadas.get());
    }

    @Test
    void unPagoRechazadoNoSeReintenta() {
        codigoFinal = 402;

        PagoInfo pago = cliente().cobrar(COBRO);

        assertEquals("RECHAZADO", pago.estado());
        assertEquals(1, llamadas.get(), "un rechazo es una respuesta valida, no una falla");
    }

    @Test
    void conMuchasFallasSeAbreElCircuitoYYaNoSeLlamaAPagos() {
        fallasRestantes = 100;
        PagosClient pagos = cliente();

        // primer cobro: 3 intentos fallidos. Segundo: la cuarta falla abre el circuito (ventana de 4, 50 %)
        assertThrows(ServicioNoDisponibleException.class, () -> pagos.cobrar(COBRO));
        assertThrows(ServicioNoDisponibleException.class, () -> pagos.cobrar(COBRO));
        assertEquals(CircuitBreaker.State.OPEN, circuitos.circuitBreaker("pagos").getState());
        int antes = llamadas.get();

        ServicioNoDisponibleException e = assertThrows(ServicioNoDisponibleException.class, () -> pagos.cobrar(COBRO));

        assertEquals("PAGOS_NO_DISPONIBLE", e.getError());
        assertEquals(antes, llamadas.get(), "con el circuito abierto se responde sin llamar a pagos");
    }
}

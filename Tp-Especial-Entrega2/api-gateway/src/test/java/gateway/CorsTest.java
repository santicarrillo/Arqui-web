package gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * CORS para la GUI: el navegador solo deja que la web (otro origen) llame al gateway si el gateway lo
 * autoriza con los encabezados Access-Control-*. Ninguna de estas pruebas llega a un microservicio.
 */
@SpringBootTest(classes = GatewayApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class CorsTest {

    private static final String WEB = "http://localhost:5173";

    @Autowired
    private WebTestClient web;

    @Test
    void elPreflightDeLaWebSeContestaSinToken() {
        web.options().uri("/compras")
                .header("Origin", WEB)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization, content-type, idempotency-key")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", WEB);
    }

    @Test
    void unRechazoPorFaltaDeTokenTambienLlevaLosEncabezados() {
        // si no los llevara, el navegador esconderia el 401 y la web no sabria que tiene que pedir login
        web.get().uri("/eventos")
                .header("Origin", WEB)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", WEB);
    }

    @Test
    void unOrigenNoAutorizadoSeRechaza() {
        web.options().uri("/eventos")
                .header("Origin", "http://sitio-malicioso.com")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectStatus().isForbidden();
    }
}

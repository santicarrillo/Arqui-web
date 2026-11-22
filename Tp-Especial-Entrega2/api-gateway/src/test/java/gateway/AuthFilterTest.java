package gateway;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pruebas de seguridad del API Gateway (F13):
 * - rutas publicas pasan sin token y limpian headers X-Usuario-Id / X-Rol falsificados;
 * - rutas protegidas rechazan peticiones sin JWT o con JWT invalido/vencido (401) sin llegar al micro;
 * - tokens validos propagan X-Usuario-Id y X-Rol confiables desde la firma, pisando lo que mande el cliente;
 * - rutas /interno/** no estan expuestas por el gateway (404).
 */
@SpringBootTest(classes = GatewayApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AuthFilterTest {

    private static final String SECRET = "clave-super-secreta-ticketya-2026-cambiar-en-produccion-min-32-bytes";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private static final SecretKey OTRA_KEY = Keys.hmacShaKeyFor(
            "otra-clave-distinta-que-no-deberia-validar-en-el-gateway-123456".getBytes(StandardCharsets.UTF_8));

    private static HttpServer downstream;
    private static String downstreamUrl;
    private static final AtomicInteger llamadasDownstream = new AtomicInteger();
    private static final AtomicReference<String> ultimoUsuarioHeader = new AtomicReference<>();
    private static final AtomicReference<String> ultimoRolHeader = new AtomicReference<>();
    private static final AtomicReference<String> ultimaRutaDownstream = new AtomicReference<>();

    @Autowired
    private WebTestClient web;

    @BeforeAll
    static void iniciarDownstream() throws IOException {
        downstream = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        downstream.createContext("/", exchange -> {
            llamadasDownstream.incrementAndGet();
            ultimaRutaDownstream.set(exchange.getRequestURI().getPath());
            ultimoUsuarioHeader.set(exchange.getRequestHeaders().getFirst("X-Usuario-Id"));
            ultimoRolHeader.set(exchange.getRequestHeaders().getFirst("X-Rol"));

            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        downstream.start();
        downstreamUrl = "http://localhost:" + downstream.getAddress().getPort();
    }

    @AfterAll
    static void apagarDownstream() {
        if (downstream != null) {
            downstream.stop(0);
        }
    }

    @DynamicPropertySource
    static void configurarRutas(DynamicPropertyRegistry r) {
        r.add("jwt.secret", () -> SECRET);
        r.add("USUARIOS_URL", () -> downstreamUrl);
        r.add("EVENTOS_URL", () -> downstreamUrl);
        r.add("VENTAS_URL", () -> downstreamUrl);
        r.add("LUGARES_URL", () -> downstreamUrl);
        r.add("PREFERENCIAS_URL", () -> downstreamUrl);
        r.add("NOTIFICACIONES_URL", () -> downstreamUrl);
    }

    @BeforeEach
    void limpiarContadores() {
        llamadasDownstream.set(0);
        ultimoUsuarioHeader.set(null);
        ultimoRolHeader.set(null);
        ultimaRutaDownstream.set(null);
    }

    private static String token(long usuarioId, String rol, SecretKey firma, long offsetMs) {
        Date ahora = new Date();
        return Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .claim("rol", rol)
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + offsetMs))
                .signWith(firma)
                .compact();
    }

    @Test
    void loginEsPublicoYLimpiaHeadersFalsificados() {
        web.post().uri("/usuarios/login")
                .header("X-Usuario-Id", "999")
                .header("X-Rol", "ADMINISTRADOR")
                .exchange()
                .expectStatus().isOk();

        assertEquals(1, llamadasDownstream.get());
        assertEquals("/usuarios/login", ultimaRutaDownstream.get());
        assertNull(ultimoUsuarioHeader.get(), "No debe reenviar X-Usuario-Id en ruta publica");
        assertNull(ultimoRolHeader.get(), "No debe reenviar X-Rol en ruta publica");
    }

    @Test
    void registroEsPublicoYLimpiaHeadersFalsificados() {
        web.post().uri("/usuarios")
                .header("X-Usuario-Id", "1")
                .header("X-Rol", "ADMINISTRADOR")
                .exchange()
                .expectStatus().isOk();

        assertEquals(1, llamadasDownstream.get());
        assertNull(ultimoUsuarioHeader.get());
        assertNull(ultimoRolHeader.get());
    }

    @Test
    void optionsPreflightEsPublicoSinToken() {
        web.options().uri("/compras")
                .exchange()
                .expectStatus().isOk();

        assertEquals(1, llamadasDownstream.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/usuarios",
            "/usuarios/1",
            "/eventos",
            "/lugares",
            "/sectores/1/asientos",
            "/compras",
            "/entradas",
            "/disponibilidad",
            "/devoluciones",
            "/ingresos",
            "/reportes/eventos/1",
            "/filas/1",
            "/preferencias",
            "/recomendaciones",
            "/notificaciones"
    })
    void rutasProtegidasSinTokenDevuelven401YNoLleganAlMicro(String ruta) {
        web.get().uri(ruta)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("X-Auth-Error", "FALTA_TOKEN");

        assertEquals(0, llamadasDownstream.get());
    }

    @Test
    void authorizationSinPrefijoBearerDevuelve401() {
        web.get().uri("/eventos")
                .header("Authorization", "Basic dXNlcjpwYXNz")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("X-Auth-Error", "FALTA_TOKEN");

        assertEquals(0, llamadasDownstream.get());
    }

    @Test
    void tokenFirmadoConOtraClaveDevuelve401() {
        String falso = token(5L, "ADMINISTRADOR", OTRA_KEY, 60_000);

        web.get().uri("/devoluciones")
                .header("Authorization", "Bearer " + falso)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("X-Auth-Error", "TOKEN_INVALIDO");

        assertEquals(0, llamadasDownstream.get());
    }

    @Test
    void tokenMalformadoDevuelve401() {
        web.get().uri("/compras/1")
                .header("Authorization", "Bearer esto.no.es.un.jwt")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("X-Auth-Error", "TOKEN_INVALIDO");

        assertEquals(0, llamadasDownstream.get());
    }

    @Test
    void tokenVencidoDevuelve401() {
        String vencido = token(5L, "CLIENTE", KEY, -10_000);

        web.get().uri("/entradas")
                .header("Authorization", "Bearer " + vencido)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("X-Auth-Error", "TOKEN_INVALIDO");

        assertEquals(0, llamadasDownstream.get());
    }

    @ParameterizedTest
    @CsvSource({
            "10, CLIENTE, /compras",
            "20, ORGANIZADOR, /eventos",
            "30, ADMINISTRADOR, /devoluciones",
            "40, ACCESO, /ingresos",
            "50, CLIENTE, /preferencias",
            "60, CLIENTE, /notificaciones"
    })
    void tokenValidoPropagaIdentidadYSobrescribeHeadersFalsificados(long id, String rol, String ruta) {
        String jwt = token(id, rol, KEY, 60_000);

        web.get().uri(ruta)
                .header("Authorization", "Bearer " + jwt)
                .header("X-Usuario-Id", "9999")
                .header("X-Rol", "ADMINISTRADOR")
                .exchange()
                .expectStatus().isOk();

        assertEquals(1, llamadasDownstream.get());
        assertEquals(ruta, ultimaRutaDownstream.get());
        assertEquals(String.valueOf(id), ultimoUsuarioHeader.get());
        assertEquals(rol, ultimoRolHeader.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/interno/disponibilidad",
            "/interno/eventos/1/cancelacion",
            "/interno/compras",
            "/interno/interesados",
            "/interno/avisos/compra-confirmada"
    })
    void rutasInternasNoEstanExpuestasEnElGateway(String ruta) {
        String adminJwt = token(1L, "ADMINISTRADOR", KEY, 60_000);

        web.post().uri(ruta)
                .header("Authorization", "Bearer " + adminJwt)
                .exchange()
                .expectStatus().isNotFound();

        assertEquals(0, llamadasDownstream.get());
    }
}

package gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Entrega 2 - Autenticacion centralizada + correlation-id.
 * El gateway es la FRONTERA de seguridad: valida el JWT una sola vez y propaga la
 * identidad (X-Usuario-Id / X-Rol) y un identificador de correlacion (X-Correlation-Id)
 * a los microservicios, para poder seguir una misma operacion a traves de todos ellos.
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    private static final String H_USUARIO = "X-Usuario-Id";
    private static final String H_ROL = "X-Rol";
    private static final String H_CORRELATION = "X-Correlation-Id";

    private final SecretKey key;

    public AuthFilter(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String metodo = request.getMethod().name();
        String ruta = request.getURI().getRawPath();

        // Correlation-id: reusa el que venga o genera uno nuevo. Viaja a los micros y vuelve al cliente.
        String entrante = request.getHeaders().getFirst(H_CORRELATION);
        final String corr = (entrante == null || entrante.isBlank())
                ? UUID.randomUUID().toString().substring(0, 8) : entrante;
        exchange.getResponse().getHeaders().set(H_CORRELATION, corr);

        String auth = request.getHeaders().getFirst("Authorization");
        boolean conToken = auth != null && auth.startsWith("Bearer ");

        // Rutas publicas: preflight CORS, login y alta de usuario (registro inicial).
        // F1: el alta con token es la de un administrador creando otros roles, asi que ese token se valida
        // y se propaga la identidad como en cualquier ruta protegida (ms-usuarios decide segun X-Rol).
        boolean altaConToken = esAltaDeUsuario(metodo, ruta) && conToken;
        if (esPublica(metodo, ruta) && !altaConToken) {
            ServerHttpRequest publica = request.mutate()
                    .headers(h -> { h.remove(H_USUARIO); h.remove(H_ROL); h.set(H_CORRELATION, corr); })
                    .build();
            return chain.filter(exchange.mutate().request(publica).build());
        }

        if (!conToken) {
            return rechazar(exchange, corr, metodo, ruta, "FALTA_TOKEN");
        }

        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(auth.substring(7)).getPayload();
            String usuarioId = claims.getSubject();
            String rol = claims.get("rol", String.class);

            // Borra lo que mande el cliente y setea SOLO lo que viene del token firmado, mas el correlation-id.
            ServerHttpRequest mutada = request.mutate()
                    .headers(h -> {
                        h.remove(H_USUARIO);
                        h.remove(H_ROL);
                        h.set(H_USUARIO, usuarioId);
                        h.set(H_ROL, rol);
                        h.set(H_CORRELATION, corr);
                    })
                    .build();
            return chain.filter(exchange.mutate().request(mutada).build());
        } catch (JwtException | IllegalArgumentException e) {
            return rechazar(exchange, corr, metodo, ruta, "TOKEN_INVALIDO");
        }
    }

    private boolean esPublica(String metodo, String ruta) {
        if ("OPTIONS".equals(metodo)) return true;                       // preflight CORS
        if ("POST".equals(metodo) && ruta.equals("/usuarios/login")) return true;
        if (esAltaDeUsuario(metodo, ruta)) return true;                  // registro
        return false;
    }

    private boolean esAltaDeUsuario(String metodo, String ruta) {
        return "POST".equals(metodo) && ruta.equals("/usuarios");
    }

    private Mono<Void> rechazar(ServerWebExchange exchange, String corr, String metodo, String ruta, String motivo) {
        // Los rechazos no llegan al LoggingFilter, asi que se registran aca con el mismo correlation-id.
        log.warn("[{}] {} {} -> 401 {}", corr, metodo, ruta, motivo);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("X-Auth-Error", motivo);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1; // corre antes del ruteo/forward
    }
}

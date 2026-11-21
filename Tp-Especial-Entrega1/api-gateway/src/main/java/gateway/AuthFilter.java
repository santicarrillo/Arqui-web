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

/**
 * Entrega 2 - Autenticacion centralizada.
 * El gateway es la FRONTERA de seguridad: valida el JWT una sola vez y propaga la
 * identidad a los microservicios con los headers X-Usuario-Id / X-Rol (que ya consumen).
 * Asi no hay que tocar cada micro. Los micros solo deben ser accesibles por el gateway.
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    private static final String H_USUARIO = "X-Usuario-Id";
    private static final String H_ROL = "X-Rol";

    private final SecretKey key;

    public AuthFilter(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String metodo = request.getMethod().name();
        String ruta = request.getURI().getRawPath();

        // Rutas publicas: preflight CORS, login y alta de usuario (registro inicial).
        if (esPublica(metodo, ruta)) {
            // Aun en rutas publicas limpiamos los headers de identidad: nadie los setea desde afuera.
            return chain.filter(exchange.mutate().request(sinHeadersDeIdentidad(request)).build());
        }

        String auth = request.getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return rechazar(exchange, "FALTA_TOKEN");
        }

        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(auth.substring(7)).getPayload();
            String usuarioId = claims.getSubject();
            String rol = claims.get("rol", String.class);

            // Borra lo que mande el cliente y setea SOLO lo que viene del token firmado.
            ServerHttpRequest mutada = request.mutate()
                    .headers(h -> {
                        h.remove(H_USUARIO);
                        h.remove(H_ROL);
                        h.set(H_USUARIO, usuarioId);
                        h.set(H_ROL, rol);
                    })
                    .build();
            return chain.filter(exchange.mutate().request(mutada).build());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("[AUTH] token invalido en {} {}: {}", metodo, ruta, e.getMessage());
            return rechazar(exchange, "TOKEN_INVALIDO");
        }
    }

    private boolean esPublica(String metodo, String ruta) {
        if ("OPTIONS".equals(metodo)) return true;                       // preflight CORS
        if ("POST".equals(metodo) && ruta.equals("/usuarios/login")) return true;
        if ("POST".equals(metodo) && ruta.equals("/usuarios")) return true; // registro
        return false;
    }

    private ServerHttpRequest sinHeadersDeIdentidad(ServerHttpRequest request) {
        return request.mutate()
                .headers(h -> { h.remove(H_USUARIO); h.remove(H_ROL); })
                .build();
    }

    private Mono<Void> rechazar(ServerWebExchange exchange, String motivo) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("X-Auth-Error", motivo);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1; // corre antes del ruteo/forward
    }
}

package gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Bitacora centralizada: registra cada peticion que entra y sale del gateway con su
 * correlation-id, el usuario (del token), el endpoint, el codigo de respuesta y el tiempo.
 * Corre despues del AuthFilter, asi ya tiene disponibles X-Correlation-Id y X-Usuario-Id.
 */
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long inicio = System.currentTimeMillis();
        var request = exchange.getRequest();
        String metodo = request.getMethod().name();
        String ruta = request.getURI().getRawPath();
        String corrHeader = request.getHeaders().getFirst("X-Correlation-Id");
        String usuarioHeader = request.getHeaders().getFirst("X-Usuario-Id");
        final String corr = corrHeader != null ? corrHeader : "-";
        final String usuario = usuarioHeader != null ? usuarioHeader : "anonimo";

        log.info("--> [{}] IN  {} {} (usuario={})", corr, metodo, ruta, usuario);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            var response = exchange.getResponse();
            long duracion = System.currentTimeMillis() - inicio;
            int status = response.getStatusCode() != null ? response.getStatusCode().value() : 500;
            log.info("<-- [{}] OUT {} {} -> {} ({} ms) usuario={}", corr, metodo, ruta, status, duracion, usuario);
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

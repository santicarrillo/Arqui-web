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
 * Filtro global que registra en consola cada petición que entra y sale del API Gateway.
 * Cumple con el requisito de logging centralizado de llamadas.
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

        log.info("--> [GATEWAY IN] {} {}", metodo, ruta);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            var response = exchange.getResponse();
            long duracion = System.currentTimeMillis() - inicio;
            int status = response.getStatusCode() != null ? response.getStatusCode().value() : 500;
            log.info("<-- [GATEWAY OUT] {} {} - Status: {} ({} ms)", metodo, ruta, status, duracion);
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

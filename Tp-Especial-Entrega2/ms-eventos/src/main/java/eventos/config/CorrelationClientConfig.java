package eventos.config;

import org.slf4j.MDC;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reenvia el X-Correlation-Id (tomado del MDC del hilo que atiende la request) en TODAS las
 * llamadas salientes de este micro a otros. Se aplica a cualquier RestClient construido desde
 * el RestClient.Builder inyectado, asi la misma operacion se sigue con el mismo id de extremo a extremo.
 */
@Configuration
public class CorrelationClientConfig {

    @Bean
    public RestClientCustomizer correlationRestClientCustomizer() {
        return builder -> builder.requestInterceptor((request, body, execution) -> {
            String corr = MDC.get("correlationId");
            if (corr != null && !corr.isBlank()) {
                request.getHeaders().set("X-Correlation-Id", corr);
            }
            return execution.execute(request, body);
        });
    }
}

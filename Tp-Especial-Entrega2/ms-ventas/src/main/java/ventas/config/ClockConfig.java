package ventas.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Reloj inyectable, para poder fijar la hora en las pruebas. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
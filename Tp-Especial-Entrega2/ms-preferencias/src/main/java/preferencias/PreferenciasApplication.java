package preferencias;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.util.TimeZone;

@SpringBootApplication
public class PreferenciasApplication {

    public static void main(String[] args) {
        // Misma zona que eventos: MongoDB guarda las fechas en UTC y Spring las convierte con la zona de la JVM
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
        SpringApplication.run(PreferenciasApplication.class, args);
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}

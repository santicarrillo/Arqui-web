package eventos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class EventosApplication {
    public static void main(String[] args) {
        // Postgres 16 no reconoce el alias viejo "America/Buenos_Aires" que reporta
        // algunas JVM de Windows: forzamos el nombre canonico antes de conectar.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
        SpringApplication.run(EventosApplication.class, args);
    }
}

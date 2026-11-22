package ventas;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VentasApplication {

    public static void main(String[] args) {
        // Postgres 16 rechaza el alias viejo "America/Buenos_Aires": forzamos el nombre canonico.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
        SpringApplication.run(VentasApplication.class, args);
    }
}
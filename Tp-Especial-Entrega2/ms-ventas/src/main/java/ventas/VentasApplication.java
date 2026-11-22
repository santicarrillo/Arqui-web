package ventas;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync   // F9: los avisos a otros servicios despues de una compra no hacen esperar al cliente
public class VentasApplication {

    public static void main(String[] args) {
        // Postgres 16 rechaza el alias viejo "America/Buenos_Aires": forzamos el nombre canonico.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
        SpringApplication.run(VentasApplication.class, args);
    }
}
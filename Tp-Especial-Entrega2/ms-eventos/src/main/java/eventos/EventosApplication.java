package eventos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class EventosApplication {
    public static void main(String[] args) {
        // Zona horaria fija: MongoDB guarda las fechas en UTC y Spring convierte LocalDateTime
        // con la zona de la JVM. Fijarla hace que una fecha se lea igual que como se guardo
        // en cualquier maquina (y evita el alias viejo "America/Buenos_Aires" de algunas JVM de Windows).
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
        SpringApplication.run(EventosApplication.class, args);
    }
}

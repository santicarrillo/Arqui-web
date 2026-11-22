package lugares;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LugaresApp {
    public static void main(String[] args) {
        // Postgres 16 rechaza el alias viejo "America/Buenos_Aires": forzamos el nombre canonico.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
        SpringApplication.run(LugaresApp.class,args);
    }
}
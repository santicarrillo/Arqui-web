package eventos.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cache en memoria (F5) con Caffeine. Dos caches, cada una con su tiempo de vida:
 *  - DETALLE: GET /eventos/{id}, lo que ventas consulta en cada compra (30 s);
 *  - BUSQUEDAS: GET /eventos con filtros, el catalogo (60 s).
 * Ademas del tiempo de vida, EventoService las borra en cada cambio de un evento.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String DETALLE = "eventoDetalle";
    public static final String BUSQUEDAS = "eventoBusquedas";

    @Bean
    public CacheManager cacheManager(@Value("${eventos.cache.detalle-segundos:30}") long detalleSegundos,
                                     @Value("${eventos.cache.busqueda-segundos:60}") long busquedaSegundos) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache(DETALLE, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(detalleSegundos))
                .maximumSize(10_000)
                .recordStats()
                .build());
        manager.registerCustomCache(BUSQUEDAS, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(busquedaSegundos))
                .maximumSize(1_000)
                .recordStats()
                .build());
        return manager;
    }
}

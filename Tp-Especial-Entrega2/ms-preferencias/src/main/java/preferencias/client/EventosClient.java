package preferencias.client;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que preferencias necesita de eventos-service: el catalogo de eventos publicados (GET /eventos).
 * Esa busqueda ya devuelve solo eventos PUBLICADOS y en eventos esta cacheada (F5).
 * Esta detras de una interfaz para simularla en los tests.
 */
public interface EventosClient {

    record EventoResumen(Long id, String nombre, String tipo, LocalDateTime fechaHora, String ciudad, String estado) {}

    /** Eventos publicados desde esa fecha. */
    List<EventoResumen> publicadosDesde(LocalDate desde);

    /** Eventos publicados desde esa fecha donde participa alguien cuyo nombre contiene ese texto. */
    List<EventoResumen> publicadosConParticipante(String participante, LocalDate desde);
}

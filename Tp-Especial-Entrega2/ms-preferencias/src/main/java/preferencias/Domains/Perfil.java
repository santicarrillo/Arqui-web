package preferencias.Domains;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * El perfil de un cliente: un documento por cliente en la coleccion "perfiles", con el id del cliente
 * como _id (asi hay a lo sumo uno por cliente y se busca por clave primaria).
 *
 * Tiene dos partes que escriben actores distintos:
 *  - lo que el cliente declara (tipos de evento, artistas favoritos, ciudad), con PUT /preferencias;
 *  - lo que compro (eventosComprados), que lo agrega ventas cada vez que se confirma una compra.
 * Cada parte se actualiza con operaciones parciales ($set y $addToSet), nunca reemplazando el documento
 * entero: asi una compra que llega mientras el cliente edita sus preferencias no se pierde.
 *
 * artistasClave es la version normalizada de artistasFavoritos (minusculas, sin tildes ni espacios de
 * mas): con ella "León Gieco" y "leon gieco" son el mismo artista. Esta indexada porque notificaciones
 * busca por artista a quien avisarle cuando se publica un evento.
 */
@Document(collection = "perfiles")
public record Perfil(
        @Id Long clienteId,
        List<String> tiposEvento,
        List<String> artistasFavoritos,
        @Indexed List<String> artistasClave,
        String ciudad,
        @Indexed List<Long> eventosComprados,
        LocalDateTime actualizadoEn
) {
    public Perfil {
        // un documento sin alguno de estos arreglos se lee como lista vacia, nunca null
        tiposEvento = tiposEvento == null ? List.of() : List.copyOf(tiposEvento);
        artistasFavoritos = artistasFavoritos == null ? List.of() : List.copyOf(artistasFavoritos);
        artistasClave = artistasClave == null ? List.of() : List.copyOf(artistasClave);
        eventosComprados = eventosComprados == null ? List.of() : List.copyOf(eventosComprados);
    }

    /** Sin nada declarado ni comprado: para el algoritmo es un cliente nuevo. */
    public boolean vacio() {
        return tiposEvento.isEmpty() && artistasFavoritos.isEmpty() && ciudad == null && eventosComprados.isEmpty();
    }

    public static Perfil nuevo(long clienteId) {
        return new Perfil(clienteId, null, null, null, null, null, null);
    }
}

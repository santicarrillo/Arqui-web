package eventos.service;

import java.time.LocalDateTime;
import java.util.List;

/** Evento interno de Spring (F8): se publico un evento. Lo atiende AvisosListener. */
public record EventoPublicado(long eventoId, String nombre, String ciudad, LocalDateTime fechaHora,
                              List<String> artistas) {
}

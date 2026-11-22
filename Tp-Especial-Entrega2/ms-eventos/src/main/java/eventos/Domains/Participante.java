package eventos.Domains;

/** Artista, equipo o disertante de un evento. Va embebido en el documento del evento. */
public record Participante(String nombre, String tipo) {}

package preferencias.service;

import org.springframework.stereotype.Service;
import preferencias.Domains.Perfil;
import preferencias.api.Dtos;
import preferencias.client.EventosClient;
import preferencias.client.EventosClient.EventoResumen;
import preferencias.repository.PerfilRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recomendaciones de eventos (F9). Cada evento publicado que el cliente todavia no compro suma puntos:
 *
 *   ARTISTA_FAVORITO                 +3  participa uno de sus artistas favoritos
 *   TIPO_PREFERIDO                   +2  es de un tipo que eligio (RECITAL, TEATRO...)
 *   TU_CIUDAD                        +1  es en su ciudad
 *   LO_COMPRARON_CLIENTES_PARECIDOS  +2  por cada cliente que compro lo mismo que el y tambien este evento (hasta 3)
 *   POPULAR                          +1  por cada cliente que lo compro (hasta 3), solo si el cliente no tiene datos
 *
 * Se devuelven los que suman algo, del mayor puntaje al menor (y a igual puntaje, el mas cercano primero).
 * Los pesos son simples a proposito: lo importante es que cada recomendacion se puede explicar con sus motivos.
 */
@Service
public class RecomendacionService {

    static final int PUNTOS_ARTISTA = 3;
    static final int PUNTOS_TIPO = 2;
    static final int PUNTOS_CIUDAD = 1;
    static final int PUNTOS_POR_CLIENTE_PARECIDO = 2;
    static final int PUNTOS_POR_COMPRADOR = 1;
    static final int MAXIMO_CLIENTES_CONTADOS = 3;
    static final int MAXIMO_RESULTADOS = 50;

    private final PerfilRepository repo;
    private final EventosClient eventos;
    private final Clock clock;

    public RecomendacionService(PerfilRepository repo, EventosClient eventos, Clock clock) {
        this.repo = repo;
        this.eventos = eventos;
        this.clock = clock;
    }

    public List<Dtos.RecomendacionView> recomendar(long clienteId, int limite) {
        int cuantos = Math.max(1, Math.min(limite, MAXIMO_RESULTADOS));
        Perfil perfil = repo.buscar(clienteId).orElse(Perfil.nuevo(clienteId));
        LocalDate hoy = LocalDate.now(clock);
        Set<Long> comprados = new HashSet<>(perfil.eventosComprados());

        // candidatos: el catalogo publicado desde hoy, menos lo que ya compro
        Map<Long, Candidato> candidatos = new LinkedHashMap<>();
        for (EventoResumen evento : eventos.publicadosDesde(hoy)) {
            if (!comprados.contains(evento.id())) {
                candidatos.put(evento.id(), new Candidato(evento));
            }
        }

        // artistas favoritos: eventos los busca dentro de los participantes embebidos de cada evento
        Set<Long> conArtistaFavorito = new HashSet<>();
        for (String artista : perfil.artistasFavoritos()) {
            eventos.publicadosConParticipante(artista, hoy).forEach(e -> conArtistaFavorito.add(e.id()));
        }

        Set<String> tipos = new HashSet<>();
        perfil.tiposEvento().forEach(t -> tipos.add(Textos.clave(t)));
        String ciudad = perfil.ciudad() == null ? null : Textos.clave(perfil.ciudad());

        Map<Long, Long> parecidos = perfil.eventosComprados().isEmpty() ? Map.of()
                : repo.compradosPorClientesParecidos(clienteId, perfil.eventosComprados(), MAXIMO_RESULTADOS);
        Map<Long, Long> populares = perfil.vacio() ? repo.masComprados(MAXIMO_RESULTADOS) : Map.of();

        for (Candidato c : candidatos.values()) {
            EventoResumen e = c.evento;
            if (conArtistaFavorito.contains(e.id())) {
                c.sumar(PUNTOS_ARTISTA, "ARTISTA_FAVORITO");
            }
            if (e.tipo() != null && tipos.contains(Textos.clave(e.tipo()))) {
                c.sumar(PUNTOS_TIPO, "TIPO_PREFERIDO");
            }
            if (ciudad != null && e.ciudad() != null && ciudad.equals(Textos.clave(e.ciudad()))) {
                c.sumar(PUNTOS_CIUDAD, "TU_CIUDAD");
            }
            long clientesParecidos = parecidos.getOrDefault(e.id(), 0L);
            if (clientesParecidos > 0) {
                c.sumar(PUNTOS_POR_CLIENTE_PARECIDO * (int) Math.min(clientesParecidos, MAXIMO_CLIENTES_CONTADOS),
                        "LO_COMPRARON_CLIENTES_PARECIDOS");
            }
            long compradores = populares.getOrDefault(e.id(), 0L);
            if (compradores > 0) {
                c.sumar(PUNTOS_POR_COMPRADOR * (int) Math.min(compradores, MAXIMO_CLIENTES_CONTADOS), "POPULAR");
            }
        }

        return candidatos.values().stream()
                .filter(c -> c.puntos > 0)
                .sorted(Comparator.comparingInt((Candidato c) -> c.puntos).reversed()
                        .thenComparing(c -> c.evento.fechaHora(), Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()))
                        .thenComparing(c -> c.evento.id()))
                .limit(cuantos)
                .map(Candidato::vista)
                .toList();
    }

    private static final class Candidato {
        private final EventoResumen evento;
        private final List<String> motivos = new ArrayList<>();
        private int puntos;

        private Candidato(EventoResumen evento) {
            this.evento = evento;
        }

        private void sumar(int cuantos, String motivo) {
            puntos += cuantos;
            motivos.add(motivo);
        }

        private Dtos.RecomendacionView vista() {
            return new Dtos.RecomendacionView(evento.id(), evento.nombre(), evento.tipo(), evento.fechaHora(),
                    evento.ciudad(), puntos, List.copyOf(motivos));
        }
    }
}

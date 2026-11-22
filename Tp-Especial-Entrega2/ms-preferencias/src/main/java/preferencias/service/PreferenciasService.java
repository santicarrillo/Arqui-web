package preferencias.service;

import org.springframework.stereotype.Service;
import preferencias.Domains.ApiException;
import preferencias.Domains.Perfil;
import preferencias.api.Dtos;
import preferencias.repository.PerfilRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class PreferenciasService {

    public static final int MAXIMO_POR_LISTA = 10;

    private final PerfilRepository repo;
    private final Clock clock;

    public PreferenciasService(PerfilRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    public Dtos.PerfilView obtener(long clienteId) {
        return Dtos.PerfilView.de(repo.buscar(clienteId).orElse(Perfil.nuevo(clienteId)));
    }

    public Dtos.PerfilView guardar(long clienteId, Dtos.PreferenciasRequest pedido) {
        // los tipos se guardan en mayusculas, como los usa eventos (RECITAL, TEATRO...)
        List<String> tipos = Textos.limpiar(pedido.tiposEvento(), s -> s.toUpperCase(Locale.ROOT));
        List<String> artistas = Textos.limpiar(pedido.artistasFavoritos(), s -> s);
        if (tipos.size() > MAXIMO_POR_LISTA || artistas.size() > MAXIMO_POR_LISTA) {
            throw ApiException.badRequest("DEMASIADAS_PREFERENCIAS",
                    "Se pueden elegir hasta " + MAXIMO_POR_LISTA + " tipos de evento y " + MAXIMO_POR_LISTA + " artistas");
        }
        String ciudad = pedido.ciudad() == null || pedido.ciudad().isBlank()
                ? null : pedido.ciudad().trim().replaceAll("\\s+", " ");
        List<String> claves = artistas.stream().map(Textos::clave).toList();

        repo.guardarPreferencias(clienteId, tipos, artistas, claves, ciudad, LocalDateTime.now(clock));
        return obtener(clienteId);
    }

    public void registrarCompra(long clienteId, long eventoId) {
        repo.registrarCompra(clienteId, eventoId, LocalDateTime.now(clock));
    }

    public List<Long> interesados(List<String> artistas) {
        List<String> claves = Textos.limpiar(artistas, s -> s).stream().map(Textos::clave).toList();
        if (claves.isEmpty()) {
            throw ApiException.badRequest("FALTAN_ARTISTAS", "Hay que indicar al menos un artista");
        }
        return repo.interesados(claves);
    }
}

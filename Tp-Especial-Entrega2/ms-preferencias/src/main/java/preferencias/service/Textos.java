package preferencias.service;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

/** Limpieza de lo que escribe el cliente, para comparar sin depender de mayusculas, tildes ni espacios. */
public final class Textos {

    private Textos() {
    }

    /** "  León   Gieco " -> "leon gieco". */
    public static String clave(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    /** Saca vacios, espacios de mas y repetidos (comparando por clave). Respeta el orden y la primera forma escrita. */
    public static List<String> limpiar(List<String> valores, UnaryOperator<String> forma) {
        if (valores == null) {
            return List.of();
        }
        Map<String, String> porClave = new LinkedHashMap<>();
        for (String valor : valores) {
            if (valor == null || valor.isBlank()) {
                continue;
            }
            String limpio = forma.apply(valor.trim().replaceAll("\\s+", " "));
            porClave.putIfAbsent(clave(limpio), limpio);
        }
        return List.copyOf(porClave.values());
    }
}

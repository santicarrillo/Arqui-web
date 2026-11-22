package preferencias.api;

import jakarta.validation.constraints.NotNull;
import preferencias.Domains.Perfil;

import java.time.LocalDateTime;
import java.util.List;

public final class Dtos {
    private Dtos() {}

    // ---------- requests ----------

    /** PUT /preferencias: reemplaza lo que el cliente declara. Nunca toca sus compras. */
    public record PreferenciasRequest(List<String> tiposEvento, List<String> artistasFavoritos, String ciudad) {}

    /** POST /interno/compras: lo manda ventas cuando se confirma una compra. */
    public record CompraRegistradaRequest(@NotNull Long clienteId, @NotNull Long eventoId) {}

    // ---------- responses ----------

    public record PerfilView(Long clienteId, List<String> tiposEvento, List<String> artistasFavoritos,
                             String ciudad, List<Long> eventosComprados, LocalDateTime actualizadoEn) {
        public static PerfilView de(Perfil p) {
            return new PerfilView(p.clienteId(), p.tiposEvento(), p.artistasFavoritos(), p.ciudad(),
                    p.eventosComprados(), p.actualizadoEn());
        }
    }

    /**
     * Un evento recomendado. motivos explica el puntaje: ARTISTA_FAVORITO, TIPO_PREFERIDO, TU_CIUDAD,
     * LO_COMPRARON_CLIENTES_PARECIDOS o POPULAR (este ultimo solo para clientes sin datos todavia).
     */
    public record RecomendacionView(Long eventoId, String nombre, String tipo, LocalDateTime fechaHora,
                                    String ciudad, int puntaje, List<String> motivos) {}
}

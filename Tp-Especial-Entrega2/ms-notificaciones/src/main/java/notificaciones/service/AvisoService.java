package notificaciones.service;

import notificaciones.Domains.Notificacion;
import notificaciones.api.Dtos;
import notificaciones.client.PreferenciasClient;
import notificaciones.repository.NotificacionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Arma las notificaciones de cada aviso: decide a quien, con que texto, y las guarda (una por cliente).
 * Solo se manda el email si la notificacion es nueva: un aviso repetido no genera un segundo email.
 * Devuelve cuantas notificaciones nuevas se crearon.
 */
@Service
public class AvisoService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final NotificacionRepository repo;
    private final PreferenciasClient preferencias;
    private final EmailSimulado email;
    private final Clock clock;

    public AvisoService(NotificacionRepository repo, PreferenciasClient preferencias, EmailSimulado email, Clock clock) {
        this.repo = repo;
        this.preferencias = preferencias;
        this.email = email;
        this.clock = clock;
    }

    public int compraConfirmada(Dtos.CompraConfirmadaRequest aviso) {
        String titulo = "Tu compra esta confirmada";
        String mensaje = "Compraste %d entrada(s) para el evento #%d por $%s. Tus codigos QR estan en tu compra #%d."
                .formatted(aviso.entradas(), aviso.eventoId(), aviso.total().toPlainString(), aviso.compraId());
        return crear(List.of(aviso.clienteId()), "compra-" + aviso.compraId(), "COMPRA_CONFIRMADA",
                aviso.eventoId(), titulo, mensaje);
    }

    public int eventoCancelado(Dtos.EventoCanceladoRequest aviso) {
        String titulo = "Se cancelo un evento para el que tenias entradas";
        String mensaje = "El evento #%d fue cancelado. Ya pedimos el reintegro del dinero de tus entradas."
                .formatted(aviso.eventoId());
        return crear(aviso.clientes(), "cancelado-" + aviso.eventoId(), "EVENTO_CANCELADO",
                aviso.eventoId(), titulo, mensaje);
    }

    public int eventoPublicado(Dtos.EventoPublicadoRequest aviso) {
        Collection<Long> fans = preferencias.interesados(aviso.artistas());
        String titulo = "Nuevo evento de un artista que seguis: " + aviso.nombre();
        StringBuilder mensaje = new StringBuilder(aviso.nombre());
        if (aviso.fechaHora() != null) {
            mensaje.append(", el ").append(aviso.fechaHora().format(FECHA));
        }
        if (aviso.ciudad() != null) {
            mensaje.append(" en ").append(aviso.ciudad());
        }
        mensaje.append(". Con ").append(String.join(", ", aviso.artistas())).append(". Ya podes comprar tus entradas.");
        return crear(fans, "publicado-" + aviso.eventoId(), "EVENTO_PUBLICADO", aviso.eventoId(), titulo,
                mensaje.toString());
    }

    private int crear(Collection<Long> clientes, String prefijo, String tipo, Long eventoId,
                      String titulo, String mensaje) {
        LocalDateTime ahora = LocalDateTime.now(clock);
        int nuevas = 0;
        for (Long clienteId : new LinkedHashSet<>(clientes)) {
            Notificacion notificacion = new Notificacion(prefijo + "-cliente-" + clienteId, clienteId, tipo, eventoId,
                    titulo, mensaje, "EMAIL", ahora, false);
            if (repo.insertarSiNoExiste(notificacion)) {
                email.enviar(clienteId, titulo, mensaje);
                nuevas++;
            }
        }
        return nuevas;
    }
}

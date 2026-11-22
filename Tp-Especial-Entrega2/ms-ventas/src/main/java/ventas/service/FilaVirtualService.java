package ventas.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ventas.Domains.ApiException;
import ventas.api.FilaDtos.ActivarFilaRequest;
import ventas.api.FilaDtos.FilaView;
import ventas.api.FilaDtos.TurnoView;
import ventas.client.EventosClient;
import ventas.repository.FilaRepository;
import ventas.repository.FilaRepository.Fila;
import ventas.repository.FilaRepository.Turno;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Fila virtual simplificada (F10), para que una venta masiva no sature el sistema.
 *
 * 1. El organizador la activa para su evento indicando cuantos clientes compran a la vez ("simultaneos") y
 *    cuantos minutos tiene cada uno.
 * 2. Cada cliente saca un turno y recibe un numero (POST /filas/{evento}/turno).
 * 3. Una tarea programada (FilaVirtualJob) habilita a los siguientes por orden de numero, hasta completar
 *    los simultaneos. Un turno sale de la tanda cuando el cliente compra (USADO) o se le termina el tiempo (VENCIDO).
 * 4. Mientras la fila esta activa, POST /compras solo acepta clientes con turno habilitado y vigente.
 *
 * Asi la base recibe como mucho "simultaneos" compras a la vez; el resto de los clientes solo consulta su turno,
 * que es una lectura barata.
 */
@Service
public class FilaVirtualService {

    private final FilaRepository repo;
    private final EventosClient eventos;
    private final Clock clock;

    public FilaVirtualService(FilaRepository repo, EventosClient eventos, Clock clock) {
        this.repo = repo;
        this.eventos = eventos;
        this.clock = clock;
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock);
    }

    // ---------------------------------------------------------------- organizador

    public FilaView activar(long usuarioId, String rol, long eventoId, ActivarFilaRequest pedido) {
        exigirDueno(usuarioId, rol, eventoId);
        repo.activar(eventoId, pedido.simultaneos(), pedido.minutosTurno(), ahora());
        return estado(eventoId);
    }

    /** Vuelve a la compra directa. Los turnos quedan guardados por si se reactiva. */
    public FilaView desactivar(long usuarioId, String rol, long eventoId) {
        exigirDueno(usuarioId, rol, eventoId);
        if (repo.fila(eventoId).isEmpty()) {
            throw ApiException.notFound("SIN_FILA", "El evento " + eventoId + " no tiene fila virtual");
        }
        repo.desactivar(eventoId, ahora());
        return estado(eventoId);
    }

    public FilaView estado(long eventoId) {
        return repo.fila(eventoId)
                .map(f -> new FilaView(eventoId, f.activa(), f.simultaneos(), f.minutosTurno(),
                        repo.contar(eventoId, "ESPERANDO"), repo.contar(eventoId, "HABILITADO")))
                .orElse(new FilaView(eventoId, false, null, null, 0, 0));
    }

    private void exigirDueno(long usuarioId, String rol, long eventoId) {
        String r = rol == null ? "" : rol.toUpperCase();
        if (!r.equals("ORGANIZADOR") && !r.equals("ADMINISTRADOR")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SOLO_ORGANIZADOR",
                    "Solo el organizador del evento o un administrador maneja la fila virtual");
        }
        Long organizador = eventos.organizadorDe(eventoId).orElseThrow(() ->
                ApiException.notFound("EVENTO_NO_ENCONTRADO", "El evento " + eventoId + " no existe"));
        if (r.equals("ORGANIZADOR") && organizador.longValue() != usuarioId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NO_ES_TU_EVENTO",
                    "Solo podes manejar la fila de tus propios eventos");
        }
    }

    // ---------------------------------------------------------------- cliente

    /**
     * Saca un turno. Si el cliente ya tiene uno en curso devuelve ese (pedirlo de nuevo no lo manda al final);
     * si el suyo ya termino, le da un numero nuevo al final de la fila.
     *
     * No es una sola transaccion a proposito: el numero se toma con un UPDATE que se confirma enseguida, asi la
     * fila del evento queda bloqueada solo un instante. Si algo falla despues, queda un numero sin usar, y eso no
     * molesta (los turnos se habilitan por orden, no por numeros consecutivos).
     */
    public TurnoView tomarTurno(long clienteId, long eventoId) {
        repo.fila(eventoId).filter(Fila::activa).orElseThrow(this::sinFila);
        LocalDateTime ahora = ahora();
        Optional<Turno> actual = repo.turno(eventoId, clienteId);
        if (actual.isPresent() && !termino(actual.get(), ahora)) {
            return vista(actual.get(), ahora);
        }

        long numero = repo.siguienteNumero(eventoId).orElseThrow(this::sinFila);
        if (actual.isPresent()) {
            repo.volverAEncolar(eventoId, clienteId, numero, ahora);
        } else {
            repo.insertarTurno(eventoId, clienteId, numero, ahora);
        }
        return vista(repo.turno(eventoId, clienteId).orElseThrow(), ahora);
    }

    public TurnoView turno(long clienteId, long eventoId) {
        Turno turno = repo.turno(eventoId, clienteId).orElseThrow(() ->
                ApiException.notFound("SIN_TURNO", "No tenes turno en la fila de este evento"));
        return vista(turno, ahora());
    }

    // ---------------------------------------------------------------- control de la compra

    /** Lo llama CompraController antes de crear una compra. Sin fila activa no hace nada. */
    public void exigirTurno(long clienteId, long eventoId) {
        Optional<Fila> fila = repo.fila(eventoId);
        if (fila.isEmpty() || !fila.get().activa()) {
            return;
        }
        LocalDateTime ahora = ahora();
        Turno turno = repo.turno(eventoId, clienteId).orElseThrow(() -> ApiException.conflict("TURNO_REQUERIDO",
                "Este evento tiene fila virtual: primero saca un turno (POST /filas/" + eventoId + "/turno)"));
        if (vigente(turno, ahora)) {
            return;
        }
        if ("ESPERANDO".equals(turno.estado())) {
            throw ApiException.conflict("TURNO_EN_ESPERA", "Todavia no es tu turno: hay "
                    + repo.esperandoAntesDe(eventoId, turno.numero()) + " persona(s) antes que vos");
        }
        throw ApiException.conflict("TURNO_VENCIDO", "Tu tiempo para comprar termino: saca un turno nuevo");
    }

    /** Despues de crear la compra: el lugar del cliente en la tanda pasa al siguiente. */
    public void marcarUsado(long clienteId, long eventoId) {
        repo.marcarUsado(eventoId, clienteId, ahora());
    }

    // ---------------------------------------------------------------- avance (tarea programada)

    /**
     * Vence los turnos sin tiempo y habilita a los siguientes hasta completar los simultaneos. Bloquea la fila
     * del evento durante la transaccion, asi dos instancias de ventas no habilitan de mas.
     * Devuelve cuantos turnos habilito.
     */
    @Transactional
    public int avanzar(long eventoId) {
        Optional<Fila> fila = repo.filaBloqueando(eventoId).filter(Fila::activa);
        if (fila.isEmpty()) {
            return 0;
        }
        LocalDateTime ahora = ahora();
        repo.vencerTurnos(eventoId, ahora);
        int lugares = fila.get().simultaneos() - (int) repo.contar(eventoId, "HABILITADO");
        if (lugares <= 0) {
            return 0;
        }
        return repo.habilitarSiguientes(eventoId, lugares, ahora, ahora.plusMinutes(fila.get().minutosTurno()));
    }

    // ---------------------------------------------------------------- comun

    /** Puede comprar: habilitado (o ya compro una vez) y dentro de su tiempo. */
    private static boolean vigente(Turno turno, LocalDateTime ahora) {
        return ("HABILITADO".equals(turno.estado()) || "USADO".equals(turno.estado()))
                && turno.venceEn() != null && turno.venceEn().isAfter(ahora);
    }

    /** Ya no esta en la fila ni puede comprar: puede sacar un turno nuevo. */
    private static boolean termino(Turno turno, LocalDateTime ahora) {
        return "VENCIDO".equals(turno.estado()) || (!"ESPERANDO".equals(turno.estado()) && !vigente(turno, ahora));
    }

    private TurnoView vista(Turno turno, LocalDateTime ahora) {
        boolean puedeComprar = vigente(turno, ahora);
        String estado = termino(turno, ahora) ? "VENCIDO" : turno.estado();
        Long antes = "ESPERANDO".equals(turno.estado()) ? repo.esperandoAntesDe(turno.eventoId(), turno.numero()) : null;
        return new TurnoView(turno.eventoId(), turno.numero(), estado, antes, turno.habilitadoEn(), turno.venceEn(),
                puedeComprar);
    }

    private ApiException sinFila() {
        return ApiException.conflict("SIN_FILA", "Este evento no tiene fila virtual activa: se compra directamente");
    }
}

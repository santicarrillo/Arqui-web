package ventas.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ventas.Domains.ApiException;
import ventas.Domains.Devolucion;
import ventas.api.DevolucionDtos.CancelacionView;
import ventas.api.DevolucionDtos.DevolucionView;
import ventas.client.ClientDtos.PagoInfo;
import ventas.client.ClientDtos.ReintegroRequest;
import ventas.client.PagosClient;
import ventas.repository.DevolucionRepository;
import ventas.repository.DevolucionRepository.EntradaDeCompra;

/**
 * Devoluciones de entradas y cancelacion de eventos.
 *
 * Las dos terminan igual: la devolucion queda APROBADA, la entrada DEVUELTA, el lugar vuelve a estar
 * libre y se le pide el reintegro a pagos-service. La diferencia es quien la origina: el cliente
 * (y un administrador la resuelve) o la cancelacion del evento (se aprueba sola).
 */
@Service
public class DevolucionService {

    private static final String ROL_ADMIN = "ADMINISTRADOR";
    private static final Set<String> ESTADOS = Set.of("PENDIENTE", "APROBADA", "RECHAZADA");

    private final DevolucionRepository repo;
    private final CompraService compras;
    private final PagosClient pagos;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int horasLimite;

    public DevolucionService(DevolucionRepository repo, CompraService compras, PagosClient pagos,
                             TransactionTemplate tx, Clock clock,
                             @Value("${ventas.devolucion-horas-limite:72}") int horasLimite) {
        this.repo = repo;
        this.compras = compras;
        this.pagos = pagos;
        this.tx = tx;
        this.clock = clock;
        this.horasLimite = horasLimite;
    }

    // ------------------------------------------------------- pedido del cliente

    /** El cliente pide devolver una entrada suya. Se acepta hasta 72 horas antes del evento. */
    @Transactional
    public DevolucionView solicitar(long clienteId, long entradaId) {
        EntradaDeCompra entrada = repo.entradaBloqueando(entradaId)
                .filter(e -> e.clienteId() == clienteId && "PAGADA".equals(e.compraEstado()))
                .orElseThrow(() -> ApiException.notFound("ENTRADA_NO_ENCONTRADA",
                        "La entrada " + entradaId + " no existe"));

        if (!"VALIDA".equals(entrada.estado())) {
            throw ApiException.conflict("ENTRADA_NO_DEVOLVIBLE",
                    "La entrada no se puede devolver (estado " + entrada.estado() + ")");
        }
        LocalDateTime ahora = ahora();
        if (ahora.plusHours(horasLimite).isAfter(entrada.eventoFechaHora())) {
            throw ApiException.conflict("FUERA_DE_PLAZO",
                    "Las devoluciones se aceptan hasta " + horasLimite + " horas antes del evento");
        }
        if (repo.activaDeEntrada(entradaId).isPresent()) {
            throw devolucionExistente();
        }

        long devolucionId;
        try {
            devolucionId = repo.insertar(entradaId, "SOLICITUD_CLIENTE", ahora);
        } catch (DuplicateKeyException e) {
            // Otro pedido de la misma entrada se nos adelanto: lo frena el indice unico de la base
            throw devolucionExistente();
        }
        return vista(repo.porId(devolucionId).orElseThrow());
    }

    // ------------------------------------------------- resolucion del administrador

    @Transactional
    public DevolucionView resolver(long adminId, String rol, long entradaId, boolean aprobar) {
        exigirAdmin(rol);
        EntradaDeCompra entrada = repo.entradaBloqueando(entradaId)
                .orElseThrow(() -> ApiException.notFound("ENTRADA_NO_ENCONTRADA",
                        "La entrada " + entradaId + " no existe"));
        Devolucion devolucion = repo.activaDeEntrada(entradaId)
                .filter(d -> "PENDIENTE".equals(d.estado()))
                .orElseThrow(() -> ApiException.notFound("DEVOLUCION_NO_ENCONTRADA",
                        "La entrada " + entradaId + " no tiene una devolucion pendiente"));

        if (aprobar) {
            aprobar(devolucion, entrada, adminId);
        } else {
            repo.resolver(devolucion.id(), "RECHAZADA", adminId, ahora());
        }
        return vista(repo.porId(devolucion.id()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<DevolucionView> listar(String rol, String estado) {
        exigirAdmin(rol);
        if (!ESTADOS.contains(estado)) {
            throw ApiException.badRequest("ESTADO_INVALIDO", "El estado tiene que ser uno de " + ESTADOS);
        }
        return repo.porEstado(estado).stream().map(this::vista).toList();
    }

    // ------------------------------------------------------ cancelacion de evento

    /**
     * Lo llama eventos-service cuando cancela un evento:
     * 1. las compras PENDIENTE pasan a VENCIDA y liberan sus lugares;
     * 2. cada entrada valida recibe una devolucion EVENTO_CANCELADO aprobada, salvo las que ya tienen una
     *    devolucion activa: si esta PENDIENTE (la pidio el cliente) se aprueba sola, sin esperar al administrador.
     *
     * Cada entrada se procesa en su propia transaccion. Si pagos-service se cae a mitad de camino, lo hecho
     * queda hecho y eventos puede repetir la llamada: las entradas ya devueltas se saltean.
     */
    public CancelacionView cancelarEvento(long eventoId) {
        int pendientes = 0;
        for (Long compraId : repo.comprasPendientesDeEvento(eventoId)) {
            compras.vencer(compraId);
            pendientes++;
        }

        int devueltas = 0;
        for (Long entradaId : repo.entradasVigentesDeEvento(eventoId)) {
            Boolean devuelta = tx.execute(estado -> devolverPorCancelacion(entradaId));
            if (Boolean.TRUE.equals(devuelta)) {
                devueltas++;
            }
        }
        return new CancelacionView(eventoId, pendientes, devueltas);
    }

    private boolean devolverPorCancelacion(long entradaId) {
        Optional<EntradaDeCompra> leida = repo.entradaBloqueando(entradaId);
        if (leida.isEmpty() || !"VALIDA".equals(leida.get().estado())) {
            return false; // ya la devolvio otro proceso mientras tanto
        }
        EntradaDeCompra entrada = leida.get();

        Devolucion devolucion;
        Optional<Devolucion> activa = repo.activaDeEntrada(entradaId);
        if (activa.isPresent()) {
            if (!"PENDIENTE".equals(activa.get().estado())) {
                return false; // ya estaba aprobada
            }
            devolucion = activa.get(); // la habia pedido el cliente: se aprueba sola
        } else {
            long id = repo.insertar(entradaId, "EVENTO_CANCELADO", ahora());
            devolucion = repo.porId(id).orElseThrow();
        }
        aprobar(devolucion, entrada, null);
        return true;
    }

    // --------------------------------------------------------------- comun

    /**
     * Aprueba la devolucion: la entrada queda DEVUELTA, el lugar vuelve a estar libre y se pide el reintegro.
     * El reintegro va ultimo: si pagos-service falla, la excepcion deshace todo y la devolucion sigue PENDIENTE.
     */
    private void aprobar(Devolucion devolucion, EntradaDeCompra entrada, Long adminId) {
        if (!repo.resolver(devolucion.id(), "APROBADA", adminId, ahora())) {
            return;
        }
        repo.marcarEntradaDevuelta(entrada.entradaId());
        if (entrada.asientoId() != null) {
            repo.liberarAsientoVendido(entrada.eventoId(), entrada.asientoId(), entrada.compraId());
        } else {
            repo.liberarLugarDeZona(entrada.eventoId(), entrada.sectorId());
        }

        PagoInfo pago = pagos.obtenerPorCompra(entrada.compraId())
                .orElseThrow(() -> ApiException.conflict("PAGO_NO_ENCONTRADO",
                        "pagos-service no tiene el pago de la compra " + entrada.compraId()));
        pagos.reintegrar(pago.id(), new ReintegroRequest(entrada.entradaId(), entrada.precio()));
    }

    private static void exigirAdmin(String rol) {
        if (!ROL_ADMIN.equalsIgnoreCase(rol)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SOLO_ADMIN",
                    "Solo un administrador puede ver y resolver devoluciones");
        }
    }

    private static ApiException devolucionExistente() {
        return ApiException.conflict("DEVOLUCION_EXISTENTE", "La entrada ya tiene una devolucion en curso");
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock);
    }

    private DevolucionView vista(Devolucion d) {
        return new DevolucionView(d.id(), d.detalleCompraId(), d.estado(), d.motivo(),
                d.fechaSolicitud(), d.adminId(), d.fechaResolucion());
    }
}

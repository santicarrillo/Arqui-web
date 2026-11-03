package service;

import Domains.ApiException;
import Domains.Compra;
import Domains.Detalle;
import api.Dtos;
import client.ClientDtos;
import client.EventosClient;
import client.PagosClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import repository.VentasRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import api.Dtos.CompraRequest;


public class CompraService {
    public record CompraCreada(Dtos.CompraView compra, boolean nueva) {
    }

    public enum Resultado { PAGADA, RECHAZADA, VENCIDA }

    public record ResultadoPago(Resultado resultado, Dtos.CompraView compra) {
    }

    private record Linea(long sectorId, Long asientoId, BigDecimal precio) {
    }

    private final VentasRepository repo;
    private final EventosClient eventos;
    private final PagosClient pagos;
    private final Clock clock;
    private final int minutosReserva;
    private final int limiteDefecto;

    public CompraService(VentasRepository repo, EventosClient eventos, PagosClient pagos, Clock clock,
                         @Value("${ventas.reserva-minutos:10}") int minutosReserva,
                         @Value("${ventas.limite-por-cliente:6}") int limiteDefecto) {
        this.repo = repo;
        this.eventos = eventos;
        this.pagos = pagos;
        this.clock = clock;
        this.minutosReserva = minutosReserva;
        this.limiteDefecto = limiteDefecto;
    }

    // ------------------------------------------------------------------ crear
    @Transactional
    public CompraCreada crear(long clienteId, String clave, CompraRequest pedido) {
        // Reintento: la misma Idempotency-Key devuelve la misma compra
        Optional<Compra> existente = repo.porClave(clave);
        if (existente.isPresent()) {
            return new CompraCreada(vista(existente.get()), false);
        }

        ClientDtos.EventoInfo evento = eventos.obtener(pedido.eventoId()).orElseThrow(() ->
                ApiException.notFound("EVENTO_NO_ENCONTRADO", "El evento " + pedido.eventoId() + " no existe"));
        if (!"PUBLICADO".equals(evento.estado())) {
            throw ApiException.conflict("EVENTO_NO_DISPONIBLE",
                    "El evento no esta a la venta (estado " + evento.estado() + ")");
        }

        List<Linea> lineas = expandir(pedido, evento);

        int limite = evento.limitePorCliente() != null ? evento.limitePorCliente() : limiteDefecto;
        long yaTiene = repo.contarEntradasActivas(clienteId, evento.id());
        if (yaTiene + lineas.size() > limite) {
            throw ApiException.conflict("LIMITE_EXCEDIDO", "El limite es de " + limite
                    + " entradas por cliente para este evento y ya tiene " + yaTiene);
        }

        BigDecimal total = lineas.stream().map(Linea::precio).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDateTime ahora = LocalDateTime.now(clock);
        Optional<Long> idNuevo = repo.insertarCompra(clienteId, evento.id(), evento.fechaHora(), total, clave,
                ahora, ahora.plusMinutes(minutosReserva));
        if (idNuevo.isEmpty()) {
            // Otro pedido con la misma clave se nos adelanto: es un reintento
            return new CompraCreada(vista(repo.porClave(clave).orElseThrow()), false);
        }
        long compraId = idNuevo.get();

        reservar(evento.id(), compraId, lineas);
        lineas.forEach(l -> repo.insertarDetalle(compraId, l.sectorId(), l.asientoId(), l.precio()));

        return new CompraCreada(vista(repo.porId(compraId).orElseThrow()), true);
    }

    /** Valida el pedido contra los sectores del evento y lo convierte en una linea por entrada. */
    private List<Linea> expandir(Dtos.CompraRequest pedido, ClientDtos.EventoInfo evento) {
        Map<Long, ClientDtos.SectorEvento> sectores = evento.sectores().stream()
                .collect(Collectors.toMap(ClientDtos.SectorEvento::sectorId, Function.identity()));
        List<Linea> lineas = new ArrayList<>();
        Set<Long> asientosPedidos = new HashSet<>();

        for (Dtos.LugarRequest lugar : pedido.lugares()) {
            ClientDtos.SectorEvento sector = sectores.get(lugar.sectorId());
            if (sector == null) {
                throw ApiException.badRequest("SECTOR_INVALIDO",
                        "El sector " + lugar.sectorId() + " no tiene precio en este evento");
            }
            if (sector.numerado()) {
                if (lugar.asientoId() == null) {
                    throw ApiException.badRequest("ASIENTO_REQUERIDO",
                            "El sector " + lugar.sectorId() + " es numerado: hay que indicar el asiento");
                }
                if (!asientosPedidos.add(lugar.asientoId())) {
                    throw ApiException.badRequest("ASIENTO_REPETIDO",
                            "El asiento " + lugar.asientoId() + " esta repetido en el pedido");
                }
                lineas.add(new Linea(sector.sectorId(), lugar.asientoId(), sector.precio()));
            } else {
                int cantidad = lugar.cantidad() == null ? 1 : lugar.cantidad();
                if (cantidad < 1) {
                    throw ApiException.badRequest("CANTIDAD_INVALIDA", "La cantidad tiene que ser al menos 1");
                }
                for (int i = 0; i < cantidad; i++) {
                    lineas.add(new Linea(sector.sectorId(), null, sector.precio()));
                }
            }
        }
        return lineas;
    }

    /**
     * Toma los lugares con UPDATE condicionales. Los asientos se recorren siempre ordenados, para que dos
     * compras que se cruzan no se bloqueen entre si. Si algun lugar ya no esta libre se lanza una excepcion
     * y la transaccion deshace todo.
     */
    private void reservar(long eventoId, long compraId, List<Linea> lineas) {
        List<Linea> asientos = lineas.stream()
                .filter(l -> l.asientoId() != null)
                .sorted(Comparator.comparingLong(Linea::sectorId).thenComparingLong(Linea::asientoId))
                .toList();
        for (Linea l : asientos) {
            if (!repo.tomarAsiento(eventoId, l.sectorId(), l.asientoId(), compraId)) {
                throw ApiException.conflict("LUGAR_NO_DISPONIBLE",
                        "El asiento " + l.asientoId() + " del sector " + l.sectorId() + " ya no esta disponible");
            }
        }

        Map<Long, Long> zonas = lineas.stream()
                .filter(l -> l.asientoId() == null)
                .collect(Collectors.groupingBy(Linea::sectorId, TreeMap::new, Collectors.counting()));
        for (Map.Entry<Long, Long> zona : zonas.entrySet()) {
            if (!repo.tomarCupo(eventoId, zona.getKey(), zona.getValue().intValue())) {
                throw ApiException.conflict("SIN_CUPO",
                        "No hay " + zona.getValue() + " lugares disponibles en el sector " + zona.getKey());
            }
        }
    }

    // ------------------------------------------------------------------ pagar

    /**
     * Paga una compra pendiente. El rechazo del pago y el vencimiento no son excepciones: tienen que
     * dejar la compra en su nuevo estado y liberar los lugares, y una excepcion deshace la transaccion.
     */
    @Transactional
    public ResultadoPago pagar(long clienteId, long compraId, String medioPago) {
        Compra compra = repo.porIdBloqueando(compraId)
                .filter(c -> c.clienteId() == clienteId)
                .orElseThrow(() -> ApiException.notFound("COMPRA_NO_ENCONTRADA", "La compra " + compraId + " no existe"));

        switch (compra.estado()) {
            case "PAGADA":
                return new ResultadoPago(Resultado.PAGADA, vista(compra));
            case "VENCIDA":
                return new ResultadoPago(Resultado.VENCIDA, vista(compra));
            case "RECHAZADA":
                throw ApiException.conflict("COMPRA_RECHAZADA",
                        "El pago de esta compra fue rechazado: hay que hacer una compra nueva");
            default:
                break;
        }

        if (!compra.venceEn().isAfter(LocalDateTime.now(clock))) {
            liberarYMarcar(compra, "VENCIDA");
            return new ResultadoPago(Resultado.VENCIDA, vista(repo.porId(compraId).orElseThrow()));
        }

        // Si pagos ya cobro (por ejemplo ventas se cayo despues del cobro), no se cobra de nuevo
        Optional<ClientDtos.PagoInfo> previo = pagos.obtenerPorCompra(compra.id());
        ClientDtos.PagoInfo pago = previo.orElseGet(() ->
                pagos.cobrar(new ClientDtos.CobroRequest(compra.id(), compra.clienteId(), compra.total())));

        if ("APROBADO".equals(pago.estado())) {
            confirmar(compra, medioPago);
            return new ResultadoPago(Resultado.PAGADA, vista(repo.porId(compraId).orElseThrow()));
        }
        liberarYMarcar(compra, "RECHAZADA");
        return new ResultadoPago(Resultado.RECHAZADA, vista(repo.porId(compraId).orElseThrow()));
    }

    private void confirmar(Compra compra, String medioPago) {
        repo.venderAsientos(compra.id());
        for (Detalle d : repo.detalles(compra.id())) {
            if (d.codigoQr() == null) {
                repo.asignarQr(d.id(), "QR-%d-%d-%d-%s".formatted(compra.eventoId(), compra.id(), d.id(),
                        UUID.randomUUID().toString().substring(0, 8)));
            }
        }
        repo.marcarPagada(compra.id(), medioPago);
    }

    // --------------------------------------------------- cancelar y vencer

    /** El cliente cancela una compra que todavia no pago. */
    @Transactional
    public void cancelar(long clienteId, long compraId) {
        Compra compra = repo.porIdBloqueando(compraId)
                .filter(c -> c.clienteId() == clienteId)
                .orElseThrow(() -> ApiException.notFound("COMPRA_NO_ENCONTRADA", "La compra " + compraId + " no existe"));
        if (!"PENDIENTE".equals(compra.estado())) {
            throw ApiException.conflict("COMPRA_NO_CANCELABLE",
                    "Solo se puede cancelar una compra pendiente (estado " + compra.estado()
                            + "). Una compra pagada se devuelve con una devolucion");
        }
        liberarYMarcar(compra, "VENCIDA");
    }

    /** Lo llama la tarea programada para cada compra pendiente que ya paso su vencimiento. */
    @Transactional
    public void vencer(long compraId) {
        repo.porIdBloqueando(compraId)
                .filter(c -> "PENDIENTE".equals(c.estado()))
                .ifPresent(c -> liberarYMarcar(c, "VENCIDA"));
    }

    /**
     * Cambia el estado desde PENDIENTE y libera los lugares. El cambio de estado es condicional, asi que
     * si se ejecuta dos veces la segunda no libera nada.
     */
    private void liberarYMarcar(Compra compra, String nuevoEstado) {
        if (!repo.cambiarEstadoDesdePendiente(compra.id(), nuevoEstado)) {
            return;
        }
        repo.liberarAsientos(compra.id());
        Map<Long, Long> zonas = repo.detalles(compra.id()).stream()
                .filter(d -> d.asientoId() == null)
                .collect(Collectors.groupingBy(Detalle::sectorId, TreeMap::new, Collectors.counting()));
        zonas.forEach((sector, cantidad) -> repo.liberarCupo(compra.eventoId(), sector, cantidad.intValue()));
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public Dtos.CompraView obtener(long clienteId, long compraId) {
        return repo.porId(compraId)
                .filter(c -> c.clienteId() == clienteId)
                .map(this::vista)
                .orElseThrow(() -> ApiException.notFound("COMPRA_NO_ENCONTRADA", "La compra " + compraId + " no existe"));
    }

    private Dtos.CompraView vista(Compra c) {
        List<Dtos.DetalleView> detalle = repo.detalles(c.id()).stream()
                .map(d -> new Dtos.DetalleView(d.id(), d.sectorId(), d.asientoId(), d.precioUnitario(),
                        d.codigoQr(), d.estado()))
                .toList();
        return new Dtos.CompraView(c.id(), c.eventoId(), c.estado(), c.total(), c.medioPago(), c.venceEn(), detalle);
    }


}

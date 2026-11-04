package integration;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import ventas.Domains.ApiException;
import ventas.api.DevolucionDtos.CancelacionView;
import ventas.api.DevolucionDtos.DevolucionView;
import ventas.api.Dtos.CompraRequest;
import ventas.api.Dtos.CompraView;
import ventas.client.ClientDtos.PagoInfo;
import ventas.client.ClientDtos.ReintegroRequest;
import ventas.service.DevolucionService;

/** Devoluciones pedidas por el cliente y cancelacion de eventos, contra PostgreSQL real. */
class DevolucionTest extends IntegracionBase {

    private static final long PAGO_ID = 500L;
    private static final long CLIENTE = 55L;
    private static final long ADMIN = 1L;

    @Autowired
    private DevolucionService devoluciones;

    /** pagos-service conoce un pago APROBADO para cualquier compra: pagar la confirma sin cobrar de nuevo. */
    @BeforeEach
    void pagosTieneElPago() {
        when(pagos.obtenerPorCompra(anyLong()))
                .thenAnswer(inv -> Optional.of(new PagoInfo(PAGO_ID, inv.getArgument(0), "APROBADO")));
    }

    /** Compra y paga un lugar; devuelve el id de la entrada. */
    private long comprarYPagar(long cliente, String clave, CompraRequest pedido) {
        CompraView compra = compras.crear(cliente, clave, pedido).compra();
        compras.pagar(cliente, compra.compraId(), "TARJETA");
        return compra.detalle().get(0).entradaId();
    }

    private String estadoEntrada(long entradaId) {
        return jdbc.sql("SELECT estado FROM detalle_compra WHERE id = :id")
                .param("id", entradaId).query(String.class).single();
    }

    private String devolucionDe(long entradaId) {
        return jdbc.sql("SELECT estado || ' ' || motivo FROM devolucion WHERE detalle_compra_id = :id")
                .param("id", entradaId).query(String.class).single();
    }

    // ------------------------------------------------------------ pedido del cliente

    @Test
    void elClienteLaPideYElAdminLaApruebaLaEntradaQuedaDevueltaYElAsientoLibre() {
        long entrada = comprarYPagar(CLIENTE, "d1", pedidoAsiento(ASIENTO));
        assertEquals("VENDIDO", estadoAsiento(ASIENTO));

        DevolucionView pedida = devoluciones.solicitar(CLIENTE, entrada);
        assertEquals("PENDIENTE", pedida.estado());
        assertEquals("SOLICITUD_CLIENTE", pedida.motivo());
        assertEquals("VALIDA", estadoEntrada(entrada), "mientras esta pendiente la entrada sigue valida");

        DevolucionView aprobada = devoluciones.resolver(ADMIN, "ADMIN", entrada, true);

        assertEquals("APROBADA", aprobada.estado());
        assertEquals(Long.valueOf(ADMIN), aprobada.adminId());
        assertEquals("DEVUELTA", estadoEntrada(entrada));
        assertEquals("LIBRE", estadoAsiento(ASIENTO), "el asiento se puede volver a vender");

        ArgumentCaptor<ReintegroRequest> reintegro = ArgumentCaptor.forClass(ReintegroRequest.class);
        verify(pagos).reintegrar(eq(PAGO_ID), reintegro.capture());
        assertEquals(Long.valueOf(entrada), reintegro.getValue().detalleCompraId());
        assertEquals(0, new BigDecimal("30000").compareTo(reintegro.getValue().monto()));
    }

    @Test
    void siElAdminLaRechazaLaEntradaSigueValida() {
        long entrada = comprarYPagar(CLIENTE, "d2", pedidoAsiento(ASIENTO));
        devoluciones.solicitar(CLIENTE, entrada);

        DevolucionView rechazada = devoluciones.resolver(ADMIN, "ADMIN", entrada, false);

        assertEquals("RECHAZADA", rechazada.estado());
        assertEquals("VALIDA", estadoEntrada(entrada));
        assertEquals("VENDIDO", estadoAsiento(ASIENTO));
        verify(pagos, never()).reintegrar(anyLong(), any());
    }

    @Test
    void aMenosDe72HorasDelEventoNoSePuedeDevolver() {
        long entrada = comprarYPagar(CLIENTE, "d3", pedidoAsiento(ASIENTO));
        // el evento ahora es dentro de 48 horas
        jdbc.sql("UPDATE compra SET evento_fecha_hora = :f")
                .param("f", LocalDateTime.now().plusHours(48)).update();

        ApiException e = assertThrows(ApiException.class, () -> devoluciones.solicitar(CLIENTE, entrada));

        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("FUERA_DE_PLAZO", e.getError());
    }

    @Test
    void unaEntradaNoPuedeTenerDosDevolucionesEnCurso() {
        long entrada = comprarYPagar(CLIENTE, "d4", pedidoAsiento(ASIENTO));
        devoluciones.solicitar(CLIENTE, entrada);

        ApiException e = assertThrows(ApiException.class, () -> devoluciones.solicitar(CLIENTE, entrada));

        assertEquals("DEVOLUCION_EXISTENTE", e.getError());
        assertEquals(1, contar("devolucion"));
    }

    @Test
    void despuesDeUnRechazoSePuedePedirDeNuevo() {
        long entrada = comprarYPagar(CLIENTE, "d5", pedidoAsiento(ASIENTO));
        devoluciones.solicitar(CLIENTE, entrada);
        devoluciones.resolver(ADMIN, "ADMIN", entrada, false);

        DevolucionView otra = devoluciones.solicitar(CLIENTE, entrada);

        assertEquals("PENDIENTE", otra.estado());
        assertEquals(2, contar("devolucion"), "la rechazada no cuenta como activa");
    }

    @Test
    void soloUnAdministradorPuedeResolver() {
        long entrada = comprarYPagar(CLIENTE, "d6", pedidoAsiento(ASIENTO));
        devoluciones.solicitar(CLIENTE, entrada);

        ApiException e = assertThrows(ApiException.class,
                () -> devoluciones.resolver(CLIENTE, "CLIENTE", entrada, true));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
    }

    @Test
    void nadiePuedeDevolverUnaEntradaAjena() {
        long entrada = comprarYPagar(CLIENTE, "d7", pedidoAsiento(ASIENTO));

        ApiException e = assertThrows(ApiException.class, () -> devoluciones.solicitar(999L, entrada));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void devolverUnaEntradaDeZonaLiberaElCupo() {
        long entrada = comprarYPagar(CLIENTE, "d8", pedidoZona(1));
        assertEquals(1, ocupadosZona());

        devoluciones.solicitar(CLIENTE, entrada);
        devoluciones.resolver(ADMIN, "ADMIN", entrada, true);

        assertEquals(0, ocupadosZona());
    }

    // --------------------------------------------------------- cancelacion de evento

    @Test
    void cancelarElEventoDevuelveTodasLasEntradasYVenceLasPendientes() {
        long entradaA = comprarYPagar(10L, "a", pedidoAsiento(100L));   // pagada, sin devolucion
        long entradaB = comprarYPagar(20L, "b", pedidoAsiento(101L));   // pagada, con devolucion pedida
        devoluciones.solicitar(20L, entradaB);
        long compraC = compras.crear(30L, "c", pedidoAsiento(102L)).compra().compraId(); // sin pagar

        CancelacionView resultado = devoluciones.cancelarEvento(EVENTO);

        assertEquals(1, resultado.comprasPendientesCanceladas());
        assertEquals(2, resultado.entradasDevueltas());

        assertEquals("DEVUELTA", estadoEntrada(entradaA));
        assertEquals("APROBADA EVENTO_CANCELADO", devolucionDe(entradaA));
        assertEquals("DEVUELTA", estadoEntrada(entradaB));
        assertEquals("APROBADA SOLICITUD_CLIENTE", devolucionDe(entradaB),
                "la que pidio el cliente se aprueba sola y no se crea una segunda");
        assertEquals(2, contar("devolucion"));

        assertEquals("VENCIDA", jdbc.sql("SELECT estado FROM compra WHERE id = :id")
                .param("id", compraC).query(String.class).single());
        assertEquals("LIBRE", estadoAsiento(100L));
        assertEquals("LIBRE", estadoAsiento(101L));
        assertEquals("LIBRE", estadoAsiento(102L));
        verify(pagos, times(2)).reintegrar(eq(PAGO_ID), any());
    }

    @Test
    void cancelarDosVecesElMismoEventoNoDuplicaNada() {
        comprarYPagar(10L, "a", pedidoAsiento(100L));

        devoluciones.cancelarEvento(EVENTO);
        CancelacionView segunda = devoluciones.cancelarEvento(EVENTO);

        assertEquals(0, segunda.entradasDevueltas());
        assertEquals(1, contar("devolucion"));
        verify(pagos, times(1)).reintegrar(eq(PAGO_ID), any());
    }
}

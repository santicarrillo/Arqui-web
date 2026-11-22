package integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import java.util.List;
import java.util.Optional;

import ventas.Domains.ApiException;
import ventas.api.Dtos;
import ventas.api.Dtos.CompraView;
import ventas.api.Dtos.LugarRequest;
import ventas.client.ClientDtos;
import ventas.client.ClientDtos.PagoInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import ventas.service.CompraService;
import ventas.service.CompraService.Resultado;
import ventas.service.CompraService.ResultadoPago;
import ventas.service.VencimientoJob;

class FlujoCompraTest extends IntegracionBase {

    private static final long CLIENTE = 55L;

    @Autowired
    private VencimientoJob vencimiento;

    /** Usa la hora de Java (la misma que el servicio), no now() de la base, que puede estar en otra zona. */
    private void vencerAhora() {
        jdbc.sql("UPDATE compra SET vence_en = :v").param("v", java.time.LocalDateTime.now().minusMinutes(1)).update();
    }

    @Test
    void pagoAprobadoVendeElAsientoYEmiteElQr() {
        Dtos.CompraView compra = compras.crear(CLIENTE, "k1", pedidoAsiento(ASIENTO)).compra();
        assertEquals("PENDIENTE", compra.estado());
        assertEquals("RESERVADO", estadoAsiento(ASIENTO));

        when(pagos.cobrar(any())).thenReturn(new ClientDtos.PagoInfo(1L, compra.compraId(), "APROBADO"));
        CompraService.ResultadoPago pagado = compras.pagar(CLIENTE, compra.compraId(), "TARJETA");

        assertEquals(Resultado.PAGADA, pagado.resultado());
        assertEquals("PAGADA", pagado.compra().estado());
        assertEquals("VENDIDO", estadoAsiento(ASIENTO));
        assertNotNull(pagado.compra().detalle().get(0).codigoQr(), "cada entrada recibe su QR");
    }

    @Test
    void pagoRechazadoLiberaElAsientoYDejaLaCompraRechazada() {
        Dtos.CompraView compra = compras.crear(CLIENTE, "k2", pedidoAsiento(ASIENTO)).compra();

        when(pagos.cobrar(any())).thenReturn(new PagoInfo(1L, compra.compraId(), "RECHAZADO"));
        ResultadoPago resultado = compras.pagar(CLIENTE, compra.compraId(), "TARJETA");

        assertEquals(Resultado.RECHAZADA, resultado.resultado());
        assertEquals("RECHAZADA", resultado.compra().estado());
        assertEquals("LIBRE", estadoAsiento(ASIENTO));
        // el lugar quedo disponible para otro cliente
        assertEquals("PENDIENTE", compras.crear(77L, "otra-clave", pedidoAsiento(ASIENTO)).compra().estado());
    }

    @Test
    void siPagosYaCobroNoSeCobraDeNuevo() {
        Dtos.CompraView compra = compras.crear(CLIENTE, "k3", pedidoAsiento(ASIENTO)).compra();
        // ventas se cayo despues del cobro: la compra sigue PENDIENTE pero pagos ya tiene el pago
        when(pagos.obtenerPorCompra(compra.compraId()))
                .thenReturn(Optional.of(new PagoInfo(9L, compra.compraId(), "APROBADO")));

        CompraService.ResultadoPago resultado = compras.pagar(CLIENTE, compra.compraId(), "TARJETA");

        assertEquals(Resultado.PAGADA, resultado.resultado());
        verify(pagos, never()).cobrar(any());
    }

    @Test
    void repetirElPagoDeUnaCompraPagadaNoCobraDeNuevo() {
        CompraView compra = compras.crear(CLIENTE, "k4", pedidoAsiento(ASIENTO)).compra();
        when(pagos.cobrar(any())).thenReturn(new PagoInfo(1L, compra.compraId(), "APROBADO"));
        compras.pagar(CLIENTE, compra.compraId(), "TARJETA");

        ResultadoPago repetido = compras.pagar(CLIENTE, compra.compraId(), "TARJETA");

        assertEquals(Resultado.PAGADA, repetido.resultado());
        verify(pagos, org.mockito.Mockito.times(1)).cobrar(any());
    }

    @Test
    void laReservaVencidaLiberaLosLugaresYNoSePuedePagar() {
        Dtos.CompraView compra = compras.crear(CLIENTE, "k5", pedidoAsiento(ASIENTO)).compra();
        compras.crear(CLIENTE, "k5-zona", pedidoZona(1));
        assertEquals(1, ocupadosZona());

        // la reserva se vencio hace un minuto
        vencerAhora();
        vencimiento.vencerReservas();

        assertEquals("LIBRE", estadoAsiento(ASIENTO));
        assertEquals(0, ocupadosZona(), "la zona recupera su cupo");
        ResultadoPago intento = compras.pagar(CLIENTE, compra.compraId(), "TARJETA");
        assertEquals(Resultado.VENCIDA, intento.resultado());
        verify(pagos, never()).cobrar(any());
    }

    @Test
    void siLaReservaVencioPeroLaTareaNoCorrioTodaviaElPagoLaVence() {
        CompraView compra = compras.crear(CLIENTE, "k6", pedidoAsiento(ASIENTO)).compra();
        vencerAhora();

        ResultadoPago intento = compras.pagar(CLIENTE, compra.compraId(), "TARJETA");

        assertEquals(Resultado.VENCIDA, intento.resultado());
        assertEquals("LIBRE", estadoAsiento(ASIENTO));
        verify(pagos, never()).cobrar(any());
    }

    @Test
    void cancelarUnaCompraPendienteLiberaLosLugares() {
        CompraView compra = compras.crear(CLIENTE, "k7", pedidoAsiento(ASIENTO)).compra();

        compras.cancelar(CLIENTE, compra.compraId());

        assertEquals("LIBRE", estadoAsiento(ASIENTO));
        ApiException e = assertThrows(ApiException.class, () -> compras.cancelar(CLIENTE, compra.compraId()));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @Test
    void elLimiteDeEntradasPorClienteSeRespeta() {
        // el limite del evento simulado es 6: cinco asientos mas una zona de 1 lugar son 6
        List<LugarRequest> cinco = List.of(100L, 101L, 102L, 103L, 104L).stream()
                .map(a -> new LugarRequest(SECTOR_NUMERADO, a, null)).toList();
        compras.crear(CLIENTE, "k8", new Dtos.CompraRequest(EVENTO, cinco));
        compras.crear(CLIENTE, "k8-zona", pedidoZona(1));

        ApiException e = assertThrows(ApiException.class,
                () -> compras.crear(CLIENTE, "k8-extra", pedidoZona(1)));

        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("LIMITE_EXCEDIDO", e.getError());
    }

    @Test
    void unaCompraPuedeMezclarAsientosYZona() {
        Dtos.CompraView compra = compras.crear(CLIENTE, "k9",
                new Dtos.CompraRequest(EVENTO, List.of(
                        new LugarRequest(SECTOR_NUMERADO, ASIENTO, null),
                        new LugarRequest(SECTOR_ZONA, null, 1)))).compra();

        assertEquals(2, compra.detalle().size());
        assertEquals(0, new java.math.BigDecimal("45000").compareTo(compra.total()));
        assertTrue(compra.detalle().stream().anyMatch(d -> d.asientoId() == null));
        assertFalse(compra.detalle().stream().allMatch(d -> d.asientoId() == null));
    }
}


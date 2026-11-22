package integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import ventas.Domains.ApiException;
import ventas.api.Dtos.CompraView;
import ventas.api.ReporteDtos.ReporteEventoView;
import ventas.api.ReporteDtos.ReporteSectorView;
import ventas.client.ClientDtos.PagoInfo;
import ventas.service.ReporteService;

/** Reportes del organizador (F7), contra PostgreSQL real. */
class ReporteTest extends IntegracionBase {

    private static final long ORGANIZADOR = 30L;

    @Autowired
    private ReporteService reportes;

    /**
     * El evento de IntegracionBase (asientos 100 a 104 a $30000 y una zona de 1 lugar a $15000) con:
     *  - asiento 100 pagado y ya usado en la puerta;
     *  - asiento 101 pagado y despues devuelto;
     *  - el lugar de la zona pagado;
     *  - asiento 102 reservado pero sin pagar (no cuenta).
     */
    @BeforeEach
    void ventasDelEvento() {
        when(eventos.organizadorDe(EVENTO)).thenReturn(Optional.of(ORGANIZADOR));
        when(pagos.obtenerPorCompra(anyLong()))
                .thenAnswer(inv -> Optional.of(new PagoInfo(1L, inv.getArgument(0), "APROBADO")));

        long usada = comprarYPagar(1L, "r1", pedidoAsiento(100L));
        long devuelta = comprarYPagar(2L, "r2", pedidoAsiento(101L));
        comprarYPagar(3L, "r3", pedidoZona(1));
        compras.crear(4L, "r4", pedidoAsiento(102L));

        jdbc.sql("UPDATE detalle_compra SET estado = 'USADA' WHERE id = :id").param("id", usada).update();
        jdbc.sql("UPDATE detalle_compra SET estado = 'DEVUELTA' WHERE id = :id").param("id", devuelta).update();
    }

    private long comprarYPagar(long cliente, String clave, ventas.api.Dtos.CompraRequest pedido) {
        CompraView compra = compras.crear(cliente, clave, pedido).compra();
        compras.pagar(cliente, compra.compraId(), "TARJETA");
        return compra.detalle().get(0).entradaId();
    }

    @Test
    void elOrganizadorVeVentasDevolucionesOcupacionYRecaudacion() {
        ReporteEventoView reporte = reportes.deEvento(ORGANIZADOR, "ORGANIZADOR", EVENTO);

        assertEquals(6, reporte.capacidad(), "5 asientos + 1 lugar de la zona");
        assertEquals(2, reporte.vendidas(), "asiento 100 y la zona; el 101 se devolvio y el 102 no se pago");
        assertEquals(1, reporte.devueltas());
        assertEquals(1, reporte.ingresados());
        assertEquals(33.3, reporte.ocupacionPorcentaje());
        assertEquals(0, new BigDecimal("45000").compareTo(reporte.recaudacion()));

        ReporteSectorView numerado = reporte.sectores().get(0);
        assertEquals(Long.valueOf(SECTOR_NUMERADO), numerado.sectorId());
        assertEquals(5, numerado.capacidad());
        assertEquals(1, numerado.vendidas());
        assertEquals(1, numerado.devueltas());
        assertEquals(20.0, numerado.ocupacionPorcentaje());
        assertEquals(0, new BigDecimal("30000").compareTo(numerado.recaudacion()));

        ReporteSectorView zona = reporte.sectores().get(1);
        assertEquals(Long.valueOf(SECTOR_ZONA), zona.sectorId());
        assertEquals(100.0, zona.ocupacionPorcentaje());
    }

    @Test
    void otroOrganizadorNoPuedeVerElReporte() {
        ApiException e = assertThrows(ApiException.class, () -> reportes.deEvento(999L, "ORGANIZADOR", EVENTO));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertEquals("NO_ES_TU_EVENTO", e.getError());
    }

    @Test
    void unAdministradorVeElReporteDeCualquierEvento() {
        ReporteEventoView reporte = reportes.deEvento(1L, "ADMINISTRADOR", EVENTO);

        assertEquals(2, reporte.vendidas());
    }

    @Test
    void unClienteNoPuedeVerReportes() {
        ApiException e = assertThrows(ApiException.class, () -> reportes.deEvento(1L, "CLIENTE", EVENTO));

        assertEquals("SOLO_ORGANIZADOR", e.getError());
    }

    @Test
    void unEventoQueNoExisteDa404() {
        when(eventos.organizadorDe(888L)).thenReturn(Optional.empty());

        ApiException e = assertThrows(ApiException.class, () -> reportes.deEvento(1L, "ADMINISTRADOR", 888L));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }
}

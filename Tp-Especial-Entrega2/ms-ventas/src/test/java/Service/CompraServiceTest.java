package Service;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ventas.api.Dtos.CompraRequest;
import ventas.api.Dtos.LugarRequest;
import ventas.client.ClientDtos.EventoInfo;
import ventas.client.ClientDtos.PagoInfo;
import ventas.client.ClientDtos.SectorEvento;
import ventas.client.EventosClient;
import ventas.client.PagosClient;
import ventas.Domains.ApiException;
import ventas.Domains.Compra;
import ventas.repository.VentasRepository;
import ventas.service.CompraService;
import ventas.service.CompraService.Resultado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Pruebas unitarias de la logica de compra, sin base de datos: el repositorio y los clientes son simulados. */
class CompraServiceTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-03T15:00:00Z"), ZoneId.of("UTC"));
    private static final LocalDateTime AHORA = LocalDateTime.now(RELOJ);

    private VentasRepository repo;
    private EventosClient eventos;
    private PagosClient pagos;
    private CompraService service;

    @BeforeEach
    void preparar() {
        repo = mock(VentasRepository.class);
        eventos = mock(EventosClient.class);
        pagos = mock(PagosClient.class);
        service = new CompraService(repo, eventos, pagos, RELOJ, 10, 6);
    }

    private EventoInfo evento(String estado, Integer limite) {
        return new EventoInfo(7L, estado, AHORA.plusDays(30), limite,
                List.of(new SectorEvento(1L, true, new BigDecimal("30000")),
                        new SectorEvento(2L, false, new BigDecimal("15000"))));
    }

    private CompraRequest pedidoAsiento() {
        return new CompraRequest(7L, List.of(new LugarRequest(1L, 100L, null)));
    }

    private Compra compraPendiente(LocalDateTime venceEn) {
        return new Compra(1L, 55L, 7L, AHORA.plusDays(30), "PENDIENTE", null, new BigDecimal("30000"),
                "k", AHORA, venceEn);
    }

    @Test
    void unEventoQueNoEstaPublicadoNoSeVende() {
        when(repo.porClave("k")).thenReturn(Optional.empty());
        when(eventos.obtener(7L)).thenReturn(Optional.of(evento("BORRADOR", 6)));

        ApiException e = assertThrows(ApiException.class, () -> service.crear(55L, "k", pedidoAsiento()));

        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("EVENTO_NO_DISPONIBLE", e.getError());
    }

    @Test
    void unEventoInexistenteDa404() {
        when(repo.porClave("k")).thenReturn(Optional.empty());
        when(eventos.obtener(7L)).thenReturn(Optional.empty());

        ApiException e = assertThrows(ApiException.class, () -> service.crear(55L, "k", pedidoAsiento()));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void superarElLimiteDeEntradasPorClienteDa409() {
        when(repo.porClave("k")).thenReturn(Optional.empty());
        when(eventos.obtener(7L)).thenReturn(Optional.of(evento("PUBLICADO", 6)));
        when(repo.contarEntradasActivas(55L, 7L)).thenReturn(6L);

        ApiException e = assertThrows(ApiException.class, () -> service.crear(55L, "k", pedidoAsiento()));

        assertEquals("LIMITE_EXCEDIDO", e.getError());
        verify(repo, never()).insertarCompra(anyLong(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void unSectorSinPrecioEnElEventoDa400() {
        when(repo.porClave("k")).thenReturn(Optional.empty());
        when(eventos.obtener(7L)).thenReturn(Optional.of(evento("PUBLICADO", 6)));
        CompraRequest pedido = new CompraRequest(7L, List.of(new LugarRequest(99L, 100L, null)));

        ApiException e = assertThrows(ApiException.class, () -> service.crear(55L, "k", pedido));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void unAsientoQueYaNoEstaLibreDa409() {
        when(repo.porClave("k")).thenReturn(Optional.empty());
        when(eventos.obtener(7L)).thenReturn(Optional.of(evento("PUBLICADO", 6)));
        when(repo.contarEntradasActivas(55L, 7L)).thenReturn(0L);
        when(repo.insertarCompra(anyLong(), anyLong(), any(), any(), any(), any(), any())).thenReturn(Optional.of(1L));
        when(repo.tomarAsiento(7L, 1L, 100L, 1L)).thenReturn(false);

        ApiException e = assertThrows(ApiException.class, () -> service.crear(55L, "k", pedidoAsiento()));

        assertEquals("LUGAR_NO_DISPONIBLE", e.getError());
        verify(repo, never()).insertarDetalle(anyLong(), anyLong(), any(), any());
    }

    @Test
    void unaZonaSinCupoDa409() {
        when(repo.porClave("k")).thenReturn(Optional.empty());
        when(eventos.obtener(7L)).thenReturn(Optional.of(evento("PUBLICADO", 6)));
        when(repo.insertarCompra(anyLong(), anyLong(), any(), any(), any(), any(), any())).thenReturn(Optional.of(1L));
        when(repo.tomarCupo(anyLong(), anyLong(), anyInt())).thenReturn(false);
        CompraRequest pedido = new CompraRequest(7L, List.of(new LugarRequest(2L, null, 3)));

        ApiException e = assertThrows(ApiException.class, () -> service.crear(55L, "k", pedido));

        assertEquals("SIN_CUPO", e.getError());
    }

    @Test
    void elReintentoConLaMismaClaveDevuelveLaCompraExistenteSinReservarNada() {
        when(repo.porClave("k")).thenReturn(Optional.of(compraPendiente(AHORA.plusMinutes(5))));
        when(repo.detalles(1L)).thenReturn(List.of());

        var creada = service.crear(55L, "k", pedidoAsiento());

        assertEquals(false, creada.nueva());
        verify(eventos, never()).obtener(anyLong());
        verify(repo, never()).tomarAsiento(anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void pagarUnaCompraAjenaDa404() {
        when(repo.porIdBloqueando(1L)).thenReturn(Optional.of(compraPendiente(AHORA.plusMinutes(5))));

        ApiException e = assertThrows(ApiException.class, () -> service.pagar(999L, 1L, "TARJETA"));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void pagarUnaCompraVencidaNoCobra() {
        when(repo.porIdBloqueando(1L)).thenReturn(Optional.of(compraPendiente(AHORA.minusMinutes(1))));
        when(repo.cambiarEstadoDesdePendiente(1L, "VENCIDA")).thenReturn(true);
        when(repo.detalles(1L)).thenReturn(List.of());
        when(repo.porId(1L)).thenReturn(Optional.of(compraPendiente(AHORA.minusMinutes(1))));

        var resultado = service.pagar(55L, 1L, "TARJETA");

        assertEquals(Resultado.VENCIDA, resultado.resultado());
        verify(pagos, never()).cobrar(any());
        verify(repo).liberarAsientos(1L);
    }

    @Test
    void siPagosYaTieneUnPagoAprobadoNoSeVuelveACobrar() {
        when(repo.porIdBloqueando(1L)).thenReturn(Optional.of(compraPendiente(AHORA.plusMinutes(5))));
        when(pagos.obtenerPorCompra(1L)).thenReturn(Optional.of(new PagoInfo(3L, 1L, "APROBADO")));
        when(repo.detalles(1L)).thenReturn(List.of());
        when(repo.porId(1L)).thenReturn(Optional.of(compraPendiente(AHORA.plusMinutes(5))));

        var resultado = service.pagar(55L, 1L, "TARJETA");

        assertEquals(Resultado.PAGADA, resultado.resultado());
        verify(pagos, never()).cobrar(any());
        verify(repo).marcarPagada(1L, "TARJETA");
    }

    @Test
    void unPagoRechazadoLiberaLosLugaresYMarcaLaCompraRechazada() {
        when(repo.porIdBloqueando(1L)).thenReturn(Optional.of(compraPendiente(AHORA.plusMinutes(5))));
        when(pagos.obtenerPorCompra(1L)).thenReturn(Optional.empty());
        when(pagos.cobrar(any())).thenReturn(new PagoInfo(3L, 1L, "RECHAZADO"));
        when(repo.cambiarEstadoDesdePendiente(1L, "RECHAZADA")).thenReturn(true);
        when(repo.detalles(1L)).thenReturn(List.of());
        when(repo.porId(1L)).thenReturn(Optional.of(compraPendiente(AHORA.plusMinutes(5))));

        var resultado = service.pagar(55L, 1L, "TARJETA");

        assertEquals(Resultado.RECHAZADA, resultado.resultado());
        verify(repo).liberarAsientos(1L);
        verify(repo, never()).venderAsientos(anyLong());
    }
}

package eventos.service;

import eventos.Domains.ApiException;
import eventos.Domains.Evento;
import eventos.Domains.EventoSector;
import eventos.api.Dtos;
import eventos.client.LugaresClient;
import eventos.client.LugaresClient.SectorLugar;
import eventos.client.VentasClient;
import eventos.client.VentasClient.SectorDisponibilidad;
import eventos.repository.EventoRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventoServiceTest {

    private final EventoRepository repo = mock(EventoRepository.class);
    private final LugaresClient lugares = mock(LugaresClient.class);
    private final VentasClient ventas = mock(VentasClient.class);
    private final EventoService service = new EventoService(repo, lugares, ventas);

    private static final EventoSector SECTOR_12 = new EventoSector(12L, true, new BigDecimal("30000"));
    private static final EventoSector ZONA_13 = new EventoSector(13L, false, new BigDecimal("15000"));

    private Evento evento(String estado, List<EventoSector> sectores) {
        return new Evento(1L, "Recital", "desc", "RECITAL",
                LocalDateTime.of(2026, 12, 1, 21, 0), estado, 1L, "Tandil", 1L, 6, null,
                sectores, List.of(), null);
    }

    @Test
    void crear_sin_nombre_tira_400() {
        var req = new Dtos.CrearEventoRequest(" ", "d", "RECITAL",
                LocalDateTime.now(), 1L, "Tandil", 1L, 6);
        ApiException ex = assertThrows(ApiException.class, () -> service.crear(req));
        assertEquals(400, ex.status().value());
    }

    @Test
    void publicar_sin_sectores_tira_409_y_no_llama_a_ventas() {
        when(repo.buscarPorId(1L)).thenReturn(Optional.of(evento("BORRADOR", List.of())));
        ApiException ex = assertThrows(ApiException.class, () -> service.publicar(1L));
        assertEquals(409, ex.status().value());
        assertEquals("SIN_SECTORES", ex.codigo());
        verifyNoInteractions(ventas);
        verify(repo, never()).publicar(anyLong());
    }

    @Test
    void publicar_prepara_la_disponibilidad_en_ventas_y_despues_publica() {
        when(repo.buscarPorId(1L)).thenReturn(Optional.of(evento("BORRADOR", List.of(SECTOR_12, ZONA_13))));
        when(lugares.sectores(1L)).thenReturn(List.of(new SectorLugar(12L, true, 50), new SectorLugar(13L, false, 1000)));
        when(repo.publicar(1L)).thenReturn(true);

        service.publicar(1L);

        // el numerado va sin capacidad (ventas le pide los asientos a lugares); la zona lleva la de lugares
        verify(ventas).prepararDisponibilidad(1L, List.of(
                new SectorDisponibilidad(12L, true, null),
                new SectorDisponibilidad(13L, false, 1000)));
        verify(repo).publicar(1L);
    }

    @Test
    void si_ventas_no_responde_el_evento_no_se_publica() {
        when(repo.buscarPorId(1L)).thenReturn(Optional.of(evento("BORRADOR", List.of(SECTOR_12))));
        when(lugares.sectores(1L)).thenReturn(List.of(new SectorLugar(12L, true, 50)));
        doThrow(ApiException.unavailable("VENTAS_NO_DISPONIBLE", "caido"))
                .when(ventas).prepararDisponibilidad(anyLong(), anyList());

        ApiException ex = assertThrows(ApiException.class, () -> service.publicar(1L));

        assertEquals(503, ex.status().value());
        verify(repo, never()).publicar(anyLong());
    }

    @Test
    void un_sector_que_no_es_del_lugar_no_deja_publicar() {
        when(repo.buscarPorId(1L)).thenReturn(Optional.of(evento("BORRADOR", List.of(SECTOR_12))));
        when(lugares.sectores(1L)).thenReturn(List.of(new SectorLugar(99L, true, 50)));

        ApiException ex = assertThrows(ApiException.class, () -> service.publicar(1L));

        assertEquals("SECTOR_FUERA_DEL_LUGAR", ex.codigo());
        verifyNoInteractions(ventas);
    }

    @Test
    void cancelar_dos_veces_tira_409() {
        when(repo.buscarPorId(1L)).thenReturn(Optional.of(evento("CANCELADO", List.of(SECTOR_12))));
        ApiException ex = assertThrows(ApiException.class, () -> service.cancelar(1L, "motivo"));
        assertEquals(409, ex.status().value());
        verifyNoInteractions(ventas);
    }

    @Test
    void cancelar_avisa_a_ventas_y_marca_el_aviso() {
        when(repo.buscarPorId(1L)).thenReturn(Optional.of(evento("PUBLICADO", List.of(SECTOR_12))));
        when(repo.cancelar(1L, "lluvia")).thenReturn(true);

        Dtos.CancelacionView view = service.cancelar(1L, "lluvia");

        assertEquals("PROCESADAS", view.devoluciones());
        verify(ventas).avisarCancelacion(1L);
        verify(repo).marcarCancelacionNotificada(1L);
    }

    @Test
    void si_ventas_esta_caido_la_cancelacion_queda_pendiente() {
        when(repo.buscarPorId(1L)).thenReturn(Optional.of(evento("PUBLICADO", List.of(SECTOR_12))));
        when(repo.cancelar(1L, "lluvia")).thenReturn(true);
        doThrow(ApiException.unavailable("VENTAS_NO_DISPONIBLE", "caido")).when(ventas).avisarCancelacion(1L);

        Dtos.CancelacionView view = service.cancelar(1L, "lluvia");

        assertEquals("CANCELADO", view.estado());
        assertEquals("PENDIENTES", view.devoluciones());
        verify(repo, never()).marcarCancelacionNotificada(anyLong());
    }

    @Test
    void sector_sin_precio_tira_400_y_no_guarda_nada() {
        when(repo.buscarPorId(1L)).thenReturn(Optional.of(evento("BORRADOR", List.of())));
        var sectores = List.of(
                new Dtos.SectorPrecioRequest(12L, true, new BigDecimal("30000")),
                new Dtos.SectorPrecioRequest(13L, false, null));
        ApiException ex = assertThrows(ApiException.class, () -> service.configurarSectores(1L, sectores));
        assertEquals(400, ex.status().value());
        verify(repo, never()).guardarSector(anyLong(), any());
    }

    @Test
    void obtenerInfo_inexistente_tira_404() {
        when(repo.buscarPorId(99L)).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class, () -> service.obtenerInfo(99L));
        assertEquals(404, ex.status().value());
    }

    @Test
    void obtenerInfo_arma_el_contrato_de_ventas_desde_el_documento() {
        when(repo.buscarPorId(1L)).thenReturn(Optional.of(evento("PUBLICADO", List.of(SECTOR_12))));
        Dtos.EventoInfoView info = service.obtenerInfo(1L);
        assertEquals("PUBLICADO", info.estado());
        assertEquals(1, info.sectores().size());
        assertEquals(12L, info.sectores().get(0).sectorId());
        assertTrue(info.sectores().get(0).numerado());
        assertEquals(0, new BigDecimal("30000").compareTo(info.sectores().get(0).precio()));
        assertEquals(1L, info.organizadorId());
    }
}

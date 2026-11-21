package eventos.service;

import eventos.Domains.ApiException;
import eventos.Domains.Evento;
import eventos.Domains.EventoSector;
import eventos.api.Dtos;
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
    private final EventoService service = new EventoService(repo);

    private static final EventoSector SECTOR_12 = new EventoSector(12L, true, new BigDecimal("30000"));

    private Evento evento(String estado, List<EventoSector> sectores) {
        return new Evento(1L, "Recital", "desc", "RECITAL",
                LocalDateTime.of(2026, 12, 1, 21, 0), estado, 1L, "Tandil", 1L, 6, null,
                sectores, List.of());
    }

    @Test
    void crear_sin_nombre_tira_400() {
        var req = new Dtos.CrearEventoRequest(" ", "d", "RECITAL",
                LocalDateTime.now(), 1L, "Tandil", 1L, 6);
        ApiException ex = assertThrows(ApiException.class, () -> service.crear(req));
        assertEquals(400, ex.status().value());
    }

    @Test
    void publicar_sin_sectores_tira_409() {
        when(repo.buscarPorId(1L)).thenReturn(Optional.of(evento("BORRADOR", List.of())));
        ApiException ex = assertThrows(ApiException.class, () -> service.publicar(1L));
        assertEquals(409, ex.status().value());
        assertEquals("SIN_SECTORES", ex.codigo());
        verify(repo, never()).cambiarEstado(anyLong(), anyString());
    }

    @Test
    void publicar_ok_cambia_estado_a_publicado() {
        when(repo.buscarPorId(1L)).thenReturn(Optional.of(evento("BORRADOR", List.of(SECTOR_12))));
        service.publicar(1L);
        verify(repo).cambiarEstado(1L, "PUBLICADO");
    }

    @Test
    void cancelar_dos_veces_tira_409() {
        when(repo.buscarPorId(1L)).thenReturn(Optional.of(evento("CANCELADO", List.of(SECTOR_12))));
        ApiException ex = assertThrows(ApiException.class, () -> service.cancelar(1L, "motivo"));
        assertEquals(409, ex.status().value());
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
    }
}

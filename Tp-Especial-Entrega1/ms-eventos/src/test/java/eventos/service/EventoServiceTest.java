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

    private Evento evento(String estado) {
        return new Evento(1L, "Recital", "desc", "RECITAL",
                LocalDateTime.of(2026, 12, 1, 21, 0), estado, 1L, "Tandil", 1L, 6, null);
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
        when(repo.buscarPorId(1L)).thenReturn(Optional.of(evento("BORRADOR")));
        when(repo.tieneSectores(1L)).thenReturn(false);
        ApiException ex = assertThrows(ApiException.class, () -> service.publicar(1L));
        assertEquals(409, ex.status().value());
        assertEquals("SIN_SECTORES", ex.codigo());
    }

    @Test
    void publicar_ok_cambia_estado_a_publicado() {
        when(repo.buscarPorId(1L)).thenReturn(Optional.of(evento("BORRADOR")));
        when(repo.tieneSectores(1L)).thenReturn(true);
        service.publicar(1L);
        verify(repo).cambiarEstado(1L, "PUBLICADO");
    }

    @Test
    void cancelar_dos_veces_tira_409() {
        when(repo.buscarPorId(1L)).thenReturn(Optional.of(evento("CANCELADO")));
        ApiException ex = assertThrows(ApiException.class, () -> service.cancelar(1L, "motivo"));
        assertEquals(409, ex.status().value());
    }

    @Test
    void obtenerInfo_inexistente_tira_404() {
        when(repo.buscarPorId(99L)).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class, () -> service.obtenerInfo(99L));
        assertEquals(404, ex.status().value());
    }

    @Test
    void obtenerInfo_arma_el_contrato_de_ventas() {
        when(repo.buscarPorId(1L)).thenReturn(Optional.of(evento("PUBLICADO")));
        when(repo.sectoresDe(1L)).thenReturn(List.of(
                new EventoSector(1L, 12L, true, new BigDecimal("30000"))));
        Dtos.EventoInfoView info = service.obtenerInfo(1L);
        assertEquals("PUBLICADO", info.estado());
        assertEquals(1, info.sectores().size());
        assertEquals(12L, info.sectores().get(0).sectorId());
        assertTrue(info.sectores().get(0).numerado());
    }
}

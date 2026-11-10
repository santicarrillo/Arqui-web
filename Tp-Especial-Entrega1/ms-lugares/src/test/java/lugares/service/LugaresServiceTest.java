package lugares.service;

import lugares.Domains.ApiException;
import lugares.Domains.Asiento;
import lugares.Domains.Lugar;
import lugares.Domains.Sector;
import lugares.api.Dtos;
import lugares.repository.LugaresRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LugaresServiceTest {

    private final LugaresRepository repo = mock(LugaresRepository.class);
    private final LugaresService service = new LugaresService(repo);

    private Lugar lugarEjemplo(Long id) {
        return new Lugar(id, "Estadio Uno", "Calle 1 y 57", -34.91, -57.93, "La Plata");
    }

    // =========================================================================
    // Pruebas de Lugares
    // =========================================================================

    @Test
    void crearLugar_datosValidos_guardaYRetornaId() {
        var req = new Dtos.CrearLugarRequest("Estadio Uno", "Calle 1 y 57", -34.91, -57.93, "La Plata");
        when(repo.guardarLugar("Estadio Uno", "Calle 1 y 57", -34.91, -57.93, "La Plata")).thenReturn(10L);

        Long id = service.crearLugar(req);

        assertEquals(10L, id);
        verify(repo).guardarLugar("Estadio Uno", "Calle 1 y 57", -34.91, -57.93, "La Plata");
    }

    @Test
    void crearLugar_nombreVacio_tira400() {
        var req = new Dtos.CrearLugarRequest("   ", "Calle 1 y 57", -34.91, -57.93, "La Plata");

        ApiException ex = assertThrows(ApiException.class, () -> service.crearLugar(req));
        assertEquals(400, ex.status().value());
        assertEquals("DATOS_INVALIDOS", ex.codigo());
    }

    @Test
    void crearLugar_faltaCoordenadas_tira400() {
        var req = new Dtos.CrearLugarRequest("Estadio", "Direccion", null, -57.93, "La Plata");

        ApiException ex = assertThrows(ApiException.class, () -> service.crearLugar(req));
        assertEquals(400, ex.status().value());
    }

    @Test
    void obtenerLugar_existente_devuelveLugarView() {
        when(repo.buscarLugarPorId(1L)).thenReturn(Optional.of(lugarEjemplo(1L)));

        Dtos.LugarView view = service.obtenerLugarPorId(1L);

        assertNotNull(view);
        assertEquals(1L, view.id());
        assertEquals("Estadio Uno", view.nombre());
        assertEquals("La Plata", view.ciudad());
    }

    @Test
    void obtenerLugar_inexistente_tira404() {
        when(repo.buscarLugarPorId(99L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.obtenerLugarPorId(99L));
        assertEquals(404, ex.status().value());
        assertEquals("LUGAR_NO_ENCONTRADO", ex.codigo());
    }

    // =========================================================================
    // Pruebas de Sectores
    // =========================================================================

    @Test
    void crearSector_lugarInexistente_tira404() {
        when(repo.buscarLugarPorId(99L)).thenReturn(Optional.empty());
        var req = new Dtos.CrearSectorRequest("Campo", false, 5000, null, null);

        ApiException ex = assertThrows(ApiException.class, () -> service.crearSector(99L, req));
        assertEquals(404, ex.status().value());
    }

    @Test
    void crearSector_zonaNoNumerada_guardaConCapacidadIndicada() {
        when(repo.buscarLugarPorId(1L)).thenReturn(Optional.of(lugarEjemplo(1L)));
        when(repo.guardarSector(1L, "Campo General", false, 5000)).thenReturn(20L);

        var req = new Dtos.CrearSectorRequest("Campo General", false, 5000, null, null);
        Long sectorId = service.crearSector(1L, req);

        assertEquals(20L, sectorId);
        verify(repo).guardarSector(1L, "Campo General", false, 5000);
        verify(repo, never()).guardarAsientosBatch(anyList());
    }

    @Test
    void crearSector_zonaNoNumerada_capacidadInvalida_tira400() {
        when(repo.buscarLugarPorId(1L)).thenReturn(Optional.of(lugarEjemplo(1L)));
        var req = new Dtos.CrearSectorRequest("Campo General", false, 0, null, null);

        ApiException ex = assertThrows(ApiException.class, () -> service.crearSector(1L, req));
        assertEquals(400, ex.status().value());
    }

    @Test
    void crearSector_numerado_generaMatrizDeAsientosYGuardaBatch() {
        when(repo.buscarLugarPorId(1L)).thenReturn(Optional.of(lugarEjemplo(1L)));
        // 3 filas x 4 asientos = capacidad 12
        when(repo.guardarSector(1L, "Platea Baja", true, 12)).thenReturn(50L);

        var req = new Dtos.CrearSectorRequest("Platea Baja", true, null, 3, 4);
        Long sectorId = service.crearSector(1L, req);

        assertEquals(50L, sectorId);
        verify(repo).guardarSector(1L, "Platea Baja", true, 12);

        // Verificamos que se haya llamado a batchUpdate con exactamente 12 asientos
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Asiento>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo).guardarAsientosBatch(captor.capture());

        List<Asiento> asientosGenerados = captor.getValue();
        assertEquals(12, asientosGenerados.size());

        // Verificamos el primer asiento (Fila 1, Asiento 1) y el ultimo (Fila 3, Asiento 4)
        assertEquals("1", asientosGenerados.get(0).fila());
        assertEquals("1", asientosGenerados.get(0).numero());
        assertEquals(50L, asientosGenerados.get(0).sectorId());

        assertEquals("3", asientosGenerados.get(11).fila());
        assertEquals("4", asientosGenerados.get(11).numero());
        assertEquals(50L, asientosGenerados.get(11).sectorId());
    }

    // =========================================================================
    // Pruebas de Consulta de Asientos (Contrato para ms-ventas)
    // =========================================================================

    @Test
    void listarAsientosPorSector_existente_devuelveListaViews() {
        var sector = new Sector(50L, 1L, "Platea", true, 2);
        when(repo.buscarSectorPorId(50L)).thenReturn(Optional.of(sector));
        when(repo.listarAsientosPorSector(50L)).thenReturn(List.of(
                new Asiento(100L, 50L, "1", "1"),
                new Asiento(101L, 50L, "1", "2")
        ));

        List<Dtos.AsientoView> asientos = service.listarAsientosPorSector(50L);

        assertEquals(2, asientos.size());
        assertEquals(100L, asientos.get(0).id());
        assertEquals("1", asientos.get(0).fila());
        assertEquals("1", asientos.get(0).numero());
    }

    @Test
    void listarAsientosPorSector_sectorInexistente_tira404() {
        when(repo.buscarSectorPorId(999L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.listarAsientosPorSector(999L));
        assertEquals(404, ex.status().value());
        assertEquals("SECTOR_NO_ENCONTRADO", ex.codigo());
    }
}

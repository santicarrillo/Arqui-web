package usuarios.service;

import org.junit.jupiter.api.Test;
import usuarios.Domains.ApiException;
import usuarios.Domains.Usuario;
import usuarios.api.Dtos;
import usuarios.repository.UsuarioRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsuarioServiceTest {

    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final UsuarioService service = new UsuarioService(repo);

    @Test
    void crearSinNombreDa400() {
        var req = new Dtos.CrearUsuarioRequest("", "a@a.com", "CLIENTE");
        ApiException e = assertThrows(ApiException.class, () -> service.crear(req));
        assertEquals(400, e.status().value());
    }

    @Test
    void crearConRolInvalidoDa400() {
        var req = new Dtos.CrearUsuarioRequest("Ana", "ana@a.com", "SUPERADMIN");
        ApiException e = assertThrows(ApiException.class, () -> service.crear(req));
        assertEquals(400, e.status().value());
    }

    @Test
    void crearConEmailDuplicadoDa409() {
        when(repo.existeEmail("ana@a.com")).thenReturn(true);
        var req = new Dtos.CrearUsuarioRequest("Ana", "ana@a.com", "CLIENTE");
        ApiException e = assertThrows(ApiException.class, () -> service.crear(req));
        assertEquals(409, e.status().value());
    }

    @Test
    void crearOkDevuelveId() {
        when(repo.existeEmail("ana@a.com")).thenReturn(false);
        when(repo.crear(any())).thenReturn(7L);
        var req = new Dtos.CrearUsuarioRequest("Ana", "ana@a.com", "ORGANIZADOR");
        assertEquals(7L, service.crear(req));
        verify(repo).crear(any());
    }

    @Test
    void obtenerInexistenteDa404() {
        when(repo.buscarPorId(99)).thenReturn(Optional.empty());
        ApiException e = assertThrows(ApiException.class, () -> service.obtener(99));
        assertEquals(404, e.status().value());
    }

    @Test
    void obtenerExistenteDevuelveVista() {
        when(repo.buscarPorId(1)).thenReturn(Optional.of(new Usuario(1L, "Ana", "ana@a.com", "CLIENTE")));
        Dtos.UsuarioView v = service.obtener(1);
        assertEquals("Ana", v.nombre());
        assertEquals("CLIENTE", v.rol());
    }
}

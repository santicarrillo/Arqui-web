package usuarios.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import usuarios.Domains.ApiException;
import usuarios.Domains.Credencial;
import usuarios.Domains.Usuario;
import usuarios.api.Dtos;
import usuarios.repository.UsuarioRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsuarioServiceTest {

    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UsuarioService service = new UsuarioService(repo, encoder);

    @Test
    void crearSinNombreDa400() {
        var req = new Dtos.CrearUsuarioRequest("", "a@a.com", "CLIENTE", "secreta");
        ApiException e = assertThrows(ApiException.class, () -> service.crear(req));
        assertEquals(400, e.status().value());
    }

    @Test
    void crearConRolInvalidoDa400() {
        var req = new Dtos.CrearUsuarioRequest("Ana", "ana@a.com", "SUPERADMIN", "secreta");
        ApiException e = assertThrows(ApiException.class, () -> service.crear(req));
        assertEquals(400, e.status().value());
    }

    @Test
    void crearSinPasswordDa400() {
        var req = new Dtos.CrearUsuarioRequest("Ana", "ana@a.com", "CLIENTE", "");
        ApiException e = assertThrows(ApiException.class, () -> service.crear(req));
        assertEquals(400, e.status().value());
    }

    @Test
    void crearConEmailDuplicadoDa409() {
        when(repo.existeEmail("ana@a.com")).thenReturn(true);
        var req = new Dtos.CrearUsuarioRequest("Ana", "ana@a.com", "CLIENTE", "secreta");
        ApiException e = assertThrows(ApiException.class, () -> service.crear(req));
        assertEquals(409, e.status().value());
    }

    @Test
    void crearOkDevuelveIdYGuardaHash() {
        when(repo.existeEmail("ana@a.com")).thenReturn(false);
        when(repo.crear(any(), any())).thenReturn(7L);
        var req = new Dtos.CrearUsuarioRequest("Ana", "ana@a.com", "ORGANIZADOR", "secreta");
        assertEquals(7L, service.crear(req));
        // el hash que se guarda NO es la password en texto plano
        verify(repo).crear(eq(req), argThat(h -> h != null && !h.equals("secreta")));
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

    @Test
    void loginEmailInexistenteDa401() {
        when(repo.buscarCredencialPorEmail("no@a.com")).thenReturn(Optional.empty());
        ApiException e = assertThrows(ApiException.class, () -> service.validarCredenciales("no@a.com", "x"));
        assertEquals(401, e.status().value());
    }

    @Test
    void loginPasswordIncorrectaDa401() {
        var cred = new Credencial(1L, "CLIENTE", encoder.encode("correcta"));
        when(repo.buscarCredencialPorEmail("ana@a.com")).thenReturn(Optional.of(cred));
        ApiException e = assertThrows(ApiException.class, () -> service.validarCredenciales("ana@a.com", "incorrecta"));
        assertEquals(401, e.status().value());
    }

    @Test
    void loginOkDevuelveCredencial() {
        var cred = new Credencial(1L, "ADMINISTRADOR", encoder.encode("correcta"));
        when(repo.buscarCredencialPorEmail("ana@a.com")).thenReturn(Optional.of(cred));
        Credencial out = service.validarCredenciales("ana@a.com", "correcta");
        assertEquals(1L, out.id());
        assertEquals("ADMINISTRADOR", out.rol());
    }
}

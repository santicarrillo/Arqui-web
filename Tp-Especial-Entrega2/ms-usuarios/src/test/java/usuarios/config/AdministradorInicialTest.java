package usuarios.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import usuarios.api.Dtos;
import usuarios.repository.UsuarioRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F1: el administrador inicial se crea una sola vez, con la clave hasheada. */
class AdministradorInicialTest {

    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void siNoExisteLoCreaComoAdministradorConLaClaveHasheada() {
        when(repo.existeEmail("admin@a.com")).thenReturn(false);

        new AdministradorInicial(repo, encoder, "admin@a.com", "1234").run(null);

        ArgumentCaptor<Dtos.CrearUsuarioRequest> alta = ArgumentCaptor.forClass(Dtos.CrearUsuarioRequest.class);
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repo).crear(alta.capture(), hash.capture());
        assertEquals("Admin", alta.getValue().nombre());
        assertEquals("admin@a.com", alta.getValue().email());
        assertEquals("ADMINISTRADOR", alta.getValue().rol());
        assertNotEquals("1234", hash.getValue(), "no se guarda la clave en texto plano");
        assertTrue(encoder.matches("1234", hash.getValue()));
    }

    @Test
    void siYaExisteNoHaceNada() {
        when(repo.existeEmail("admin@a.com")).thenReturn(true);

        new AdministradorInicial(repo, encoder, "admin@a.com", "1234").run(null);

        verify(repo, never()).crear(any(), any());
    }
}

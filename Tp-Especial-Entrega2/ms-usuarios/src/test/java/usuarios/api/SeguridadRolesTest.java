package usuarios.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import usuarios.Domains.Usuario;
import usuarios.repository.UsuarioRepository;
import usuarios.service.UsuarioService;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F1: autorizacion por rol en ms-usuarios, en la capa HTTP (MockMvc, sin base de datos).
 * Los encabezados X-Usuario-Id / X-Rol son los que pone el gateway a partir del JWT; en el registro
 * publico no llegan.
 */
class SeguridadRolesTest {

    private UsuarioRepository repo;
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        repo = mock(UsuarioRepository.class);
        UsuarioService service = new UsuarioService(repo, new BCryptPasswordEncoder());
        mvc = MockMvcBuilders.standaloneSetup(new UsuarioController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private static String alta(String rol) {
        return """
                {"nombre":"Ana","email":"ana@a.com","rol":"%s","password":"1234"}
                """.formatted(rol);
    }

    // ---------------------------------------------------------------- POST /usuarios

    @Test
    void registroPublicoComoClienteDa201() throws Exception {
        when(repo.crear(any(), any())).thenReturn(5L);

        mvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON).content(alta("CLIENTE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "ORGANIZADOR", "ACCESO"})
    void registroPublicoConOtroRolDa403(String rol) throws Exception {
        mvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON).content(alta(rol)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_ADMIN_CREA_ROLES"));

        verifyNoInteractions(repo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENTE", "ORGANIZADOR", "ACCESO"})
    void unUsuarioQueNoEsAdminNoCreaRolesRestringidos(String rolSolicitante) throws Exception {
        mvc.perform(post("/usuarios").header("X-Usuario-Id", 9).header("X-Rol", rolSolicitante)
                        .contentType(MediaType.APPLICATION_JSON).content(alta("ORGANIZADOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_ADMIN_CREA_ROLES"));

        verifyNoInteractions(repo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORGANIZADOR", "ACCESO", "ADMINISTRADOR"})
    void elAdminCreaOtrosRoles(String rol) throws Exception {
        when(repo.crear(any(), any())).thenReturn(6L);

        mvc.perform(post("/usuarios").header("X-Usuario-Id", 1).header("X-Rol", "ADMINISTRADOR")
                        .contentType(MediaType.APPLICATION_JSON).content(alta(rol)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(6));
    }

    @Test
    void unRolInexistenteSigueDando400() throws Exception {
        mvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON).content(alta("SUPERADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ROL_INVALIDO"));
    }

    // ---------------------------------------------------------------- GET /usuarios

    @ParameterizedTest
    @ValueSource(strings = {"CLIENTE", "ORGANIZADOR", "ACCESO"})
    void listarUsuariosRechazaRolesNoAdminCon403(String rol) throws Exception {
        mvc.perform(get("/usuarios").header("X-Usuario-Id", 9).header("X-Rol", rol))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_ADMIN"));

        verifyNoInteractions(repo);
    }

    @Test
    void listarUsuariosSinEncabezadosDa403() throws Exception {
        mvc.perform(get("/usuarios")).andExpect(status().isForbidden());
        verifyNoInteractions(repo);
    }

    @Test
    void listarUsuariosPermiteAdministrador() throws Exception {
        when(repo.listar("CLIENTE")).thenReturn(List.of(new Usuario(5L, "Ana", "ana@a.com", "CLIENTE")));

        mvc.perform(get("/usuarios").param("rol", "CLIENTE").header("X-Usuario-Id", 1).header("X-Rol", "ADMINISTRADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("ana@a.com"));

        verify(repo).listar("CLIENTE");
    }

    // ---------------------------------------------------------------- GET /usuarios/{id}

    @Test
    void cadaUsuarioVeSusPropiosDatos() throws Exception {
        when(repo.buscarPorId(5L)).thenReturn(Optional.of(new Usuario(5L, "Ana", "ana@a.com", "CLIENTE")));

        mvc.perform(get("/usuarios/5").header("X-Usuario-Id", 5).header("X-Rol", "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENTE", "ORGANIZADOR", "ACCESO"})
    void verOtroUsuarioDa403(String rol) throws Exception {
        mvc.perform(get("/usuarios/5").header("X-Usuario-Id", 9).header("X-Rol", rol))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NO_ES_TU_USUARIO"));

        verifyNoInteractions(repo);
    }

    @Test
    void elAdminVeCualquierUsuario() throws Exception {
        when(repo.buscarPorId(5L)).thenReturn(Optional.of(new Usuario(5L, "Ana", "ana@a.com", "CLIENTE")));

        mvc.perform(get("/usuarios/5").header("X-Usuario-Id", 1).header("X-Rol", "ADMINISTRADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@a.com"));
    }
}

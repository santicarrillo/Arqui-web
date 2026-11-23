package lugares.api;

import lugares.Domains.Lugar;
import lugares.Domains.Sector;
import lugares.repository.LugaresRepository;
import lugares.service.LugaresService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F1: autorizacion por rol en ms-lugares, en la capa HTTP (MockMvc, sin base de datos). */
class SeguridadRolesTest {

    private static final String LUGAR = """
            {"nombre":"Estadio","direccion":"Av. 1","latitud":-37.3,"longitud":-59.1,"ciudad":"Tandil"}
            """;
    private static final String SECTOR = """
            {"nombre":"Campo","numerado":false,"capacidad":100}
            """;

    private LugaresRepository repo;
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        repo = mock(LugaresRepository.class);
        LugaresService service = new LugaresService(repo);
        mvc = MockMvcBuilders.standaloneSetup(new LugaresController(service), new SectoresController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENTE", "ACCESO"})
    void crearLugarRechazaClienteYAccesoCon403(String rol) throws Exception {
        mvc.perform(post("/lugares").header("X-Rol", rol).contentType(MediaType.APPLICATION_JSON).content(LUGAR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_ADMIN_U_ORGANIZADOR"));

        verifyNoInteractions(repo);
    }

    @Test
    void crearLugarSinRolDa403() throws Exception {
        mvc.perform(post("/lugares").contentType(MediaType.APPLICATION_JSON).content(LUGAR))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "ORGANIZADOR"})
    void crearLugarPermiteAdminYOrganizador(String rol) throws Exception {
        when(repo.guardarLugar(anyString(), anyString(), anyDouble(), anyDouble(), anyString())).thenReturn(3L);

        mvc.perform(post("/lugares").header("X-Rol", rol).contentType(MediaType.APPLICATION_JSON).content(LUGAR))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENTE", "ACCESO"})
    void crearSectorRechazaClienteYAccesoCon403(String rol) throws Exception {
        mvc.perform(post("/lugares/3/sectores").header("X-Rol", rol).contentType(MediaType.APPLICATION_JSON).content(SECTOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_ADMIN_U_ORGANIZADOR"));

        verifyNoInteractions(repo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "ORGANIZADOR"})
    void crearSectorPermiteAdminYOrganizador(String rol) throws Exception {
        when(repo.buscarLugarPorId(3L)).thenReturn(Optional.of(new Lugar(3L, "Estadio", "Av. 1", -37.3, -59.1, "Tandil")));
        when(repo.guardarSector(anyLong(), anyString(), anyBoolean(), anyInt())).thenReturn(8L);

        mvc.perform(post("/lugares/3/sectores").header("X-Rol", rol).contentType(MediaType.APPLICATION_JSON).content(SECTOR))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(8));
    }

    @Test
    void lasConsultasQuedanAbiertasSinRol() throws Exception {
        when(repo.buscarLugarPorId(3L)).thenReturn(Optional.of(new Lugar(3L, "Estadio", "Av. 1", -37.3, -59.1, "Tandil")));

        when(repo.buscarSectorPorId(8L)).thenReturn(Optional.of(new Sector(8L, 3L, "Platea", true, 10)));

        mvc.perform(get("/lugares/3")).andExpect(status().isOk());
        mvc.perform(get("/lugares/3/sectores")).andExpect(status().isOk());
        mvc.perform(get("/sectores/8/asientos")).andExpect(status().isOk());
    }
}

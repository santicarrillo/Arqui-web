package api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import preferencias.api.ApiExceptionHandler;
import preferencias.api.Dtos;
import preferencias.api.PreferenciasController;
import preferencias.service.PreferenciasService;
import preferencias.service.RecomendacionService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de autorizacion por rol en ms-preferencias (F13).
 * Verifica sin Docker que /preferencias (GET/PUT) y /recomendaciones solo acepten CLIENTE
 * y respondan 403 SOLO_CLIENTES a ORGANIZADOR, ADMINISTRADOR y ACCESO.
 */
class SeguridadRolesTest {

    private PreferenciasService preferenciasService;
    private RecomendacionService recomendacionService;
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        preferenciasService = mock(PreferenciasService.class);
        recomendacionService = mock(RecomendacionService.class);

        mvc = MockMvcBuilders.standaloneSetup(new PreferenciasController(preferenciasService, recomendacionService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORGANIZADOR", "ADMINISTRADOR", "ACCESO"})
    void getPreferenciasRechazaNoClientesCon403(String rol) throws Exception {
        mvc.perform(get("/preferencias")
                        .header("X-Usuario-Id", 1)
                        .header("X-Rol", rol))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_CLIENTES"));

        verifyNoInteractions(preferenciasService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORGANIZADOR", "ADMINISTRADOR", "ACCESO"})
    void putPreferenciasRechazaNoClientesCon403(String rol) throws Exception {
        mvc.perform(put("/preferencias")
                        .header("X-Usuario-Id", 1)
                        .header("X-Rol", rol)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tiposEvento\":[\"RECITAL\"],\"artistasFavoritos\":[\"Divididos\"],\"ciudad\":\"Tandil\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_CLIENTES"));

        verifyNoInteractions(preferenciasService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORGANIZADOR", "ADMINISTRADOR", "ACCESO"})
    void getRecomendacionesRechazaNoClientesCon403(String rol) throws Exception {
        mvc.perform(get("/recomendaciones")
                        .header("X-Usuario-Id", 1)
                        .header("X-Rol", rol))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_CLIENTES"));

        verifyNoInteractions(recomendacionService);
    }

    @Test
    void clientePuedeAccederAPreferenciasYRecomendaciones() throws Exception {
        Dtos.PerfilView perfil = new Dtos.PerfilView(
                5L, List.of("RECITAL"), List.of("Divididos"), "Tandil", List.of(), LocalDateTime.now());
        when(preferenciasService.obtener(5L)).thenReturn(perfil);
        when(preferenciasService.guardar(eq(5L), any())).thenReturn(perfil);
        when(recomendacionService.recomendar(5L, 10)).thenReturn(List.of());

        mvc.perform(get("/preferencias")
                        .header("X-Usuario-Id", 5)
                        .header("X-Rol", "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(5));

        mvc.perform(put("/preferencias")
                        .header("X-Usuario-Id", 5)
                        .header("X-Rol", "CLIENTE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tiposEvento\":[\"RECITAL\"],\"artistasFavoritos\":[\"Divididos\"],\"ciudad\":\"Tandil\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ciudad").value("Tandil"));

        mvc.perform(get("/recomendaciones")
                        .header("X-Usuario-Id", 5)
                        .header("X-Rol", "CLIENTE"))
                .andExpect(status().isOk());
    }
}

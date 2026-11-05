package integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.TimeZone;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = eventos.EventosApplication.class)
@AutoConfigureMockMvc
@Testcontainers
class EventoIntegrationTest {

    static {
        // misma razon que en el main: evitar el error de TimeZone con Postgres
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
    }

    @Container
    static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", db::getJdbcUrl);
        r.add("spring.datasource.username", db::getUsername);
        r.add("spring.datasource.password", db::getPassword);
    }

    @Autowired
    MockMvc mvc;

    private long crearEvento(String tipo, String ciudad) throws Exception {
        String body = mvc.perform(post("/eventos").contentType("application/json").content("""
                {"nombre":"Evento","tipo":"%s","fechaHora":"2026-12-01T21:00:00","lugarId":1,"ciudad":"%s","organizadorId":1,"limitePorCliente":6}
                """.formatted(tipo, ciudad)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void flujo_crear_publicar_y_contrato() throws Exception {
        long id = crearEvento("RECITAL", "Tandil");

        mvc.perform(post("/eventos/" + id + "/sectores").contentType("application/json").content("""
                [{"sectorId":12,"numerado":true,"precio":30000}]
                """)).andExpect(status().isOk());

        mvc.perform(put("/eventos/" + id + "/publicacion")).andExpect(status().isOk());

        mvc.perform(get("/eventos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.fechaHora").value("2026-12-01T21:00:00"))
                .andExpect(jsonPath("$.sectores[0].sectorId").value(12))
                .andExpect(jsonPath("$.sectores[0].numerado").value(true));
    }

    @Test
    void publicar_sin_sectores_da_409() throws Exception {
        long id = crearEvento("TEATRO", "Azul");
        mvc.perform(put("/eventos/" + id + "/publicacion")).andExpect(status().isConflict());
    }

    @Test
    void busqueda_filtra_por_tipo() throws Exception {
        long id = crearEvento("DEPORTE", "Tandil");
        mvc.perform(post("/eventos/" + id + "/sectores").contentType("application/json").content("""
                [{"sectorId":1,"numerado":false,"precio":5000}]
                """)).andExpect(status().isOk());
        mvc.perform(put("/eventos/" + id + "/publicacion")).andExpect(status().isOk());

        // hay al menos un DEPORTE publicado
        mvc.perform(get("/eventos").param("tipo", "DEPORTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("DEPORTE"));
    }
}

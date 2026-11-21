package integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.TimeZone;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test de integracion con Testcontainers: levanta su propio MongoDB (sin usuario ni contrasena,
 * no usa el docker-compose).
 *
 * En maquinas sin Docker se SALTEA (disabledWithoutDocker = true).
 * Con Docker Engine 29+ hace falta un archivo ~/.docker-java.properties con api.version=1.44,
 * porque el engine ya no acepta versiones viejas de la API.
 */
@SpringBootTest(classes = eventos.EventosApplication.class)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class EventoIntegrationTest {

    static {
        // misma zona que en el main, para que las fechas se lean igual que como se guardaron
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
    }

    @Container
    static MongoDBContainer db = new MongoDBContainer("mongo:7.0.11");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.data.mongodb.uri", () -> db.getReplicaSetUrl("eventos"));
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

    private void cargarSectores(long id, String json) throws Exception {
        mvc.perform(post("/eventos/" + id + "/sectores").contentType("application/json").content(json))
                .andExpect(status().isOk());
    }

    private void publicar(long id) throws Exception {
        mvc.perform(put("/eventos/" + id + "/publicacion")).andExpect(status().isOk());
    }

    @Test
    void flujo_crear_publicar_y_contrato() throws Exception {
        long id = crearEvento("RECITAL", "Tandil");
        cargarSectores(id, """
                [{"sectorId":12,"numerado":true,"precio":30000}]
                """);
        publicar(id);

        // el contrato que consume ventas no cambio con la migracion
        mvc.perform(get("/eventos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.fechaHora").value("2026-12-01T21:00:00"))
                .andExpect(jsonPath("$.limitePorCliente").value(6))
                .andExpect(jsonPath("$.sectores[0].sectorId").value(12))
                .andExpect(jsonPath("$.sectores[0].numerado").value(true))
                .andExpect(jsonPath("$.sectores[0].precio").value(30000));
    }

    @Test
    void publicar_sin_sectores_da_409() throws Exception {
        long id = crearEvento("TEATRO", "Azul");
        mvc.perform(put("/eventos/" + id + "/publicacion")).andExpect(status().isConflict());
    }

    @Test
    void volver_a_cargar_un_sector_actualiza_el_precio_sin_duplicarlo() throws Exception {
        long id = crearEvento("RECITAL", "Olavarria");
        cargarSectores(id, """
                [{"sectorId":12,"numerado":true,"precio":30000}]
                """);
        cargarSectores(id, """
                [{"sectorId":12,"numerado":true,"precio":35000},
                 {"sectorId":13,"numerado":false,"precio":15000}]
                """);

        mvc.perform(get("/eventos/" + id))
                .andExpect(jsonPath("$.sectores.length()").value(2))
                .andExpect(jsonPath("$.sectores[?(@.sectorId == 12)].precio").value(35000));
    }

    @Test
    void busqueda_filtra_por_tipo() throws Exception {
        long id = crearEvento("DEPORTE", "Tandil");
        cargarSectores(id, """
                [{"sectorId":1,"numerado":false,"precio":5000}]
                """);
        publicar(id);

        mvc.perform(get("/eventos").param("tipo", "DEPORTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("DEPORTE"));
    }

    @Test
    void busqueda_por_participante_sin_importar_mayusculas() throws Exception {
        long id = crearEvento("RECITAL", "Tandil");
        mvc.perform(post("/eventos/" + id + "/participantes").contentType("application/json").content("""
                [{"nombre":"Los Piojos","tipo":"ARTISTA"}]
                """)).andExpect(status().isOk());
        cargarSectores(id, """
                [{"sectorId":1,"numerado":false,"precio":20000}]
                """);
        publicar(id);

        mvc.perform(get("/eventos").param("participante", "piojos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());
    }

    @Test
    void busqueda_ordenada_por_precio_pone_primero_el_mas_barato() throws Exception {
        long caro = crearEvento("CONFERENCIA", "Tandil");
        cargarSectores(caro, """
                [{"sectorId":1,"numerado":false,"precio":9000}]
                """);
        publicar(caro);

        long barato = crearEvento("CONFERENCIA", "Tandil");
        cargarSectores(barato, """
                [{"sectorId":1,"numerado":false,"precio":1000},
                 {"sectorId":2,"numerado":true,"precio":12000}]
                """);
        publicar(barato);

        mvc.perform(get("/eventos").param("tipo", "CONFERENCIA").param("orden", "precio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(barato))
                .andExpect(jsonPath("$[1].id").value(caro));
    }
}

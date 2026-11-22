package integration;

import com.jayway.jsonpath.JsonPath;
import eventos.Domains.ApiException;
import eventos.client.LugaresClient;
import eventos.client.NotificacionesClient;
import eventos.client.LugaresClient.SectorLugar;
import eventos.client.VentasClient;
import eventos.client.VentasClient.SectorDisponibilidad;
import eventos.repository.EventoRepository;
import eventos.service.EventoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test de integracion con Testcontainers: levanta su propio MongoDB (sin usuario ni contrasena,
 * no usa el docker-compose).
 *
 * En maquinas sin Docker se SALTEA (disabledWithoutDocker = true).
 * Con Docker Engine 29+ hace falta un archivo ~/.docker-java.properties con api.version=1.44,
 * porque el engine ya no acepta versiones viejas de la API.
 *
 * lugares-service y ventas-service se simulan (@MockBean): aca se prueba eventos.
 */
@SpringBootTest(classes = eventos.EventosApplication.class,
        properties = "eventos.reintento-cancelaciones-ms=3600000")
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

    @Autowired
    EventoService eventos;

    @MockBean
    LugaresClient lugares;

    @MockBean
    VentasClient ventas;

    @MockBean
    NotificacionesClient notificaciones;

    /** El repositorio real, espiado para contar cuantas veces se lee la base (F5). */
    @SpyBean
    EventoRepository repo;

    /** El lugar 1 tiene dos zonas y dos sectores numerados. */
    @BeforeEach
    void lugarConSectores() {
        when(lugares.sectores(1L)).thenReturn(List.of(
                new SectorLugar(1L, false, 500),
                new SectorLugar(2L, true, 100),
                new SectorLugar(12L, true, 50),
                new SectorLugar(13L, false, 1000)));
    }

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

    // ---------------------------------------------------------------- F12: sagas con ventas

    @Test
    void publicar_prepara_la_disponibilidad_en_ventas_con_la_capacidad_de_las_zonas() throws Exception {
        long id = crearEvento("RECITAL", "Tandil");
        cargarSectores(id, """
                [{"sectorId":12,"numerado":true,"precio":30000},
                 {"sectorId":13,"numerado":false,"precio":15000}]
                """);
        publicar(id);

        verify(ventas).prepararDisponibilidad(id, List.of(
                new SectorDisponibilidad(12L, true, null),
                new SectorDisponibilidad(13L, false, 1000)));
    }

    @Test
    void si_ventas_no_responde_el_evento_sigue_en_borrador() throws Exception {
        long id = crearEvento("RECITAL", "Tandil");
        cargarSectores(id, """
                [{"sectorId":13,"numerado":false,"precio":15000}]
                """);
        doThrow(ApiException.unavailable("VENTAS_NO_DISPONIBLE", "caido"))
                .when(ventas).prepararDisponibilidad(anyLong(), anyList());

        mvc.perform(put("/eventos/" + id + "/publicacion")).andExpect(status().isServiceUnavailable());

        mvc.perform(get("/eventos/" + id)).andExpect(jsonPath("$.estado").value("BORRADOR"));
    }

    @Test
    void un_sector_que_no_es_del_lugar_no_deja_publicar() throws Exception {
        long id = crearEvento("RECITAL", "Tandil");
        cargarSectores(id, """
                [{"sectorId":77,"numerado":false,"precio":15000}]
                """);

        mvc.perform(put("/eventos/" + id + "/publicacion"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SECTOR_FUERA_DEL_LUGAR"));
    }

    @Test
    void cancelar_avisa_a_ventas_para_que_procese_las_devoluciones() throws Exception {
        long id = crearEvento("RECITAL", "Tandil");
        cargarSectores(id, """
                [{"sectorId":13,"numerado":false,"precio":15000}]
                """);
        publicar(id);

        mvc.perform(put("/eventos/" + id + "/cancelacion").contentType("application/json")
                        .content("{\"motivo\":\"lluvia\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.devoluciones").value("PROCESADAS"));

        verify(ventas).avisarCancelacion(id);
    }

    @Test
    void si_ventas_esta_caido_la_cancelacion_se_reintenta_despues() throws Exception {
        long id = crearEvento("RECITAL", "Tandil");
        cargarSectores(id, """
                [{"sectorId":13,"numerado":false,"precio":15000}]
                """);
        publicar(id);
        // la primera vez ventas no responde, la segunda si
        doThrow(ApiException.unavailable("VENTAS_NO_DISPONIBLE", "caido")).doNothing()
                .when(ventas).avisarCancelacion(eq(id));

        mvc.perform(put("/eventos/" + id + "/cancelacion").contentType("application/json")
                        .content("{\"motivo\":\"lluvia\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devoluciones").value("PENDIENTES"));

        // lo que hace la tarea programada
        assertTrue(eventos.reintentarCancelacionesPendientes() >= 1);
        verify(ventas, times(2)).avisarCancelacion(id);
        // ya no queda pendiente
        eventos.reintentarCancelacionesPendientes();
        verify(ventas, times(2)).avisarCancelacion(id);
    }

    // ---------------------------------------------------------------- F5: cache

    @Test
    void el_detalle_se_lee_de_la_cache_la_segunda_vez() throws Exception {
        long id = crearEvento("TEATRO", "Tandil");
        clearInvocations(repo);

        mvc.perform(get("/eventos/" + id)).andExpect(status().isOk());
        mvc.perform(get("/eventos/" + id)).andExpect(status().isOk());

        verify(repo, times(1)).buscarPorId(id);
    }

    @Test
    void cambiar_el_evento_borra_su_detalle_de_la_cache() throws Exception {
        long id = crearEvento("TEATRO", "Tandil");
        mvc.perform(get("/eventos/" + id)).andExpect(jsonPath("$.sectores.length()").value(0));

        cargarSectores(id, """
                [{"sectorId":13,"numerado":false,"precio":15000}]
                """);
        mvc.perform(get("/eventos/" + id)).andExpect(jsonPath("$.sectores.length()").value(1));

        publicar(id);
        mvc.perform(get("/eventos/" + id)).andExpect(jsonPath("$.estado").value("PUBLICADO"));
    }

    @Test
    void publicar_hace_aparecer_el_evento_en_una_busqueda_que_estaba_en_cache() throws Exception {
        mvc.perform(get("/eventos").param("tipo", "FESTIVAL")).andExpect(jsonPath("$.length()").value(0));

        long id = crearEvento("FESTIVAL", "Tandil");
        cargarSectores(id, """
                [{"sectorId":1,"numerado":false,"precio":8000}]
                """);
        publicar(id);

        mvc.perform(get("/eventos").param("tipo", "FESTIVAL"))
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());
    }

    // ---------------------------------------------------------------- F8: aviso de publicacion

    @Test
    void publicar_un_evento_con_artistas_avisa_a_notificaciones() throws Exception {
        long id = crearEvento("RECITAL", "Tandil");
        mvc.perform(post("/eventos/" + id + "/participantes").contentType("application/json").content("""
                [{"nombre":"Divididos","tipo":"BANDA"}]
                """)).andExpect(status().isOk());
        cargarSectores(id, """
                [{"sectorId":1,"numerado":false,"precio":8000}]
                """);

        publicar(id);

        // corre en otro hilo: se espera hasta 2 segundos
        verify(notificaciones, timeout(2000)).eventoPublicado(eq(id), eq("Evento"), eq("Tandil"), any(),
                eq(List.of("Divididos")));
    }
}

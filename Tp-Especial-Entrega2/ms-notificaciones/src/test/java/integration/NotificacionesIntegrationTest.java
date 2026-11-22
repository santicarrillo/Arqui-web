package integration;

import notificaciones.Domains.ApiException;
import notificaciones.Domains.Notificacion;
import notificaciones.api.Dtos.CompraConfirmadaRequest;
import notificaciones.api.Dtos.EventoCanceladoRequest;
import notificaciones.api.Dtos.EventoPublicadoRequest;
import notificaciones.client.PreferenciasClient;
import notificaciones.service.AvisoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TimeZone;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MongoDB real en un contenedor (Testcontainers) y preferencias-service simulado.
 * Los casos de negocio llaman a AvisoService directo (sincronico); el primero prueba el camino HTTP con su
 * procesamiento en segundo plano.
 */
@SpringBootTest(classes = notificaciones.NotificacionesApplication.class)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class NotificacionesIntegrationTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
    }

    @Container
    static MongoDBContainer db = new MongoDBContainer("mongo:7.0.11");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.data.mongodb.uri", () -> db.getReplicaSetUrl("notificaciones"));
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    MongoTemplate mongo;

    @Autowired
    AvisoService avisos;

    @MockBean
    PreferenciasClient preferencias;

    @BeforeEach
    void limpiar() {
        mongo.remove(new Query(), Notificacion.class);
    }

    static CompraConfirmadaRequest compra(long compraId, long clienteId) {
        return new CompraConfirmadaRequest(compraId, clienteId, 7L, 2, new BigDecimal("30000"));
    }

    long cantidad() {
        return mongo.count(new Query(), Notificacion.class);
    }

    @Test
    void el_aviso_responde_202_y_la_notificacion_aparece_despues() throws Exception {
        mvc.perform(post("/interno/avisos/compra-confirmada").contentType(MediaType.APPLICATION_JSON).content("""
                        {"compraId":15,"clienteId":5,"eventoId":7,"entradas":2,"total":30000}
                        """))
                .andExpect(status().isAccepted());

        await().atMost(Duration.ofSeconds(5)).until(() -> cantidad() == 1);

        mvc.perform(get("/notificaciones").header("X-Usuario-Id", 5).header("X-Rol", "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("compra-15-cliente-5"))
                .andExpect(jsonPath("$[0].tipo").value("COMPRA_CONFIRMADA"))
                .andExpect(jsonPath("$[0].leida").value(false))
                .andExpect(jsonPath("$[0].mensaje", containsString("2 entrada")));
    }

    @Test
    void un_aviso_incompleto_da_400() throws Exception {
        mvc.perform(post("/interno/avisos/compra-confirmada").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"compraId\":15}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DATOS_INVALIDOS"));
    }

    @Test
    void repetir_un_aviso_no_duplica_la_notificacion() {
        assertEquals(1, avisos.compraConfirmada(compra(15, 5)));
        assertEquals(0, avisos.compraConfirmada(compra(15, 5)));

        assertEquals(1, cantidad());
    }

    @Test
    void la_cancelacion_avisa_una_vez_a_cada_comprador() {
        assertEquals(2, avisos.eventoCancelado(new EventoCanceladoRequest(7L, List.of(5L, 6L, 5L))));

        assertEquals(2, cantidad());
    }

    @Test
    void la_publicacion_avisa_a_los_que_siguen_al_artista() throws Exception {
        when(preferencias.interesados(List.of("Divididos"))).thenReturn(List.of(5L, 8L));

        int nuevas = avisos.eventoPublicado(new EventoPublicadoRequest(7L, "Divididos en Tandil", "Tandil",
                LocalDateTime.of(2026, 12, 10, 21, 0), List.of("Divididos")));

        assertEquals(2, nuevas);
        mvc.perform(get("/notificaciones").header("X-Usuario-Id", 8).header("X-Rol", "CLIENTE"))
                .andExpect(jsonPath("$[0].tipo").value("EVENTO_PUBLICADO"))
                .andExpect(jsonPath("$[0].titulo", containsString("Divididos en Tandil")))
                .andExpect(jsonPath("$[0].mensaje", containsString("10/12/2026 21:00")));
    }

    @Test
    void si_preferencias_no_responde_el_aviso_igual_se_acepta() throws Exception {
        when(preferencias.interesados(any()))
                .thenThrow(ApiException.unavailable("PREFERENCIAS_NO_DISPONIBLE", "caido"));

        mvc.perform(post("/interno/avisos/evento-publicado").contentType(MediaType.APPLICATION_JSON).content("""
                        {"eventoId":7,"nombre":"Divididos en Tandil","artistas":["Divididos"]}
                        """))
                .andExpect(status().isAccepted());

        verify(preferencias, timeout(2000)).interesados(List.of("Divididos"));
        assertEquals(0, cantidad());
    }

    @Test
    void cada_usuario_ve_y_marca_solo_las_suyas() throws Exception {
        avisos.compraConfirmada(compra(15, 5));
        avisos.compraConfirmada(compra(16, 6));

        mvc.perform(get("/notificaciones").header("X-Usuario-Id", 5))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("compra-15-cliente-5"));

        mvc.perform(put("/notificaciones/compra-15-cliente-5/leida").header("X-Usuario-Id", 6))
                .andExpect(status().isNotFound());
        mvc.perform(put("/notificaciones/compra-15-cliente-5/leida").header("X-Usuario-Id", 5))
                .andExpect(status().isNoContent());

        mvc.perform(get("/notificaciones").header("X-Usuario-Id", 5).param("soloNoLeidas", "true"))
                .andExpect(jsonPath("$", hasSize(0)));
    }
}

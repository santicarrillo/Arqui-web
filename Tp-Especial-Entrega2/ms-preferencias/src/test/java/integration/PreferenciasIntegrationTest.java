package integration;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import preferencias.Domains.ApiException;
import preferencias.Domains.Perfil;
import preferencias.client.EventosClient;
import preferencias.client.EventosClient.EventoResumen;

import java.time.LocalDateTime;
import java.util.List;
import java.util.TimeZone;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integracion: MongoDB real en un contenedor (Testcontainers) y eventos-service simulado.
 * En maquinas sin Docker se saltea. Con Docker Engine 29+ hace falta ~/.docker-java.properties con api.version=1.44.
 */
@SpringBootTest(classes = preferencias.PreferenciasApplication.class)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PreferenciasIntegrationTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
    }

    @Container
    static MongoDBContainer db = new MongoDBContainer("mongo:7.0.11");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.data.mongodb.uri", () -> db.getReplicaSetUrl("preferencias"));
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    MongoTemplate mongo;

    @MockBean
    EventosClient eventos;

    // Catalogo simulado de eventos publicados
    static final EventoResumen DIVIDIDOS_TANDIL = evento(1, "Divididos en Tandil", "RECITAL", "Tandil", 10);
    static final EventoResumen RECITAL_AZUL = evento(2, "Recital en Azul", "RECITAL", "Azul", 20);
    static final EventoResumen OBRA_TANDIL = evento(3, "Obra en Tandil", "TEATRO", "Tandil", 5);
    static final EventoResumen STANDUP_AZUL = evento(4, "Stand up en Azul", "TEATRO", "Azul", 15);

    @BeforeEach
    void preparar() {
        mongo.remove(new Query(), Perfil.class);
        when(eventos.publicadosDesde(any()))
                .thenReturn(List.of(DIVIDIDOS_TANDIL, RECITAL_AZUL, OBRA_TANDIL, STANDUP_AZUL));
        when(eventos.publicadosConParticipante(eq("Divididos"), any())).thenReturn(List.of(DIVIDIDOS_TANDIL));
    }

    static EventoResumen evento(long id, String nombre, String tipo, String ciudad, int enDias) {
        return new EventoResumen(id, nombre, tipo, LocalDateTime.now().plusDays(enDias), ciudad, "PUBLICADO");
    }

    ResultActions comoCliente(long clienteId, MockHttpServletRequestBuilder pedido) throws Exception {
        return mvc.perform(pedido.header("X-Usuario-Id", clienteId).header("X-Rol", "CLIENTE"));
    }

    void guardarPreferencias(long clienteId, String json) throws Exception {
        comoCliente(clienteId, put("/preferencias").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    void registrarCompra(long clienteId, long eventoId) throws Exception {
        mvc.perform(post("/interno/compras").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":" + clienteId + ",\"eventoId\":" + eventoId + "}"))
                .andExpect(status().isNoContent());
    }

    // ---------------------------------------------------------------- preferencias

    @Test
    void un_cliente_sin_perfil_ve_sus_preferencias_vacias() throws Exception {
        comoCliente(5, get("/preferencias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(5))
                .andExpect(jsonPath("$.artistasFavoritos", hasSize(0)))
                .andExpect(jsonPath("$.eventosComprados", hasSize(0)));
    }

    @Test
    void guardar_limpia_espacios_y_repetidos() throws Exception {
        comoCliente(5, put("/preferencias").contentType(MediaType.APPLICATION_JSON).content("""
                {"tiposEvento":["recital","RECITAL","teatro"],
                 "artistasFavoritos":["León Gieco"," leon   gieco ","Divididos",""],
                 "ciudad":"  Tandil "}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiposEvento", contains("RECITAL", "TEATRO")))
                .andExpect(jsonPath("$.artistasFavoritos", contains("León Gieco", "Divididos")))
                .andExpect(jsonPath("$.ciudad").value("Tandil"));
    }

    @Test
    void mas_de_diez_artistas_da_400() throws Exception {
        comoCliente(5, put("/preferencias").contentType(MediaType.APPLICATION_JSON).content("""
                {"artistasFavoritos":["a1","a2","a3","a4","a5","a6","a7","a8","a9","a10","a11"]}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DEMASIADAS_PREFERENCIAS"));
    }

    @Test
    void solo_los_clientes_tienen_preferencias() throws Exception {
        mvc.perform(get("/preferencias").header("X-Usuario-Id", 1).header("X-Rol", "ORGANIZADOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_CLIENTES"));
    }

    @Test
    void repetir_el_aviso_de_una_compra_no_la_duplica() throws Exception {
        registrarCompra(5, 100);
        registrarCompra(5, 100);

        comoCliente(5, get("/preferencias")).andExpect(jsonPath("$.eventosComprados", contains(100)));
    }

    @Test
    void guardar_preferencias_no_borra_las_compras() throws Exception {
        registrarCompra(5, 100);
        guardarPreferencias(5, """
                {"tiposEvento":["TEATRO"]}
                """);

        comoCliente(5, get("/preferencias"))
                .andExpect(jsonPath("$.tiposEvento", contains("TEATRO")))
                .andExpect(jsonPath("$.eventosComprados", contains(100)));
    }

    // ---------------------------------------------------------------- recomendaciones

    @Test
    void recomienda_por_artista_tipo_y_ciudad_del_mas_afin_al_menos() throws Exception {
        guardarPreferencias(5, """
                {"tiposEvento":["RECITAL"],"artistasFavoritos":["Divididos"],"ciudad":"Tandil"}
                """);

        comoCliente(5, get("/recomendaciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))      // el stand up en Azul no coincide en nada
                .andExpect(jsonPath("$[0].eventoId").value(1))
                .andExpect(jsonPath("$[0].puntaje").value(6))
                .andExpect(jsonPath("$[0].motivos", contains("ARTISTA_FAVORITO", "TIPO_PREFERIDO", "TU_CIUDAD")))
                .andExpect(jsonPath("$[1].eventoId").value(2))
                .andExpect(jsonPath("$[1].motivos", contains("TIPO_PREFERIDO")))
                .andExpect(jsonPath("$[2].eventoId").value(3))
                .andExpect(jsonPath("$[2].motivos", contains("TU_CIUDAD")));
    }

    @Test
    void no_recomienda_eventos_que_ya_compro() throws Exception {
        guardarPreferencias(5, """
                {"tiposEvento":["RECITAL"],"artistasFavoritos":["Divididos"],"ciudad":"Tandil"}
                """);
        registrarCompra(5, 1);

        comoCliente(5, get("/recomendaciones"))
                .andExpect(jsonPath("$[*].eventoId", contains(2, 3)));
    }

    @Test
    void recomienda_lo_que_compraron_clientes_parecidos() throws Exception {
        registrarCompra(10, 1);
        registrarCompra(10, 2);
        registrarCompra(11, 1);
        registrarCompra(11, 2);
        registrarCompra(12, 1);
        registrarCompra(12, 3);
        registrarCompra(13, 4);      // no compro nada en comun con el cliente 5: no cuenta
        registrarCompra(5, 1);

        comoCliente(5, get("/recomendaciones"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].eventoId").value(2))
                .andExpect(jsonPath("$[0].puntaje").value(4))    // dos clientes parecidos
                .andExpect(jsonPath("$[0].motivos", contains("LO_COMPRARON_CLIENTES_PARECIDOS")))
                .andExpect(jsonPath("$[1].eventoId").value(3))
                .andExpect(jsonPath("$[1].puntaje").value(2));
    }

    @Test
    void a_un_cliente_nuevo_le_recomienda_lo_mas_comprado() throws Exception {
        registrarCompra(10, 2);
        registrarCompra(11, 2);
        registrarCompra(12, 3);

        comoCliente(99, get("/recomendaciones"))
                .andExpect(jsonPath("$[*].eventoId", contains(2, 3)))
                .andExpect(jsonPath("$[0].motivos", contains("POPULAR")));
    }

    @Test
    void si_eventos_no_responde_las_recomendaciones_dan_503() throws Exception {
        when(eventos.publicadosDesde(any()))
                .thenThrow(ApiException.unavailable("EVENTOS_NO_DISPONIBLE", "eventos no responde"));

        comoCliente(5, get("/recomendaciones"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("EVENTOS_NO_DISPONIBLE"));
    }

    // ---------------------------------------------------------------- interno (para notificaciones)

    @Test
    void interesados_por_artista_sin_importar_mayusculas_ni_tildes() throws Exception {
        guardarPreferencias(20, """
                {"artistasFavoritos":["León Gieco"]}
                """);
        guardarPreferencias(21, """
                {"artistasFavoritos":["Divididos"]}
                """);
        guardarPreferencias(22, """
                {"artistasFavoritos":["LEON GIECO","Divididos"]}
                """);

        mvc.perform(get("/interno/interesados").param("artistas", "leon gieco"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", containsInAnyOrder(20, 22)));
        mvc.perform(get("/interno/interesados").param("artistas", "Divididos", "Nadie"))
                .andExpect(jsonPath("$", containsInAnyOrder(21, 22)));
    }
}

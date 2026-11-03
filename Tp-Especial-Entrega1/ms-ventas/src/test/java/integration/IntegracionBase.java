package integration;

import ventas.api.Dtos;
import ventas.client.ClientDtos;
import ventas.client.EventosClient;
import ventas.client.LugaresClient;
import ventas.client.PagosClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import ventas.service.CompraService;
import ventas.service.DisponibilidadService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
        * Base de las pruebas de integracion: PostgreSQL real en un contenedor (hace falta Docker) y los tres
 * servicios externos simulados. El contenedor es unico para todas las clases, asi el contexto de Spring
 * se puede reutilizar.
 */
@SpringBootTest(properties = "ventas.vencimiento-cada-ms=3600000")
abstract class IntegracionBase {

    static final long EVENTO = 7L;
    static final long SECTOR_NUMERADO = 1L;
    static final long SECTOR_ZONA = 2L;
    static final long ASIENTO = 100L;

    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        DB.start();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }

    @MockBean
    protected EventosClient eventos;
    @MockBean
    protected LugaresClient lugares;
    @MockBean
    protected PagosClient pagos;

    @Autowired
    protected JdbcClient jdbc;
    @Autowired
    protected CompraService compras;
    @Autowired
    protected DisponibilidadService disponibilidad;

    /**
     * Un evento publicado con un sector numerado (asientos 100 a 104, $30000) y una zona (capacidad 1, $15000),
     * con la disponibilidad ya preparada.
     */
    @BeforeEach
    void prepararEvento() {
        jdbc.sql("TRUNCATE devolucion, detalle_compra, evento_asiento, cupo_sector, compra RESTART IDENTITY CASCADE")
                .update();

        when(eventos.obtener(EVENTO)).thenReturn(Optional.of(new ClientDtos.EventoInfo(
                EVENTO, "PUBLICADO", LocalDateTime.now().plusDays(30), 6,
                List.of(new ClientDtos.SectorEvento(SECTOR_NUMERADO, true, new BigDecimal("30000")),
                        new ClientDtos.SectorEvento(SECTOR_ZONA, false, new BigDecimal("15000"))))));
        when(lugares.asientos(SECTOR_NUMERADO)).thenReturn(List.of(
                new ClientDtos.AsientoInfo(100L), new ClientDtos.AsientoInfo(101L), new ClientDtos.AsientoInfo(102L),
                new ClientDtos.AsientoInfo(103L), new ClientDtos.AsientoInfo(104L)));
        when(pagos.obtenerPorCompra(anyLong())).thenReturn(Optional.empty());

        disponibilidad.inicializar(new Dtos.InicializarRequest(EVENTO, List.of(
                new Dtos.SectorInit(SECTOR_NUMERADO, true, null),
                new Dtos.SectorInit(SECTOR_ZONA, false, 1))));
    }

    protected static Dtos.CompraRequest pedidoAsiento(long asientoId) {
        return new Dtos.CompraRequest(EVENTO, List.of(new Dtos.LugarRequest(SECTOR_NUMERADO, asientoId, null)));
    }

    protected static Dtos.CompraRequest pedidoZona(int cantidad) {
        return new Dtos.CompraRequest(EVENTO, List.of(new Dtos.LugarRequest(SECTOR_ZONA, null, cantidad)));
    }

    protected String estadoAsiento(long asientoId) {
        return jdbc.sql("SELECT estado FROM evento_asiento WHERE evento_id = :e AND asiento_id = :a")
                .param("e", EVENTO).param("a", asientoId).query(String.class).single();
    }

    protected int ocupadosZona() {
        return jdbc.sql("SELECT ocupados FROM cupo_sector WHERE evento_id = :e AND sector_id = :s")
                .param("e", EVENTO).param("s", SECTOR_ZONA).query(Integer.class).single();
    }

    protected long contar(String tabla) {
        return jdbc.sql("SELECT COUNT(*) FROM " + tabla).query(Long.class).single();
    }
}

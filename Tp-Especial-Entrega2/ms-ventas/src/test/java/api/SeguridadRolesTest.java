package api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import ventas.Domains.Devolucion;
import ventas.api.DevolucionController;
import ventas.api.FilaController;
import ventas.api.IngresoController;
import ventas.api.ReporteController;
import ventas.api.apiExptionHandler;
import ventas.client.EventosClient;
import ventas.client.PagosClient;
import ventas.repository.DevolucionRepository;
import ventas.repository.DevolucionRepository.EntradaDeCompra;
import ventas.repository.FilaRepository;
import ventas.repository.FilaRepository.Fila;
import ventas.repository.IngresoRepository;
import ventas.repository.IngresoRepository.EntradaParaIngreso;
import ventas.repository.ReporteRepository;
import ventas.service.CompraService;
import ventas.service.DevolucionService;
import ventas.service.FilaVirtualService;
import ventas.service.IngresoService;
import ventas.service.ReporteService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de autorizacion por rol en la capa HTTP de ms-ventas (F13).
 * Verifica sin Docker que cada endpoint restringido devuelva 403 ante roles no autorizados
 * y 200 ante los roles permitidos (incluyendo validacion de pertenencia del organizador).
 */
class SeguridadRolesTest {

    private static final long EVENTO_ID = 7L;
    private static final long DUENO_ID = 10L;
    private static final long OTRO_ORG_ID = 20L;
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-22T12:00:00Z"), ZoneOffset.UTC);

    private DevolucionRepository devolucionRepo;
    private IngresoRepository ingresoRepo;
    private ReporteRepository reporteRepo;
    private FilaRepository filaRepo;
    private EventosClient eventosClient;

    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        devolucionRepo = mock(DevolucionRepository.class);
        ingresoRepo = mock(IngresoRepository.class);
        reporteRepo = mock(ReporteRepository.class);
        filaRepo = mock(FilaRepository.class);
        eventosClient = mock(EventosClient.class);

        DevolucionService devolucionService = new DevolucionService(
                devolucionRepo, mock(CompraService.class), mock(PagosClient.class),
                mock(ApplicationEventPublisher.class), mock(TransactionTemplate.class), RELOJ, 72);
        IngresoService ingresoService = new IngresoService(ingresoRepo, RELOJ);
        ReporteService reporteService = new ReporteService(reporteRepo, eventosClient);
        FilaVirtualService filaService = new FilaVirtualService(filaRepo, eventosClient, RELOJ);

        mvc = MockMvcBuilders.standaloneSetup(
                        new DevolucionController(devolucionService),
                        new IngresoController(ingresoService),
                        new ReporteController(reporteService),
                        new FilaController(filaService))
                .setControllerAdvice(new apiExptionHandler())
                .build();
    }

    // ---------------------------------------------------------------- Devoluciones (solo ADMINISTRADOR)

    @ParameterizedTest
    @ValueSource(strings = {"CLIENTE", "ORGANIZADOR", "ACCESO"})
    void listarDevolucionesRechazaRolesNoAdminCon403(String rol) throws Exception {
        mvc.perform(get("/devoluciones").header("X-Rol", rol))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_ADMIN"));

        verifyNoInteractions(devolucionRepo);
    }

    @Test
    void listarDevolucionesPermiteAdministrador() throws Exception {
        when(devolucionRepo.porEstado("PENDIENTE")).thenReturn(List.of());

        mvc.perform(get("/devoluciones").header("X-Rol", "ADMINISTRADOR"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENTE", "ORGANIZADOR", "ACCESO"})
    void resolverDevolucionRechazaRolesNoAdminCon403(String rol) throws Exception {
        mvc.perform(put("/entradas/1/devolucion")
                        .header("X-Usuario-Id", 1)
                        .header("X-Rol", rol)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aprobar\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_ADMIN"));

        verifyNoInteractions(devolucionRepo);
    }

    @Test
    void resolverDevolucionPermiteAdministrador() throws Exception {
        LocalDateTime ahora = LocalDateTime.now(RELOJ);
        when(devolucionRepo.entradaBloqueando(1L)).thenReturn(Optional.of(new EntradaDeCompra(
                1L, 100L, 55L, EVENTO_ID, ahora.plusDays(10), "PAGADA", 1L, 50L,
                new BigDecimal("10000"), "VALIDA")));
        Devolucion pendiente = new Devolucion(9L, 1L, ahora, "PENDIENTE", null, null, "SOLICITUD_CLIENTE");
        Devolucion rechazada = new Devolucion(9L, 1L, ahora, "RECHAZADA", 1L, ahora, "SOLICITUD_CLIENTE");
        when(devolucionRepo.activaDeEntrada(1L)).thenReturn(Optional.of(pendiente));
        when(devolucionRepo.porId(9L)).thenReturn(Optional.of(rechazada));

        mvc.perform(put("/entradas/1/devolucion")
                        .header("X-Usuario-Id", 1)
                        .header("X-Rol", "ADMINISTRADOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aprobar\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"));
    }

    // ---------------------------------------------------------------- Control de acceso QR (ACCESO / ADMINISTRADOR)

    @ParameterizedTest
    @ValueSource(strings = {"CLIENTE", "ORGANIZADOR"})
    void validarQrRechazaClienteYOrganizadorCon403(String rol) throws Exception {
        mvc.perform(post("/ingresos")
                        .header("X-Usuario-Id", 5)
                        .header("X-Rol", rol)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventoId\":7,\"codigoQr\":\"QR-7-1-1-abcd\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_PERSONAL_DE_ACCESO"));

        verifyNoInteractions(ingresoRepo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACCESO", "ADMINISTRADOR"})
    void validarQrPermiteAccesoYAdministrador(String rol) throws Exception {
        LocalDateTime ahora = LocalDateTime.now(RELOJ);
        when(ingresoRepo.marcarUsada(eq("QR-7-1-1-abcd"), eq(EVENTO_ID), anyLong(), any()))
                .thenReturn(Optional.of(1L));
        when(ingresoRepo.porQr("QR-7-1-1-abcd")).thenReturn(Optional.of(
                new EntradaParaIngreso(1L, EVENTO_ID, 2L, 100L, "USADA", "PAGADA", ahora)));

        mvc.perform(post("/ingresos")
                        .header("X-Usuario-Id", 90)
                        .header("X-Rol", rol)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventoId\":7,\"codigoQr\":\"QR-7-1-1-abcd\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permitido").value(true));
    }

    // ---------------------------------------------------------------- Reportes (ORGANIZADOR dueno / ADMINISTRADOR)

    @ParameterizedTest
    @ValueSource(strings = {"CLIENTE", "ACCESO"})
    void reporteEventoRechazaClienteYAccesoCon403(String rol) throws Exception {
        mvc.perform(get("/reportes/eventos/" + EVENTO_ID)
                        .header("X-Usuario-Id", 5)
                        .header("X-Rol", rol))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_ORGANIZADOR"));

        verifyNoInteractions(eventosClient, reporteRepo);
    }

    @Test
    void reporteEventoRechazaOrganizadorDeOtroEventoCon403() throws Exception {
        when(eventosClient.organizadorDe(EVENTO_ID)).thenReturn(Optional.of(DUENO_ID));

        mvc.perform(get("/reportes/eventos/" + EVENTO_ID)
                        .header("X-Usuario-Id", OTRO_ORG_ID)
                        .header("X-Rol", "ORGANIZADOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NO_ES_TU_EVENTO"));
    }

    @Test
    void reporteEventoPermiteOrganizadorDuenoYAdministrador() throws Exception {
        when(eventosClient.organizadorDe(EVENTO_ID)).thenReturn(Optional.of(DUENO_ID));
        when(reporteRepo.ventasPorSector(EVENTO_ID)).thenReturn(List.of());
        when(reporteRepo.capacidades(EVENTO_ID)).thenReturn(List.of());

        mvc.perform(get("/reportes/eventos/" + EVENTO_ID)
                        .header("X-Usuario-Id", DUENO_ID)
                        .header("X-Rol", "ORGANIZADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(EVENTO_ID));

        mvc.perform(get("/reportes/eventos/" + EVENTO_ID)
                        .header("X-Usuario-Id", 1)
                        .header("X-Rol", "ADMINISTRADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(EVENTO_ID));
    }

    // ---------------------------------------------------------------- Fila virtual (ORGANIZADOR dueno / ADMINISTRADOR)

    @ParameterizedTest
    @ValueSource(strings = {"CLIENTE", "ACCESO"})
    void activarYDesactivarFilaRechazaClienteYAccesoCon403(String rol) throws Exception {
        mvc.perform(put("/filas/" + EVENTO_ID)
                        .header("X-Usuario-Id", 5)
                        .header("X-Rol", rol)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"simultaneos\":20,\"minutosTurno\":10}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_ORGANIZADOR"));

        mvc.perform(delete("/filas/" + EVENTO_ID)
                        .header("X-Usuario-Id", 5)
                        .header("X-Rol", rol))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_ORGANIZADOR"));

        verifyNoInteractions(eventosClient, filaRepo);
    }

    @Test
    void activarYDesactivarFilaRechazaOrganizadorAjenoCon403() throws Exception {
        when(eventosClient.organizadorDe(EVENTO_ID)).thenReturn(Optional.of(DUENO_ID));

        mvc.perform(put("/filas/" + EVENTO_ID)
                        .header("X-Usuario-Id", OTRO_ORG_ID)
                        .header("X-Rol", "ORGANIZADOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"simultaneos\":20,\"minutosTurno\":10}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NO_ES_TU_EVENTO"));

        mvc.perform(delete("/filas/" + EVENTO_ID)
                        .header("X-Usuario-Id", OTRO_ORG_ID)
                        .header("X-Rol", "ORGANIZADOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NO_ES_TU_EVENTO"));
    }

    @Test
    void activarYDesactivarFilaPermiteOrganizadorDuenoYAdministrador() throws Exception {
        when(eventosClient.organizadorDe(EVENTO_ID)).thenReturn(Optional.of(DUENO_ID));
        when(filaRepo.fila(EVENTO_ID)).thenReturn(Optional.of(new Fila(EVENTO_ID, true, 20, 10, 0)));

        mvc.perform(put("/filas/" + EVENTO_ID)
                        .header("X-Usuario-Id", DUENO_ID)
                        .header("X-Rol", "ORGANIZADOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"simultaneos\":20,\"minutosTurno\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true));

        mvc.perform(delete("/filas/" + EVENTO_ID)
                        .header("X-Usuario-Id", 1)
                        .header("X-Rol", "ADMINISTRADOR"))
                .andExpect(status().isOk());
    }
}

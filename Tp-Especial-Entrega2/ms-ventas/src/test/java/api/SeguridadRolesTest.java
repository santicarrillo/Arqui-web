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
import ventas.api.CompraController;
import ventas.api.ConsultaController;
import ventas.api.DevolucionController;
import ventas.api.Dtos;
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
import ventas.repository.VentasRepository;
import ventas.service.CompraService;
import ventas.service.DevolucionService;
import ventas.service.DisponibilidadService;
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
import static org.mockito.Mockito.verify;
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
 * F1: compras, entradas, pedido de devolucion y turnos de la fila virtual son solo de CLIENTE.
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
    private CompraService compraService;
    private VentasRepository ventasRepo;

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
        compraService = mock(CompraService.class);
        ventasRepo = mock(VentasRepository.class);

        mvc = MockMvcBuilders.standaloneSetup(
                        new DevolucionController(devolucionService),
                        new IngresoController(ingresoService),
                        new ReporteController(reporteService),
                        new FilaController(filaService),
                        new CompraController(compraService, filaService),
                        new ConsultaController(mock(DisponibilidadService.class), ventasRepo))
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

    // ---------------------------------------------------------------- F1: compras, entradas y turnos (solo CLIENTE)

    private static final String PEDIDO = "{\"eventoId\":7,\"lugares\":[{\"sectorId\":1,\"cantidad\":1}]}";

    private static Dtos.CompraView compra(String estado) {
        return new Dtos.CompraView(100L, EVENTO_ID, estado, new BigDecimal("15000"), null, null, List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORGANIZADOR", "ADMINISTRADOR", "ACCESO"})
    void comprarRechazaRolesQueNoSonClienteCon403(String rol) throws Exception {
        mvc.perform(post("/compras").header("X-Usuario-Id", 5).header("X-Rol", rol)
                        .header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(PEDIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_CLIENTES"));

        verifyNoInteractions(compraService, filaRepo);
    }

    @Test
    void comprarPermiteCliente() throws Exception {
        when(compraService.crear(eq(5L), eq("k1"), any()))
                .thenReturn(new CompraService.CompraCreada(compra("PENDIENTE"), true));

        mvc.perform(post("/compras").header("X-Usuario-Id", 5).header("X-Rol", "CLIENTE")
                        .header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(PEDIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.compraId").value(100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORGANIZADOR", "ADMINISTRADOR", "ACCESO"})
    void pagarVerYCancelarCompraRechazanRolesQueNoSonClienteCon403(String rol) throws Exception {
        mvc.perform(post("/compras/100/pagar").header("X-Usuario-Id", 5).header("X-Rol", rol)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"medioPago\":\"TARJETA\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_CLIENTES"));
        mvc.perform(get("/compras/100").header("X-Usuario-Id", 5).header("X-Rol", rol))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_CLIENTES"));
        mvc.perform(delete("/compras/100").header("X-Usuario-Id", 5).header("X-Rol", rol))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_CLIENTES"));

        verifyNoInteractions(compraService);
    }

    @Test
    void pagarVerYCancelarCompraPermitenCliente() throws Exception {
        when(compraService.pagar(5L, 100L, "TARJETA"))
                .thenReturn(new CompraService.ResultadoPago(CompraService.Resultado.PAGADA, compra("PAGADA")));
        when(compraService.obtener(5L, 100L)).thenReturn(compra("PAGADA"));

        mvc.perform(post("/compras/100/pagar").header("X-Usuario-Id", 5).header("X-Rol", "CLIENTE")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"medioPago\":\"TARJETA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAGADA"));
        mvc.perform(get("/compras/100").header("X-Usuario-Id", 5).header("X-Rol", "CLIENTE"))
                .andExpect(status().isOk());
        mvc.perform(delete("/compras/100").header("X-Usuario-Id", 5).header("X-Rol", "CLIENTE"))
                .andExpect(status().isNoContent());

        verify(compraService).cancelar(5L, 100L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORGANIZADOR", "ADMINISTRADOR", "ACCESO"})
    void entradasRechazanRolesQueNoSonClienteCon403(String rol) throws Exception {
        mvc.perform(get("/entradas").header("X-Usuario-Id", 5).header("X-Rol", rol))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_CLIENTES"));
        mvc.perform(get("/entradas/1").header("X-Usuario-Id", 5).header("X-Rol", rol))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_CLIENTES"));

        verifyNoInteractions(ventasRepo);
    }

    @Test
    void entradasPermitenCliente() throws Exception {
        Dtos.EntradaView entrada = new Dtos.EntradaView(1L, 100L, EVENTO_ID, 1L, null,
                new BigDecimal("15000"), "QR-7-100-1-abcd", "VALIDA", null);
        when(ventasRepo.entradasDeCliente(5L)).thenReturn(List.of(entrada));
        when(ventasRepo.entradaDeCliente(1L, 5L)).thenReturn(Optional.of(entrada));

        mvc.perform(get("/entradas").header("X-Usuario-Id", 5).header("X-Rol", "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigoQr").value("QR-7-100-1-abcd"));
        mvc.perform(get("/entradas/1").header("X-Usuario-Id", 5).header("X-Rol", "CLIENTE"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORGANIZADOR", "ADMINISTRADOR", "ACCESO"})
    void pedirDevolucionRechazaRolesQueNoSonClienteCon403(String rol) throws Exception {
        mvc.perform(post("/entradas/1/devolucion").header("X-Usuario-Id", 5).header("X-Rol", rol))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_CLIENTES"));

        verifyNoInteractions(devolucionRepo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORGANIZADOR", "ADMINISTRADOR", "ACCESO"})
    void turnosDeLaFilaRechazanRolesQueNoSonClienteCon403(String rol) throws Exception {
        mvc.perform(post("/filas/" + EVENTO_ID + "/turno").header("X-Usuario-Id", 5).header("X-Rol", rol))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_CLIENTES"));
        mvc.perform(get("/filas/" + EVENTO_ID + "/turno").header("X-Usuario-Id", 5).header("X-Rol", rol))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SOLO_CLIENTES"));

        verifyNoInteractions(filaRepo);
    }
}

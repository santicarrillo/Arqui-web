package integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ventas.Domains.ApiException;
import ventas.api.CompraController;
import ventas.api.Dtos.CompraView;
import ventas.api.FilaDtos.ActivarFilaRequest;
import ventas.api.FilaDtos.TurnoView;
import ventas.service.FilaVirtualService;

/** Fila virtual (F10), contra PostgreSQL real. La compra se prueba por el controller, que es donde se controla el turno. */
class FilaVirtualTest extends IntegracionBase {

    private static final long ORGANIZADOR = 900L;

    @Autowired
    private FilaVirtualService filas;

    @Autowired
    private CompraController compraController;

    @BeforeEach
    void elEventoEsDelOrganizador() {
        when(eventos.organizadorDe(EVENTO)).thenReturn(Optional.of(ORGANIZADOR));
    }

    private void activar(int simultaneos, int minutos) {
        filas.activar(ORGANIZADOR, "ORGANIZADOR", EVENTO, new ActivarFilaRequest(simultaneos, minutos));
    }

    private ResponseEntity<CompraView> comprarZona(long cliente, String clave) {
        return compraController.crear(cliente, clave, pedidoZona(1));
    }

    private ResponseEntity<CompraView> comprarAsiento(long cliente, String clave, long asiento) {
        return compraController.crear(cliente, clave, pedidoAsiento(asiento));
    }

    private String errorAlComprar(long cliente) {
        ApiException e = assertThrows(ApiException.class, () -> comprarAsiento(cliente, "k-" + cliente, ASIENTO));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        return e.getError();
    }

    @Test
    void sinFilaSeCompraDirectamente() {
        assertEquals(HttpStatus.CREATED, comprarAsiento(55L, "k1", ASIENTO).getStatusCode());
    }

    @Test
    void conLaFilaActivaNoSeCompraSinTurno() {
        activar(1, 10);

        assertEquals("TURNO_REQUERIDO", errorAlComprar(55L));
    }

    @Test
    void pedirTurnoDosVecesDevuelveElMismo() {
        activar(1, 10);

        TurnoView primero = filas.tomarTurno(55L, EVENTO);
        TurnoView segundo = filas.tomarTurno(55L, EVENTO);

        assertEquals(primero.numero(), segundo.numero());
        assertEquals(1, contar("turno_fila"));
    }

    @Test
    void losTurnosSeHabilitanPorOrdenHastaCompletarLosSimultaneos() {
        activar(2, 10);
        assertEquals(0L, filas.tomarTurno(1L, EVENTO).personasAntes());
        assertEquals(1L, filas.tomarTurno(2L, EVENTO).personasAntes());
        assertEquals(2L, filas.tomarTurno(3L, EVENTO).personasAntes());

        assertEquals(2, filas.avanzar(EVENTO));

        assertTrue(filas.turno(1L, EVENTO).puedeComprar());
        assertTrue(filas.turno(2L, EVENTO).puedeComprar());
        TurnoView tercero = filas.turno(3L, EVENTO);
        assertEquals("ESPERANDO", tercero.estado());
        assertEquals(0L, tercero.personasAntes(), "ahora es el primero de la fila");
        assertEquals(0, filas.avanzar(EVENTO), "la tanda esta completa: no se habilita a nadie mas");
    }

    @Test
    void conTurnoHabilitadoSeCompraYSuLugarPasaAlSiguiente() {
        activar(1, 10);
        filas.tomarTurno(55L, EVENTO);
        filas.tomarTurno(77L, EVENTO);
        filas.avanzar(EVENTO);

        assertEquals("TURNO_EN_ESPERA", errorAlComprar(77L));
        assertEquals(HttpStatus.CREATED, comprarAsiento(55L, "k55", ASIENTO).getStatusCode());
        assertEquals("USADO", filas.turno(55L, EVENTO).estado());

        assertEquals(1, filas.avanzar(EVENTO), "el que compro libero su lugar");
        assertEquals(HttpStatus.CREATED, comprarZona(77L, "k77").getStatusCode());
    }

    @Test
    void unTurnoVencidoNoDejaComprarYVuelveAlFinalSiPideOtro() {
        activar(1, 10);
        filas.tomarTurno(55L, EVENTO);
        filas.avanzar(EVENTO);
        filas.tomarTurno(77L, EVENTO);
        jdbc.sql("UPDATE turno_fila SET vence_en = :v WHERE cliente_id = 55")
                .param("v", LocalDateTime.now().minusMinutes(1)).update();

        assertEquals("TURNO_VENCIDO", errorAlComprar(55L));

        assertEquals(1, filas.avanzar(EVENTO));
        assertTrue(filas.turno(77L, EVENTO).puedeComprar(), "el lugar del vencido paso al siguiente");

        TurnoView nuevo = filas.tomarTurno(55L, EVENTO);
        assertEquals("ESPERANDO", nuevo.estado());
        assertEquals(3L, nuevo.numero(), "vuelve al final de la fila");
        assertFalse(nuevo.puedeComprar());
    }

    @Test
    void soloElOrganizadorDelEventoOUnAdministradorManejanLaFila() {
        ApiException ajeno = assertThrows(ApiException.class, () ->
                filas.activar(901L, "ORGANIZADOR", EVENTO, new ActivarFilaRequest(1, 10)));
        assertEquals("NO_ES_TU_EVENTO", ajeno.getError());
        ApiException cliente = assertThrows(ApiException.class, () ->
                filas.activar(55L, "CLIENTE", EVENTO, new ActivarFilaRequest(1, 10)));
        assertEquals("SOLO_ORGANIZADOR", cliente.getError());

        assertTrue(filas.activar(1L, "ADMINISTRADOR", EVENTO, new ActivarFilaRequest(1, 10)).activa());
    }

    @Test
    void desactivarLaFilaVuelveALaCompraDirecta() {
        activar(1, 10);
        filas.desactivar(ORGANIZADOR, "ORGANIZADOR", EVENTO);

        assertFalse(filas.estado(EVENTO).activa());
        assertEquals(HttpStatus.CREATED, comprarAsiento(55L, "k1", ASIENTO).getStatusCode());
    }

    @Test
    void sinFilaActivaNoSeDanTurnos() {
        ApiException e = assertThrows(ApiException.class, () -> filas.tomarTurno(55L, EVENTO));

        assertEquals("SIN_FILA", e.getError());
        assertNull(filas.estado(EVENTO).simultaneos());
    }

    @Test
    void muchosClientesALaVezRecibenNumerosDistintos() throws Exception {
        activar(5, 10);
        int clientes = 30;
        Set<Long> numeros = ConcurrentHashMap.newKeySet();
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(clientes);
        for (long c = 1; c <= clientes; c++) {
            long cliente = c;
            pool.submit(() -> {
                largada.await();
                numeros.add(filas.tomarTurno(cliente, EVENTO).numero());
                return null;
            });
        }
        largada.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(clientes, numeros.size(), "ningun numero repetido");
        assertEquals(clientes, contar("turno_fila"));
        assertEquals(5, filas.avanzar(EVENTO));
    }
}

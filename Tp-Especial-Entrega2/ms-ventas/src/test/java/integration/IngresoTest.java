package integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import ventas.Domains.ApiException;
import ventas.api.Dtos.CompraView;
import ventas.api.IngresoDtos.IngresoView;
import ventas.client.ClientDtos.PagoInfo;
import ventas.service.IngresoService;

/** Control de acceso con QR (F6), contra PostgreSQL real. */
class IngresoTest extends IntegracionBase {

    private static final long CLIENTE = 55L;
    private static final long PERSONAL = 900L;

    @Autowired
    private IngresoService ingresos;

    /** pagos-service tiene un pago APROBADO para cualquier compra: pagar la confirma y genera los QR. */
    @BeforeEach
    void pagosApruebaTodo() {
        when(pagos.obtenerPorCompra(anyLong()))
                .thenAnswer(inv -> Optional.of(new PagoInfo(1L, inv.getArgument(0), "APROBADO")));
    }

    /** Compra y paga un asiento; devuelve el QR de la entrada. */
    private String comprarYPagar(String clave, long asiento) {
        CompraView compra = compras.crear(CLIENTE, clave, pedidoAsiento(asiento)).compra();
        return compras.pagar(CLIENTE, compra.compraId(), "TARJETA").compra().detalle().get(0).codigoQr();
    }

    private String estadoEntrada(String qr) {
        return jdbc.sql("SELECT estado FROM detalle_compra WHERE codigo_qr = :qr")
                .param("qr", qr).query(String.class).single();
    }

    @Test
    void unaEntradaPagadaIngresaYQuedaUsada() {
        String qr = comprarYPagar("i1", ASIENTO);

        IngresoView ingreso = ingresos.validar(PERSONAL, "ACCESO", EVENTO, qr);

        assertTrue(ingreso.permitido());
        assertEquals(Long.valueOf(ASIENTO), ingreso.asientoId());
        assertNotNull(ingreso.ingresoEn());
        assertEquals("USADA", estadoEntrada(qr));
    }

    @Test
    void conLaMismaEntradaNoSePuedeEntrarDosVeces() {
        String qr = comprarYPagar("i2", ASIENTO);
        ingresos.validar(PERSONAL, "ACCESO", EVENTO, qr);

        ApiException e = assertThrows(ApiException.class, () -> ingresos.validar(PERSONAL, "ACCESO", EVENTO, qr));

        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("ENTRADA_YA_USADA", e.getError());
    }

    @Test
    void unQrQueNoExisteDa404() {
        ApiException e = assertThrows(ApiException.class,
                () -> ingresos.validar(PERSONAL, "ACCESO", EVENTO, "QR-inventado"));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        assertEquals("QR_INVALIDO", e.getError());
    }

    @Test
    void unaEntradaDeOtroEventoNoIngresaYSigueValida() {
        String qr = comprarYPagar("i3", ASIENTO);

        ApiException e = assertThrows(ApiException.class, () -> ingresos.validar(PERSONAL, "ACCESO", 999L, qr));

        assertEquals("ENTRADA_DE_OTRO_EVENTO", e.getError());
        assertEquals("VALIDA", estadoEntrada(qr), "sigue sirviendo para su propio evento");
    }

    @Test
    void unaEntradaDevueltaNoIngresa() {
        String qr = comprarYPagar("i4", ASIENTO);
        jdbc.sql("UPDATE detalle_compra SET estado = 'DEVUELTA' WHERE codigo_qr = :qr").param("qr", qr).update();

        ApiException e = assertThrows(ApiException.class, () -> ingresos.validar(PERSONAL, "ACCESO", EVENTO, qr));

        assertEquals("ENTRADA_DEVUELTA", e.getError());
    }

    @Test
    void unClienteNoPuedeValidarEntradas() {
        String qr = comprarYPagar("i5", ASIENTO);

        ApiException e = assertThrows(ApiException.class, () -> ingresos.validar(CLIENTE, "CLIENTE", EVENTO, qr));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertEquals("VALIDA", estadoEntrada(qr));
    }

    /** Dos molinetes leen el mismo QR al mismo tiempo (por ejemplo, una captura de pantalla compartida). */
    @Test
    void veinteMolinetesLeenElMismoQrYSoloUnoDejaPasar() throws Exception {
        String qr = comprarYPagar("i6", ASIENTO);
        int hilos = 20;
        AtomicInteger permitidos = new AtomicInteger();
        AtomicInteger rechazados = new AtomicInteger();
        AtomicInteger otros = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch largada = new CountDownLatch(1);

        for (int i = 0; i < hilos; i++) {
            pool.submit(() -> {
                listos.countDown();
                try {
                    largada.await();
                    ingresos.validar(PERSONAL, "ACCESO", EVENTO, qr);
                    permitidos.incrementAndGet();
                } catch (ApiException e) {
                    if ("ENTRADA_YA_USADA".equals(e.getError())) {
                        rechazados.incrementAndGet();
                    } else {
                        otros.incrementAndGet();
                    }
                } catch (Exception e) {
                    otros.incrementAndGet();
                }
            });
        }
        listos.await();
        largada.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));

        assertEquals(1, permitidos.get(), "una sola persona entra");
        assertEquals(hilos - 1, rechazados.get());
        assertEquals(0, otros.get());
    }
}

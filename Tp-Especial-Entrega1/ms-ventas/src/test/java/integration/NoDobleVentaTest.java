package integration;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ventas.Domains.ApiException;
import ventas.service.CompraService.CompraCreada;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * La prueba que pide la consigna: muchas compras simultaneas del mismo lugar y una sola exitosa.
 * Corre contra PostgreSQL real, porque lo que se prueba es el control que hace la base.
 */
class NoDobleVentaTest extends IntegracionBase {

    private static final int HILOS = 50;

    @Test
    void cincuentaClientesPidenElMismoAsientoYSoloUnoLoConsigue() throws Exception {
        Resultado r = lanzar(HILOS, cliente -> compras.crear(cliente, "clave-" + cliente, pedidoAsiento(ASIENTO)));

        assertEquals(1, r.exitos.get(), "tiene que haber una sola reserva exitosa");
        assertEquals(HILOS - 1, r.conflictos.get(), "el resto tiene que recibir 409");
        assertEquals(0, r.otros.get(), "no tiene que haber otros errores");
        assertEquals("RESERVADO", estadoAsiento(ASIENTO));
        assertEquals(1, contar("compra"), "las compras fallidas no dejan nada guardado");
        assertEquals(1, contar("detalle_compra"));
    }

    @Test
    void cincuentaClientesPidenElUltimoLugarDeUnaZonaYSoloUnoLoConsigue() throws Exception {
        Resultado r = lanzar(HILOS, cliente -> compras.crear(cliente, "clave-" + cliente, pedidoZona(1)));

        assertEquals(1, r.exitos.get());
        assertEquals(HILOS - 1, r.conflictos.get());
        assertEquals(0, r.otros.get());
        assertEquals(1, ocupadosZona(), "el cupo nunca supera la capacidad");
    }

    @Test
    void elMismoClienteReintentaConLaMismaClaveYNoDuplicaLaCompra() throws Exception {
        AtomicInteger nuevas = new AtomicInteger();
        Resultado r = lanzar(20, cliente -> {
            CompraCreada creada = compras.crear(99L, "misma-clave", pedidoAsiento(ASIENTO));
            if (creada.nueva()) {
                nuevas.incrementAndGet();
            }
            return creada;
        });

        assertEquals(20, r.exitos.get(), "todos los reintentos reciben la compra");
        assertEquals(0, r.conflictos.get());
        assertEquals(0, r.otros.get());
        assertEquals(1, nuevas.get(), "solo uno la crea");
        assertEquals(1, contar("compra"));
        assertEquals(1, contar("detalle_compra"));
    }

    // ---------------------------------------------------------------------------------

    private interface Pedido {
        Object ejecutar(long cliente) throws Exception;
    }

    private static final class Resultado {
        final AtomicInteger exitos = new AtomicInteger();
        final AtomicInteger conflictos = new AtomicInteger();
        final AtomicInteger otros = new AtomicInteger();
    }

    /** Lanza N hilos que arrancan a la vez y cuenta como termino cada uno. */
    private Resultado lanzar(int hilos, Pedido pedido) throws Exception {
        Resultado resultado = new Resultado();
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch largada = new CountDownLatch(1);

        for (int i = 0; i < hilos; i++) {
            final long cliente = 1000 + i;
            pool.submit(() -> {
                listos.countDown();
                try {
                    largada.await();
                    pedido.ejecutar(cliente);
                    resultado.exitos.incrementAndGet();
                } catch (ApiException e) {
                    if (e.getStatus() == HttpStatus.CONFLICT) {
                        resultado.conflictos.incrementAndGet();
                    } else {
                        resultado.otros.incrementAndGet();
                    }
                } catch (Exception e) {
                    resultado.otros.incrementAndGet();
                }
            });
        }

        listos.await();
        largada.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "los hilos no terminaron a tiempo");
        return resultado;
    }
}

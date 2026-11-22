package ventas.client;

import java.math.BigDecimal;
import java.util.List;

/** F8: notificaciones-service le avisa al cliente (email simulado y bandeja en GET /notificaciones). */
public interface NotificacionesClient {

    void compraConfirmada(long compraId, long clienteId, long eventoId, int entradas, BigDecimal total);

    void eventoCancelado(long eventoId, List<Long> clientes);
}

package ventas.client;

/** F9: preferencias-service guarda el historial de compras de cada cliente para recomendarle eventos. */
public interface PreferenciasClient {

    /** Es idempotente del lado de preferencias: repetir el aviso no duplica la compra. */
    void registrarCompra(long clienteId, long eventoId);
}

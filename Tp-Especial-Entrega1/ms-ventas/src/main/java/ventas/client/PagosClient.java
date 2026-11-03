package ventas.client;

import ventas.Domains.ApiException;

import java.util.Optional;

public interface PagosClient {
    /** El pago de una compra, si ya existe (GET /pagos?compraId=). */
    Optional<ClientDtos.PagoInfo> obtenerPorCompra(long compraId) throws ApiException;

    /** Pide el cobro. Devuelve el pago con estado APROBADO o RECHAZADO. */
    ClientDtos.PagoInfo cobrar(ClientDtos.CobroRequest cobro) throws ApiException;
}

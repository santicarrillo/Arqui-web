package ventas.client;

import java.util.Optional;
import ventas.client.ClientDtos.CobroRequest;
import ventas.client.ClientDtos.PagoInfo;
import ventas.client.ClientDtos.ReintegroRequest;

/** Lo que ventas necesita del pagos-service. */
public interface PagosClient {

    /** El pago de una compra, si ya existe (GET /pagos?compraId=). */
    Optional<PagoInfo> obtenerPorCompra(long compraId);

    /** Pide el cobro. Devuelve el pago con estado APROBADO o RECHAZADO. */
    PagoInfo cobrar(CobroRequest cobro);

    /** Pide el reintegro de una entrada devuelta (POST /pagos/{id}/reintegro). */
    void reintegrar(long pagoId, ReintegroRequest reintegro);
}
package client;

import Domains.ApiException;

import java.util.List;

/** Lo que ventas necesita del lugares-service. */
public interface LugaresClient {

    /** Asientos de un sector numerado. */
    List<ClientDtos.AsientoInfo> asientos(long sectorId) throws ApiException;
}
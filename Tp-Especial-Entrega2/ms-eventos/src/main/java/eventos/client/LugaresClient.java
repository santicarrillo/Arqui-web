package eventos.client;

import java.util.List;

/** Lo que eventos necesita de lugares-service. */
public interface LugaresClient {

    /** Un sector del lugar: si es numerado y su capacidad (en zonas, la cantidad de lugares). */
    record SectorLugar(Long id, boolean numerado, int capacidad) {}

    /** Sectores del lugar (GET /lugares/{id}/sectores). */
    List<SectorLugar> sectores(long lugarId);
}

package eventos.client;

import java.util.List;

/** Lo que eventos le avisa a ventas-service. Los dos endpoints de ventas se pueden repetir sin duplicar nada. */
public interface VentasClient {

    /** Un sector a preparar: los numerados no llevan capacidad (ventas le pide los asientos a lugares). */
    record SectorDisponibilidad(Long sectorId, boolean numerado, Integer capacidad) {}

    /** Crea los asientos y cupos del evento (POST /interno/disponibilidad). */
    void prepararDisponibilidad(long eventoId, List<SectorDisponibilidad> sectores);

    /** Vence las compras pendientes y devuelve las entradas pagadas (POST /interno/eventos/{id}/cancelacion). */
    void avisarCancelacion(long eventoId);
}

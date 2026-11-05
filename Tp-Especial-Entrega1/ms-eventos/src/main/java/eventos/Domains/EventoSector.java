package eventos.Domains;

import java.math.BigDecimal;

public record EventoSector(Long eventoId, Long sectorId,
                           boolean numerado, BigDecimal precio) {}
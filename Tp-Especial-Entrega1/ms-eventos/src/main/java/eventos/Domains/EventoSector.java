package eventos.Domains;

import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

/**
 * Precio de un sector para un evento. Va embebido en el documento del evento.
 * El precio se guarda como Decimal128 (decimal exacto); por defecto Spring lo guardaria como texto.
 */
public record EventoSector(
        Long sectorId,
        boolean numerado,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal precio
) {}

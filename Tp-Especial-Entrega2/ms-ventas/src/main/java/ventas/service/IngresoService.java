package ventas.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ventas.Domains.ApiException;
import ventas.api.IngresoDtos.IngresoView;
import ventas.repository.IngresoRepository;
import ventas.repository.IngresoRepository.EntradaParaIngreso;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;

/**
 * Control de acceso (funcionalidad 8): en la puerta se lee el QR y se valida que la entrada exista, sea de ese
 * evento y no se haya usado. Si todo esta bien queda USADA y ya no sirve para volver a entrar.
 */
@Service
public class IngresoService {

    private static final Set<String> ROLES_PERMITIDOS = Set.of("ACCESO", "ADMINISTRADOR");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final IngresoRepository repo;
    private final Clock clock;

    public IngresoService(IngresoRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    public IngresoView validar(long personalId, String rol, long eventoId, String codigoQr) {
        if (rol == null || !ROLES_PERMITIDOS.contains(rol.toUpperCase())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SOLO_PERSONAL_DE_ACCESO",
                    "Solo el personal de acceso puede validar entradas");
        }
        String qr = codigoQr.trim();

        // Primero se intenta marcarla: si el UPDATE condicional la encuentra VALIDA, entra.
        if (repo.marcarUsada(qr, eventoId, personalId, LocalDateTime.now(clock)).isPresent()) {
            EntradaParaIngreso e = repo.porQr(qr).orElseThrow();
            return new IngresoView(true, e.entradaId(), e.eventoId(), e.sectorId(), e.asientoId(), e.usadoEn(),
                    "Ingreso permitido");
        }
        // Si no se pudo marcar, se lee la entrada solo para explicar el motivo.
        throw motivoDeRechazo(repo.porQr(qr), eventoId);
    }

    private static ApiException motivoDeRechazo(Optional<EntradaParaIngreso> leida, long eventoId) {
        if (leida.isEmpty()) {
            return ApiException.notFound("QR_INVALIDO", "El codigo QR no corresponde a ninguna entrada");
        }
        EntradaParaIngreso e = leida.get();
        if (e.eventoId() != eventoId) {
            return ApiException.conflict("ENTRADA_DE_OTRO_EVENTO",
                    "La entrada es para otro evento (" + e.eventoId() + ")");
        }
        if ("USADA".equals(e.estado())) {
            String cuando = e.usadoEn() == null ? "" : " el " + e.usadoEn().format(HORA);
            return ApiException.conflict("ENTRADA_YA_USADA", "La entrada ya se uso para ingresar" + cuando);
        }
        if ("DEVUELTA".equals(e.estado())) {
            return ApiException.conflict("ENTRADA_DEVUELTA", "La entrada fue devuelta y ya no es valida");
        }
        return ApiException.conflict("ENTRADA_NO_VALIDA", "La entrada no esta habilitada para ingresar");
    }
}

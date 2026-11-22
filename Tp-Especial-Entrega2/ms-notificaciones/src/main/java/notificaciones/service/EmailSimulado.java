package notificaciones.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Envio de email simulado: escribe el mail en el log. En produccion se buscaria el email del usuario en
 * usuarios-service y se mandaria por SMTP o un servicio de correo; el resto del flujo no cambia.
 */
@Component
public class EmailSimulado {

    private static final Logger log = LoggerFactory.getLogger(EmailSimulado.class);

    public void enviar(long clienteId, String asunto, String cuerpo) {
        log.info("[EMAIL] para el usuario {} | {} | {}", clienteId, asunto, cuerpo);
    }
}

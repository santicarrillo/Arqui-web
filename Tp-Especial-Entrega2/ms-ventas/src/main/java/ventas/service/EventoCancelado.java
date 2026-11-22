package ventas.service;

import java.util.List;

/**
 * Evento interno de Spring (F8): ventas termino de procesar la cancelacion de un evento y estos clientes
 * recibieron la devolucion de al menos una entrada. Lo atiende NotificacionesListener.
 */
public record EventoCancelado(long eventoId, List<Long> clientes) {
}

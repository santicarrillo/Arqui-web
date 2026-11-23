package ventas.api;

import org.springframework.http.HttpStatus;
import ventas.Domains.ApiException;

/**
 * F1: comprar, ver las propias compras y entradas, pedir devoluciones y sacar turno en la fila virtual es de
 * los clientes. El rol lo manda el gateway en X-Rol a partir del JWT.
 */
final class Roles {

    private Roles() {
    }

    static void exigirCliente(String rol) {
        if (!"CLIENTE".equals(rol)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SOLO_CLIENTES",
                    "Solo los clientes compran entradas y las gestionan");
        }
    }
}

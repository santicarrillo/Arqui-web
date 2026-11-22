# API Gateway (`api-gateway`)

**Integrante C - Arquitecturas Web (TUDAI, UNICEN)**  
**Entrega 1 - TicketYa**

El `api-gateway` es la **puerta de entrada única** para todo el sistema de microservicios de TicketYa. Los clientes externos (aplicaciones web, móviles o herramientas como Postman) nunca se comunican directamente con los microservicios individuales; todas las solicitudes ingresan a través del puerto **`8080`**.

---

## 1. Responsabilidades del Gateway

1. **Ruteo Centralizado y Desacoplamiento:** Redirige cada petición HTTP al microservicio que le corresponde según el prefijo de la URL.
2. **Aislamiento de Servicios Internos:** Servicios como `pagos-service` (puerto 8085) o los endpoints internos de ventas (`/interno/**`) no tienen ruta pública en el Gateway, evitando que agentes externos interactúen con ellos.
3. **Auditoría y Trazabilidad:** Monitorea y registra en consola el ingreso, egreso, código de estado HTTP y tiempo de respuesta de cada llamada.

---

## 2. Tabla de Ruteo (`application.yml`)

Todas las llamadas externas ingresan por `http://localhost:8080`:

| Ruta Externa | Microservicio Destino | Puerto | Descripción |
| :--- | :--- | :--- | :--- |
| `/usuarios/**` | `usuarios-service` | `8081` | Gestión y consulta de usuarios. |
| `/eventos/**` | `eventos-service` | `8082` | Catálogo de eventos, tarifas y búsqueda. |
| `/compras/**` | `ventas-service` | `8083` | Creación de compras con reserva y pago. |
| `/entradas/**` | `ventas-service` | `8083` | Consulta de entradas con QR y devoluciones. |
| `/disponibilidad/**`| `ventas-service` | `8083` | Consulta de disponibilidad por sector/asiento. |
| `/lugares/**` | `lugares-service` | `8084` | Catálogo físico de estadios y teatros. |
| `/sectores/**` | `lugares-service` | `8084` | Consulta de butacas por sector. |

---

## 3. Funcionamiento del Filtro Global: `LoggingFilter`

Para cumplir con el requisito de auditoría de tráfico, se implementó un filtro global (`GlobalFilter`) llamado `LoggingFilter`.

### ¿Por qué reactivo y no bloqueante?
El Gateway está construido sobre **Spring Cloud Gateway** y el servidor **Netty** (Spring WebFlux). A diferencia de los servidores tradicionales basados en Tomcat donde cada petición bloquea un hilo del sistema operativo, Netty es **asíncrono y no bloqueante**. Esto permite al Gateway procesar miles de peticiones simultáneas con un consumo mínimo de memoria RAM y CPU.

### Anatomía del filtro (`LoggingFilter.java`):

```java
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long inicio = System.currentTimeMillis();
        var request = exchange.getRequest();
        String metodo = request.getMethod().name();
        String ruta = request.getURI().getRawPath();

        // 1. Fase IN (Antes de enviar la petición al microservicio)
        log.info("--> [GATEWAY IN] {} {}", metodo, ruta);

        // 2. Encadenamiento y Fase OUT (Al regresar la respuesta del microservicio)
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            var response = exchange.getResponse();
            long duracion = System.currentTimeMillis() - inicio;
            int status = response.getStatusCode() != null ? response.getStatusCode().value() : 500;
            log.info("<-- [GATEWAY OUT] {} {} - Status: {} ({} ms)", metodo, ruta, status, duracion);
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
```

### ¿Cómo opera paso a paso?

1. **`ServerWebExchange exchange`:** Es el objeto que contiene tanto la petición entrante (`request`: método HTTP, URL, encabezados como `X-Usuario-Id`) como la respuesta saliente (`response`: status code HTTP).
2. **Fase de Entrada (`IN`):** Captura el timestamp inicial (`System.currentTimeMillis()`), extrae el método y la ruta, e imprime de inmediato en consola:
   ```text
   INFO --> [GATEWAY IN] POST /compras
   ```
3. **`chain.filter(exchange)` (Cadena de Responsabilidad):** Delega la petición al siguiente filtro o enrutador para que viaje hacia el microservicio correspondiente (ej. `ventas-service`).
4. **`.then(Mono.fromRunnable(...))` (Callback Asíncrono):** No bloquea el hilo de ejecución mientras el microservicio procesa la lógica. Se registra como una función de retorno que se "despierta" en cuanto el microservicio termina de responder y el paquete regresa por la red hacia el cliente.
5. **Fase de Salida (`OUT`):** Calcula la diferencia de tiempo (`duracion = ahora - inicio`), obtiene el código de respuesta HTTP (ej: 200, 201, 404, 409) e imprime:
   ```text
   INFO <-- [GATEWAY OUT] POST /compras - Status: 201 (35 ms)
   ```
6. **`getOrder() = Ordered.LOWEST_PRECEDENCE`:** Le asigna la prioridad más baja en la cadena de filtros, garantizando que el inicio se mida antes de cualquier otro filtro y la salida se capture al final de todo, asegurando que la medición del tiempo sea 100% exacta.

---

## 4. Instrucciones de Ejecución

Desde IntelliJ IDEA ejecutando `GatewayApp.java` o mediante Maven:
```bash
./mvnw spring-boot:run -pl api-gateway
```
* Una vez iniciado, podés enviar cualquier petición de prueba a `http://localhost:8080/lugares` y observar la traza de logs en la consola del Gateway.

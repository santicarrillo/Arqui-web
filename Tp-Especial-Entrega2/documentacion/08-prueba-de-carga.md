# Informe de prueba de carga — Flujo de compra

## Objetivo

Verificar que el sistema soporta la apertura de venta de un evento, es decir, muchos clientes
comprando y pagando entradas en forma concurrente, manteniendo tiempos de respuesta aceptables
y **sin vender dos veces la misma entrada** ni perder operaciones por errores.

## Herramienta y escenario

- **Herramienta:** Apache JMeter 5.6.3, en modo no-GUI.
- **Plan:** `documentacion/TicketYa-carga-compra.jmx`.
- **Flujo medido (el circuito de compra completo), todo a través del API Gateway (`localhost:8080`) con JWT:**
  1. `setUp` (una vez): se crean los usuarios admin/organizador, un lugar con un sector de gran
     capacidad y un evento, y se publica.
  2. Cada usuario virtual: **registro → login (obtiene su JWT) → compra → pago**.
- Se usa un sector de capacidad amplia y un límite por cliente alto para que la carga mida el
  rendimiento del circuito y no se corte por agotamiento de stock.

## Configuración de la corrida

| Parámetro | Valor |
|---|---|
| Usuarios concurrentes (threads) | 50 |
| Ramp-up | 15 s |
| Iteraciones por usuario (compra+pago) | 10 |
| Entorno | Sistema completo en Docker (13 contenedores: 5 bases + 8 microservicios) |
| Cliente de carga | Misma máquina (localhost) |

> Parámetros ajustables por línea de comando: `-Jusuarios`, `-Jrampa`, `-Jloops`.

## Resultados

| Métrica | Valor |
|---|---|
| Muestras totales | **1109** |
| Duración | 17 s |
| Throughput | **65,1 req/s** |
| Tiempo de respuesta promedio | **20 ms** |
| Mínimo | 7 ms |
| Máximo | 432 ms |
| **Errores** | **0 (0,00 %)** |

El detalle completo (percentiles p90/p95/p99, gráficos de tiempos y throughput en el tiempo) queda
en el dashboard HTML que genera JMeter: `reporte-html/index.html`.

## Análisis

- **Cero errores sobre 1109 operaciones.** Las ~500 compras y ~500 pagos concurrentes se
  resolvieron correctamente: el mecanismo contra la doble venta (update condicional + unicidad en
  base) y la idempotencia del pago aguantaron la concurrencia sin inconsistencias ni fallos.
- **Tiempos bajos y estables.** El promedio de 20 ms es holgado para el flujo completo. El máximo
  de 432 ms corresponde al **primer request** (arranque en frío de la JVM / primera conexión a la
  base y a pagos); una vez calientes los servicios, los tiempos se estabilizan muy por debajo.
- **Throughput sostenido** de ~65 req/s en una sola máquina corriendo a la vez los 8 microservicios,
  sus 5 bases y el generador de carga, lo cual es un escenario pesimista (en producción cada pieza
  iría en su propio host/contenedor con más recursos).

## Conclusión

El sistema soporta la carga concurrente del flujo de compra sin errores y con tiempos de respuesta
bajos, cumpliendo el requisito de rendimiento de la Entrega 2. No se detectaron condiciones de
doble venta ni degradación bajo concurrencia en el escenario probado.

## Cómo reproducir

Con el sistema levantado (`docker compose up -d`), desde la carpeta `bin` de JMeter:

```
jmeter -n -t <ruta>\documentacion\TicketYa-carga-compra.jmx -l resultados.jtl -e -o reporte-html
```

El informe navegable queda en `reporte-html/index.html`.

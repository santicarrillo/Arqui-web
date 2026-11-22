# Checklist — Entrega 1 (TicketYa)

Estado de cada tarea pedida por el enunciado.

## Features y User Stories
- [x] Features + user stories de todo el sistema, con criterios de aceptación → `documentacion/01-user-stories.md`

## Arquitectura de microservicios
- [x] Micros, responsabilidades y API pública → `documentacion/02-arquitectura.md`
- [x] Diagrama de módulos → `documentacion/diagramas/diagrama-modulos.svg`
- [x] Diagrama de secuencia del flujo de compra (con pago rechazado y reserva vencida) → `documentacion/diagramas/diagrama-secuencia.svg`

## Modelo de datos
- [x] ER por microservicio + entidades solapadas → `documentacion/03-modelo-datos.md`, `documentacion/diagramas/diagrama-er*.svg`

## Diseño de API REST
- [x] Listado de endpoints (método, ruta, parámetros, códigos, ejemplos) → `documentacion/04-api-rest.md`
- [x] OpenAPI/Swagger → springdoc en cada micro (`/swagger-ui.html`, `/v3/api-docs`)

## Implementación
- [x] Microservicios + API Gateway de la funcionalidad de la entrega → usuarios, eventos, lugares, ventas, pagos, gateway
- [x] README de ejecución + documentación técnica → `README.md`, `documentacion/`

## Testing
- [x] Pruebas unitarias de lógica de negocio (compra, devoluciones, búsqueda, usuarios, estados)
- [x] Prueba de no-doble-venta (muchas compras simultáneas, solo una gana) → `ms-ventas`

## Entregable (repositorio)
- [x] Código fuente
- [x] Documentación de diseño → `documentacion/`
- [x] README con instrucciones de ejecución
- [x] Datos de prueba → la colección de Postman los crea al correrla
- [x] Colección de requests → `documentacion/TicketYa.postman_collection.json`

## Observaciones / a coordinar con el grupo
- [ ] **Integración publicación → disponibilidad**: hoy es un paso manual en el Postman. Decidir si lo
      dispara eventos al publicar o lo resuelve ventas (ver nota en `documentacion/02-arquitectura.md`).
- [ ] **Smoke test de punta a punta**: levantar todo (`docker compose up -d` + los 6 apps) y correr la
      colección de Postman para confirmar el flujo integrado.
- [i] `ms-pagos` está fuera del estándar del grupo (proyecto independiente, Java 17, JPA). Funciona; documentado.
- [i] Se corrigieron archivos de otros integrantes para que el sistema arranque: `docker-compose.yml`
      (puertos/credenciales de las bases) y `api-gateway` (ruta de usuarios y devoluciones). Avisar al grupo.

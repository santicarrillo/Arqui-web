# Lugares Service (`ms-lugares`) & API Gateway

**Integrante C - Arquitecturas Web (TUDAI, UNICEN)**  
**Entrega 1 - TicketYa**

Este módulo contiene la implementación del catálogo físico de lugares y sectores (`ms-lugares`) junto con el punto único de entrada del sistema (`api-gateway`), cumpliendo con la especificación de la **US-04** y los contratos arquitectónicos de la Entrega 1.

---

## 1. Módulos asignados y Puertos

| Módulo | Puerto | Base de Datos | Responsabilidad |
| :--- | :--- | :--- | :--- |
| **`ms-lugares`** | `8084` | PostgreSQL (`localhost:5435/lugares`) | Catálogo de estadios/teatros, sectores (numerados y zonas) y generación masiva de asientos. |
| **`api-gateway`** | `8080` | *Sin base* | Punto de entrada único, ruteo síncrono por prefijos y logging de auditoría en consola. |

---

## 2. Decisiones de Diseño y Arquitectura (ADR)

### A. Persistencia con Spring JDBC (`JdbcTemplate`) en lugar de JPA/Hibernate
* **Control de Concurrencia y Simplicidad:** Evita la sobrecarga de memoria del EntityManager, el dirty-checking automático y las excepciones de bloqueo optimista bajo alta demanda.
* **Inserción Masiva (`batchUpdate`):** Al dar de alta sectores numerados de 1.000+ butacas, `jdbc.batchUpdate` envía un único mensaje empaquetado de red TCP a PostgreSQL. Se resuelven miles de inserts en ~10 ms sin instanciar entidades administradas en memoria.
* **Coherencia de equipo:** Mantiene el mismo enfoque que `ms-ventas` y `ms-eventos`.

### B. Modelo Relacional y Restricciones de Dominio (`schema.sql`)
* **Identificadores:** `BIGSERIAL` (64 bits, compatible con `Long` en Java).
* **Integridad Referencial:** `ON DELETE CASCADE` en `sector` y `asiento` para que si se remueve un lugar de prueba, se limpien en cascada sus dependencias sin dejar registros huérfanos.
* **Garantía de No Duplicación:** Restricción `CONSTRAINT uq_asiento_sector_fila_numero UNIQUE (sector_id, fila, numero)`. Delega al motor de base de datos la imposibilidad de repetir una butaca.
* **Optimización de Lecturas (Índices B-Tree):** Índices en `lugar_id` y `sector_id`. Como `ms-lugares` es un catálogo (99% lecturas vs 1% escrituras), los índices transforman el escaneo de tabla $\mathcal{O}(N)$ en búsqueda logarítmica $\mathcal{O}(\log N)$ para alimentar rápidamente a `ms-ventas`.

### C. Manejo Semántico de Errores (`ApiException`)
* Hereda de `RuntimeException` (Unchecked Exception): dispara automáticamente el `ROLLBACK` de transacciones en Spring (`@Transactional`) ante cualquier error de negocio, garantizando las propiedades **ACID** (Atomicidad y Consistencia).
* Evita el error genérico `500 Internal Server Error`, asociando cada caso a su código HTTP canónico:
  * `400 Bad Request` (`ApiException.badRequest`): Datos incompletos o inválidos.
  * `404 Not Found` (`ApiException.notFound`): ID de lugar o sector inexistente.
  * `409 Conflict` (`ApiException.conflict`): Choque de estado o duplicados.
* Manejador centralizado `@RestControllerAdvice` (`ApiExceptionHandler`) que formatea todos los errores al contrato estándar:
  ```json
  { "error": "LUGAR_NO_ENCONTRADO", "mensaje": "No existe el lugar con id: 99" }
  ```

### D. API Gateway Reactivo y Filtro de Auditoría (`LoggingFilter`)
* Implementado con **Spring Cloud Gateway** sobre **Netty** (no bloqueante, ultra liviano para alto tráfico).
* **Seguridad de servicios internos:** `pagos-service` (8085) y endpoints internos de ventas (`/interno/**`) no están expuestos en el Gateway, aislándolos del exterior.
* **`LoggingFilter` (`GlobalFilter`):** Utiliza la reactividad de Spring WebFlux (`Mono.fromRunnable`) para interceptar solicitudes entrantes y salientes sin bloquear hilos del sistema operativo, registrando en consola:
  * Ingreso: `--> [GATEWAY IN] POST /compras`
  * Egreso: `<-- [GATEWAY OUT] POST /compras - Status: 201 (32 ms)`

---

## 3. Endpoints de `ms-lugares` (Puerto 8084 o vía Gateway 8080)

### 1. Crear un Lugar Físico
* **`POST /lugares`**
* **Request Body:**
  ```json
  {
    "nombre": "Estadio UNO Jorge Luis Hirschi",
    "direccion": "Calle 1 y 57",
    "latitud": -34.9125,
    "longitud": -57.9392,
    "ciudad": "La Plata"
  }
  ```
* **Response (201 Created):**
  ```json
  { "id": 1 }
  ```

### 2. Consultar un Lugar por ID
* **`GET /lugares/{id}`**
* **Response (200 OK):**
  ```json
  {
    "id": 1,
    "nombre": "Estadio UNO Jorge Luis Hirschi",
    "direccion": "Calle 1 y 57",
    "latitud": -34.9125,
    "longitud": -57.9392,
    "ciudad": "La Plata"
  }
  ```

### 3. Crear Sector en un Lugar
* **`POST /lugares/{id}/sectores`**
* **Caso A: Sector Numerado (con generación automática de butacas)**
  ```json
  {
    "nombre": "Platea Premium",
    "numerado": true,
    "totalFilas": 10,
    "asientosPorFila": 20
  }
  ```
  *(Genera 200 asientos automáticamente del F1-A1 al F10-A20 y calcula capacidad = 200).*
* **Caso B: Zona No Numerada (por cupo/capacidad)**
  ```json
  {
    "nombre": "Campo General",
    "numerado": false,
    "capacidad": 5000
  }
  ```
* **Response (201 Created):**
  ```json
  { "id": 10 }
  ```

### 4. Listar Sectores de un Lugar
* **`GET /lugares/{id}/sectores`**
* **Response (200 OK):**
  ```json
  [
    { "id": 10, "lugarId": 1, "nombre": "Platea Premium", "numerado": true, "capacidad": 200 },
    { "id": 11, "lugarId": 1, "nombre": "Campo General", "numerado": false, "capacidad": 5000 }
  ]
  ```

### 5. Consultar Asientos de un Sector (Contrato para `ms-ventas`)
* **`GET /sectores/{id}/asientos`**
* **Response (200 OK):**
  ```json
  [
    { "id": 101, "fila": "1", "numero": "1" },
    { "id": 102, "fila": "1", "numero": "2" },
    { "id": 103, "fila": "1", "numero": "3" }
  ]
  ```

---

## 4. Ruteo en `api-gateway` (Puerto 8080)

| Ruta Externa | Microservicio Destino | Puerto |
| :--- | :--- | :--- |
| `http://localhost:8080/usuarios/**` | `usuarios-service` | `8081` |
| `http://localhost:8080/eventos/**` | `eventos-service` | `8082` |
| `http://localhost:8080/compras/**` | `ventas-service` | `8083` |
| `http://localhost:8080/entradas/**` | `ventas-service` | `8083` |
| `http://localhost:8080/disponibilidad/**`| `ventas-service` | `8083` |
| `http://localhost:8080/lugares/**` | `lugares-service` | `8084` |
| `http://localhost:8080/sectores/**` | `lugares-service` | `8084` |

---

## 5. Instrucciones de Ejecución

### 1. Base de Datos de Lugares (Docker)
Parado en `Tp-Especial-Entrega1/ms-lugares/`:
```bash
docker compose up -d
```
*(Levanta PostgreSQL 16 Alpine en el puerto `5435`).*

### 2. Ejecutar `ms-lugares`
Desde IntelliJ IDEA ejecutando `LugaresApp.java` o por Maven:
```bash
./mvnw spring-boot:run -pl ms-lugares
```
* Swagger UI disponible en: `http://localhost:8084/swagger-ui.html`

### 3. Ejecutar `api-gateway`
Desde IntelliJ IDEA ejecutando `GatewayApp.java` o por Maven:
```bash
./mvnw spring-boot:run -pl api-gateway
```

### 4. Ejecución de Pruebas Unitarias
Las pruebas corren 100% aisladas en memoria sin requerir Docker:
```bash
./mvnw test -pl ms-lugares
```

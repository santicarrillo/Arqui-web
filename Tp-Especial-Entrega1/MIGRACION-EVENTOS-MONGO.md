# Migración de ms-eventos a MongoDB: qué se hizo

Fecha: 2026-11-21 · Rama: `feature/eventos-mongo` · **Sin commit ni push**

## Resumen

`ms-eventos` pasó de PostgreSQL (JDBC) a MongoDB. El resto de los servicios (ventas, usuarios, lugares,
pagos, gateway) no se tocó. El contrato que consume ventas, `GET /eventos/{id}`, devuelve el mismo JSON
que antes.

- Tests: **13/13 pasando** (`mvn -pl ms-eventos -am verify` → BUILD SUCCESS).
- Pruebas con la app levantada: **las 6 dieron bien**.
- No hizo falta corregir código: los archivos descargados compilaron y pasaron los tests tal como venían.

---

## 0. Verificación previa

- Rama actual: `feature/eventos-mongo` (no es `main`).
- `git status` limpio antes de empezar.

## 1. Archivos aplicados

Se copiaron desde `C:\Users\Santiago\Downloads\Tp-Especial-Entrega1` sobre el repo, reemplazando los existentes:

| Archivo | Estado |
|---|---|
| `README.md` | modificado |
| `docker-compose.yml` | modificado |
| `documentacion/03-modelo-datos.md` | modificado |
| `documentacion/05-mongodb-eventos.md` | **nuevo** |
| `ms-eventos/docker-compose.yml` | modificado |
| `ms-eventos/pom.xml` | modificado |
| `ms-eventos/src/main/java/eventos/EventosApplication.java` | modificado |
| `ms-eventos/src/main/java/eventos/Domains/Evento.java` | modificado |
| `ms-eventos/src/main/java/eventos/Domains/EventoSector.java` | modificado |
| `ms-eventos/src/main/java/eventos/Domains/Participante.java` | modificado |
| `ms-eventos/src/main/java/eventos/repository/EventoRepository.java` | modificado |
| `ms-eventos/src/main/java/eventos/service/EventoService.java` | modificado |
| `ms-eventos/src/main/resources/application.yml` | modificado |
| `ms-eventos/src/test/java/eventos/service/EventoServiceTest.java` | modificado |
| `ms-eventos/src/test/java/integration/EventoIntegrationTest.java` | modificado |
| `ms-eventos/src/main/resources/schema.sql` | **borrado** (ya no se usa) |

Lo que **no** se copió:
- La carpeta `.github/modernize/...` que estaba en Descargas, porque no figuraba en la lista.
- El `LEEME.txt` mencionado en las instrucciones no estaba en la carpeta.

## 2. Revisión de completitud y coherencia

**Configuración con autenticación: coincide.**

Los dos `docker-compose.yml`, el general y el de `ms-eventos`, tienen:

```yaml
eventos-db:
  image: mongo:7.0.11
  container_name: eventos-db
  command: ["--auth"]
  environment:
    MONGO_INITDB_ROOT_USERNAME: root
    MONGO_INITDB_ROOT_PASSWORD: password
    MONGO_INITDB_DATABASE: eventos
  ports:
    - "127.0.0.1:27017:27017"
```

`ms-eventos/src/main/resources/application.yml`:

```yaml
spring:
  data:
    mongodb:
      uri: ${EVENTOS_MONGO_URI:mongodb://root:password@localhost:27017/eventos?authSource=admin}
      auto-index-creation: true
```

**Sin restos de la base relacional: OK.**

Se buscó `JdbcTemplate`, `spring-boot-starter-jdbc`, `postgresql`, `@Transactional` y `schema.sql` en
`ms-eventos`, sin contar `target/`. No queda ningún uso real. La única coincidencia es un **comentario** en
`EventoService.java:15` que explica por qué se sacó `@Transactional`: con MongoDB solo, no hay gestor de
transacciones y cada cambio toca un único documento. Se dejó como está porque documenta la decisión.

**API sin cambios: OK.**

`eventos/api/Dtos.java`, `EventoController.java` y `ApiExceptionHandler.java` no tienen diferencias con el commit anterior.

**Corrección en `documentacion/02-arquitectura.md`**

Es el único cambio hecho a mano. Se modificó solo esta línea; el resto del archivo quedó igual.

Antes:
> Persistencia relacional (PostgreSQL) con **JDBC** (`JdbcTemplate`) en todos los micros salvo `ms-pagos`, que usa JPA.

Después:
> Persistencia relacional (PostgreSQL) en todos los micros salvo `ms-eventos`, que usa **MongoDB**
> (ver 05-mongodb-eventos.md). Los relacionales usan **JDBC** (`JdbcTemplate`), salvo `ms-pagos`, que usa JPA.

La línea original ya tenía la excepción de `ms-pagos`, así que se conservó y se agregó la de eventos.

## 3. Compilación y tests

Comando, desde `Tp-Especial-Entrega1`:

```
mvn -pl ms-eventos -am verify
```

| Clase de test | Resultado |
|---|---|
| `eventos.service.EventoServiceTest` (unitario) | 7/7 ✅ |
| `integration.EventoIntegrationTest` (Testcontainers + MongoDB) | 6/6 ✅ |
| **Total** | **13 tests, 0 fallos, 0 errores** |

Detalles del entorno:
- **Maven no está en el PATH.** Se usó el que trae IntelliJ:
  `C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2024.2.0.1\plugins\maven\lib\maven3\bin\mvn.cmd`,
  con `JAVA_HOME=C:\Users\Santiago\.jdks\temurin-21.0.12.1`.
- **Docker Desktop estaba cerrado** y se abrió. La versión del engine es 29.7.2.
- `C:\Users\Santiago\.docker-java.properties` ya tenía `api.version=1.44`, así que Testcontainers funcionó sin tocar nada.
- El test de integración tarda unos 2 minutos y medio porque levanta su propio contenedor de Mongo.

## 4. Prueba con la app levantada

Pasos:
1. `docker compose down` y `docker compose up -d` desde `Tp-Especial-Entrega1`. El `down` hace falta para
   que Mongo cree el usuario root, que solo se crea con la base vacía.
2. Se corrió `EventosApplication` desde el jar compilado
   (`java -jar ms-eventos/target/ms-eventos-1.0-SNAPSHOT.jar`).
3. Se probó directo contra `http://localhost:8082`, sin pasar por el gateway.

Nota: al arrancar, el log muestra una excepción `MongoSocketReadException: Prematurely reached end of stream`.
Es normal: Mongo se reinicia después de crear el usuario root. El driver reconecta solo y la app termina
de levantar en unos 24 segundos.

### Resultados

| # | Request | Resultado |
|---|---|---|
| 1 | `POST /eventos` con el evento "Prueba" | **201** `{"id":1}` |
| 2 | `POST /eventos/1/sectores` `[{"sectorId":12,"numerado":true,"precio":30000}]` | **200** |
| 3 | `POST /eventos/1/participantes` `[{"nombre":"Los Piojos","tipo":"ARTISTA"}]` | **200** |
| 4 | `PUT /eventos/1/publicacion` | **200** |
| 5 | `GET /eventos/1` | **200**, ver abajo |
| 6a | `GET /eventos?participante=piojos` | **200**, el evento aparece |
| 6b | `GET /eventos?orden=precio` | **200**, el evento aparece |

Respuesta de `GET /eventos/1`, el contrato que consume ventas. Tiene exactamente `id`, `estado`,
`fechaHora`, `limitePorCliente` y `sectores[{sectorId, numerado, precio}]`, sin campos de más:

```json
{"id":1,"estado":"PUBLICADO","fechaHora":"2026-12-20T21:00:00","limitePorCliente":6,
 "sectores":[{"sectorId":12,"numerado":true,"precio":30000}]}
```

Respuesta de los listados (6a y 6b):

```json
[{"id":1,"nombre":"Prueba","tipo":"RECITAL","fechaHora":"2026-12-20T21:00:00","ciudad":"Tandil","estado":"PUBLICADO"}]
```

### Cómo quedó guardado en MongoDB

```
docker exec -it eventos-db mongosh -u root -p password --authenticationDatabase admin eventos --eval "db.eventos.find()"
```

```js
{
  _id: Long('1'),
  nombre: 'Prueba',
  tipo: 'RECITAL',
  fechaHora: ISODate('2026-12-21T00:00:00.000Z'),
  estado: 'PUBLICADO',
  lugarId: Long('1'),
  ciudad: 'Tandil',
  organizadorId: Long('1'),
  limitePorCliente: 6,
  sectores: [ { sectorId: Long('12'), numerado: true, precio: Decimal128('30000') } ],
  participantes: [ { nombre: 'Los Piojos', tipo: 'ARTISTA' } ],
  _class: 'eventos.Domains.Evento'
}
```

- ✅ El `_id` es **numérico** (`Long`).
- ✅ El precio quedó como **`Decimal128`** (NumberDecimal), no como texto. Se confirmó también con
  `{"sectores.precio": {$type: "decimal"}}`, que devuelve 1 documento.
- ℹ️ `fechaHora` se guarda en **UTC**: 21:00 en Argentina son las 00:00Z del día siguiente. Es el
  comportamiento normal de Mongo, y la API la devuelve en hora local (`2026-12-20T21:00:00`), igual que antes.
- Sectores y participantes quedan **adentro del documento del evento**, en lugar de en tablas aparte.

## 5. Estado final

- `git status`: 15 archivos modificados, `schema.sql` borrado y `documentacion/05-mongodb-eventos.md` sin
  seguimiento. También aparece este archivo (`MIGRACION-EVENTOS-MONGO.md`) como no seguido.
  **No se hizo `git add`, commit ni push.**
- Los avisos de git sobre LF/CRLF son normales en Windows y no afectan nada.
- La app de eventos quedó frenada. Los contenedores de `docker compose` siguen levantados; para bajarlos:
  `docker compose down`.
- `docker compose down` / `up -d` recreó también los contenedores de las otras bases (ventas, usuarios,
  lugares, pagos), porque así lo pedía el procedimiento.

## Cambios además de copiar los archivos

1. Se borró `ms-eventos/src/main/resources/schema.sql`, como estaba pedido.
2. Se corrigió una línea de `documentacion/02-arquitectura.md` (ver sección 2).
3. Se creó este archivo de resumen.

No se modificó código Java ni configuración más allá de lo copiado.

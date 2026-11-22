# Entrega 2 — F13 · Pruebas de seguridad (JWT y roles)

Documentación del **Bloque 1 de F13**: pruebas automatizadas que verifican la autenticación con JWT en el `api-gateway` y el control de autorización por roles (`401` / `403` / `200`) en los microservicios.

---

## 1. Qué se prueba y dónde vive cada suite

Ninguna de estas suites requiere Docker ni levantar la base de datos: corren en segundos con Maven o desde IntelliJ.

| Módulo | Test | Qué verifica |
|---|---|---|
| `api-gateway` | `src/test/java/gateway/AuthFilterTest.java` | Validación centralizada de JWT en `AuthFilter`, limpieza de headers falsificados, rechazo `401` antes de rutear, propagación de `X-Usuario-Id` / `X-Rol` y bloqueo de rutas `/interno/**` (`404`). |
| `ms-ventas` | `src/test/java/api/SeguridadRolesTest.java` | Autorización por rol (`403` vs `200`) en capa HTTP (`MockMvc`) sobre devoluciones, ingresos QR, reportes del organizador y fila virtual, incluyendo validación de dueño del evento (`403 NO_ES_TU_EVENTO`). |
| `ms-preferencias` | `src/test/java/api/SeguridadRolesTest.java` | Autorización por rol (`403 SOLO_CLIENTES` vs `200`) en `GET /preferencias`, `PUT /preferencias` y `GET /recomendaciones`. |
| `ms-usuarios` | `src/test/java/usuarios/service/UsuarioServiceTest.java` | Credenciales inválidas (`401`) en el login y almacenamiento con hash BCrypt *(ya existente en F0/F1)*. |

---

## 2. Casos cubiertos en `api-gateway` (`AuthFilterTest`)

El test levanta `GatewayApp` en puerto aleatorio apuntando a un servidor HTTP downstream en memoria (sin levantar los microservicios reales) y verifica:

1. **Rutas públicas (`POST /usuarios/login`, `POST /usuarios`, `OPTIONS`):**
   * Pasan sin header `Authorization`.
   * Si el cliente intenta enviar `X-Usuario-Id` o `X-Rol` falsificados, el gateway los elimina antes de reenviar la petición.
2. **Rutas protegidas sin token (`401 Unauthorized`, `X-Auth-Error: FALTA_TOKEN`):**
   * Probado sobre las 15 rutas expuestas (`/usuarios`, `/usuarios/1`, `/eventos`, `/lugares`, `/sectores/1/asientos`, `/compras`, `/entradas`, `/disponibilidad`, `/devoluciones`, `/ingresos`, `/reportes/eventos/1`, `/filas/1`, `/preferencias`, `/recomendaciones`, `/notificaciones`).
   * Se verifica que la petición **nunca** llega al microservicio downstream.
3. **Tokens inválidos (`401 Unauthorized`):**
   * Header `Authorization` sin prefijo `Bearer ` (`Basic ...`) → `401` (`FALTA_TOKEN`).
   * JWT firmado con otra clave secreta → `401` (`TOKEN_INVALIDO`).
   * JWT malformado → `401` (`TOKEN_INVALIDO`).
   * JWT vencido → `401` (`TOKEN_INVALIDO`).
4. **Propagación confiable de identidad (`200 OK`):**
   * Con JWT válido para cada rol (`CLIENTE`, `ORGANIZADOR`, `ADMINISTRADOR`, `ACCESO`), el gateway inyecta `X-Usuario-Id` y `X-Rol` desde el payload firmado y **sobrescribe** cualquier header de identidad falsificado que envíe el cliente.
5. **Aislamiento de rutas internas (`404 Not Found`):**
   * Las rutas `/interno/disponibilidad`, `/interno/eventos/1/cancelacion`, `/interno/compras`, `/interno/interesados` y `/interno/avisos/compra-confirmada` no tienen ruta en el gateway y devuelven `404` incluso con un JWT válido de `ADMINISTRADOR`.

---

## 3. Matriz de roles verificada por endpoint

| Endpoint | Sin token | `CLIENTE` | `ORGANIZADOR` | `ADMINISTRADOR` | `ACCESO` | Estado |
|---|:--:|:--:|:--:|:--:|:--:|---|
| `POST /usuarios/login` | 200 (público) | — | — | — | — | Cubierto (`AuthFilterTest`, `UsuarioServiceTest`) |
| `POST /usuarios` | 200 (público) | — | — | — | — | Cubierto (`AuthFilterTest`) — *ver pendiente F1* |
| `GET /devoluciones` | 401 | 403 | 403 | **200** | 403 | Cubierto (`ms-ventas/SeguridadRolesTest`) |
| `PUT /entradas/{id}/devolucion` | 401 | 403 | 403 | **200** | 403 | Cubierto (`ms-ventas/SeguridadRolesTest`) |
| `POST /ingresos` | 401 | 403 | 403 | **200** | **200** | Cubierto (`ms-ventas/SeguridadRolesTest`) |
| `GET /reportes/eventos/{id}` | 401 | 403 | **200** dueño / 403 ajeno | **200** | 403 | Cubierto (`ms-ventas/SeguridadRolesTest`) |
| `PUT /filas/{eventoId}` | 401 | 403 | **200** dueño / 403 ajeno | **200** | 403 | Cubierto (`ms-ventas/SeguridadRolesTest`) |
| `DELETE /filas/{eventoId}` | 401 | 403 | **200** dueño / 403 ajeno | **200** | 403 | Cubierto (`ms-ventas/SeguridadRolesTest`) |
| `GET /preferencias` | 401 | **200** | 403 | 403 | 403 | Cubierto (`ms-preferencias/SeguridadRolesTest`) |
| `PUT /preferencias` | 401 | **200** | 403 | 403 | 403 | Cubierto (`ms-preferencias/SeguridadRolesTest`) |
| `GET /recomendaciones` | 401 | **200** | 403 | 403 | 403 | Cubierto (`ms-preferencias/SeguridadRolesTest`) |
| `/interno/**` (vía gateway) | 401 | 404 | 404 | 404 | 404 | Cubierto (`AuthFilterTest`) |

---

## 4. Cómo ejecutar estas pruebas

Desde la carpeta `Tp-Especial-Entrega2/`:

```bash
mvn -pl api-gateway,ms-ventas,ms-preferencias -Dtest=AuthFilterTest,SeguridadRolesTest -DfailIfNoTests=false test
```

En IntelliJ también pueden ejecutarse individualmente haciendo clic en **Run** sobre cada clase de test (`AuthFilterTest` y ambos `SeguridadRolesTest`).

---

## 5. Pendiente de ampliar cuando se cierre F1

Según lo indicado en [06-entrega2-fase1.md](06-entrega2-fase1.md) (*Avisos para el grupo*), todavía resta cerrar la autorización por rol en el gateway y en los servicios de catálogo/usuarios:

* Restricción en el registro público (`POST /usuarios`) para impedir el autoregistro como `ADMINISTRADOR` / `ACCESO`.
* Control de roles en `ms-eventos` (`POST /eventos`, `POST /eventos/{id}/sectores`, `POST /eventos/{id}/participantes`, `PUT /eventos/{id}/publicacion`, `PUT /eventos/{id}/cancelacion` → `ORGANIZADOR` / `ADMINISTRADOR`).
* Control de roles en `ms-lugares` (`POST /lugares`, `POST /lugares/{id}/sectores` → `ADMINISTRADOR` / `ORGANIZADOR`).
* Control de roles en `ms-usuarios` (`GET /usuarios` → `ADMINISTRADOR`) y en `ms-ventas` (`/compras/**`, `/entradas/**` → `CLIENTE`).

Apenas se mergee el cierre de **F1**, se suman los casos `403` correspondientes en `AuthFilterTest` (si las reglas viven en el gateway) o en los `SeguridadRolesTest` de cada microservicio.

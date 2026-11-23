# Entrega 2 — F13 · Pruebas de seguridad (JWT y roles)

Documentación del **Bloque 1 de F13**: pruebas automatizadas que verifican la autenticación con JWT en el `api-gateway` y el control de autorización por roles (`401` / `403` / `200`) en los microservicios.

---

## 1. Qué se prueba y dónde vive cada suite

Ninguna de estas suites requiere Docker ni levantar la base de datos: corren en segundos con Maven o desde IntelliJ.

| Módulo | Test | Qué verifica |
|---|---|---|
| `api-gateway` | `src/test/java/gateway/AuthFilterTest.java` | Validación centralizada de JWT en `AuthFilter`, limpieza de headers falsificados, rechazo `401` antes de rutear, propagación de `X-Usuario-Id` / `X-Rol` y bloqueo de rutas `/interno/**` (`404`). |
| `ms-ventas` | `src/test/java/api/SeguridadRolesTest.java` | Autorización por rol (`403` vs `200`) en capa HTTP (`MockMvc`) sobre devoluciones, ingresos QR, reportes del organizador y fila virtual, incluyendo validación de dueño del evento (`403 NO_ES_TU_EVENTO`). F1: compras, entradas, pedido de devolución y turnos solo `CLIENTE` (`403 SOLO_CLIENTES`). |
| `ms-preferencias` | `src/test/java/api/SeguridadRolesTest.java` | Autorización por rol (`403 SOLO_CLIENTES` vs `200`) en `GET /preferencias`, `PUT /preferencias` y `GET /recomendaciones`. |
| `ms-usuarios` | `src/test/java/usuarios/service/UsuarioServiceTest.java` | Credenciales inválidas (`401`) en el login y almacenamiento con hash BCrypt *(ya existente en F0/F1)*. |
| `ms-usuarios` | `src/test/java/usuarios/api/SeguridadRolesTest.java` | Registro público solo como `CLIENTE` (`403 SOLO_ADMIN_CREA_ROLES`), alta de otros roles por el admin, lista solo admin (`403 SOLO_ADMIN`) y consulta del propio usuario o del admin (`403 NO_ES_TU_USUARIO`). |
| `ms-usuarios` | `src/test/java/usuarios/config/AdministradorInicialTest.java` | El administrador inicial se crea una sola vez, con rol `ADMINISTRADOR` y la clave hasheada. |
| `ms-eventos` | `EventoServiceTest`, `integration/EventoIntegrationTest` | Crear y modificar eventos solo `ORGANIZADOR` / `ADMINISTRADOR` (`403 SOLO_ORGANIZADOR`); un organizador solo toca sus eventos (`403 NO_ES_TU_EVENTO`) y crea a su nombre; el admin puede con cualquiera. |
| `ms-lugares` | `src/test/java/lugares/api/SeguridadRolesTest.java` | Alta de lugares y sectores solo `ADMINISTRADOR` / `ORGANIZADOR` (`403 SOLO_ADMIN_U_ORGANIZADOR`); las consultas quedan abiertas. |

---

## 2. Casos cubiertos en `api-gateway` (`AuthFilterTest`)

El test levanta `GatewayApp` en puerto aleatorio apuntando a un servidor HTTP downstream en memoria (sin levantar los microservicios reales) y verifica:

1. **Rutas públicas (`POST /usuarios/login`, `POST /usuarios`, `OPTIONS`):**
   * Pasan sin header `Authorization`.
   * Si el cliente intenta enviar `X-Usuario-Id` o `X-Rol` falsificados, el gateway los elimina antes de reenviar la petición.
   * **F1:** `POST /usuarios` con `Authorization: Bearer ...` (un admin creando otros roles) valida el token como una ruta protegida: si es válido propaga `X-Usuario-Id` / `X-Rol` del token; si no, `401 TOKEN_INVALIDO` sin llegar a `ms-usuarios`.
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
| `POST /usuarios` rol `CLIENTE` | **201** (público) | **201** | **201** | **201** | **201** | Cubierto (`ms-usuarios/SeguridadRolesTest`, `AuthFilterTest`) |
| `POST /usuarios` rol `ORGANIZADOR` / `ACCESO` / `ADMINISTRADOR` | 403 | 403 | 403 | **201** | 403 | Cubierto (`ms-usuarios/SeguridadRolesTest`, `AuthFilterTest`) |
| `GET /usuarios` | 401 | 403 | 403 | **200** | 403 | Cubierto (`ms-usuarios/SeguridadRolesTest`) |
| `GET /usuarios/{id}` | 401 | **200** propio / 403 ajeno | **200** propio / 403 ajeno | **200** | **200** propio / 403 ajeno | Cubierto (`ms-usuarios/SeguridadRolesTest`) |
| `POST /eventos` | 401 | 403 | **201** (a su nombre) | **201** | 403 | Cubierto (`EventoServiceTest`, `EventoIntegrationTest`) |
| `POST /eventos/{id}/sectores`, `/participantes` | 401 | 403 | **200** dueño / 403 ajeno | **200** | 403 | Cubierto (`EventoServiceTest`, `EventoIntegrationTest`) |
| `PUT /eventos/{id}/publicacion`, `/cancelacion` | 401 | 403 | **200** dueño / 403 ajeno | **200** | 403 | Cubierto (`EventoServiceTest`, `EventoIntegrationTest`) |
| `GET /eventos`, `GET /eventos/{id}` | 401 | **200** | **200** | **200** | **200** | Abiertos con token (ventas y preferencias los leen directo) |
| `POST /lugares`, `POST /lugares/{id}/sectores` | 401 | 403 | **201** | **201** | 403 | Cubierto (`ms-lugares/SeguridadRolesTest`) |
| `GET /lugares/**`, `GET /sectores/{id}/asientos` | 401 | **200** | **200** | **200** | **200** | Cubierto (`ms-lugares/SeguridadRolesTest`) |
| `POST /compras`, `POST /compras/{id}/pagar`, `GET` / `DELETE /compras/{id}` | 401 | **200** | 403 | 403 | 403 | Cubierto (`ms-ventas/SeguridadRolesTest`) |
| `GET /entradas`, `GET /entradas/{id}` | 401 | **200** | 403 | 403 | 403 | Cubierto (`ms-ventas/SeguridadRolesTest`) |
| `POST /entradas/{id}/devolucion` | 401 | **201** | 403 | 403 | 403 | Cubierto (`ms-ventas/SeguridadRolesTest`) |
| `POST` / `GET /filas/{eventoId}/turno` | 401 | **200** | 403 | 403 | 403 | Cubierto (`ms-ventas/SeguridadRolesTest`) |
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
mvn -pl api-gateway,ms-usuarios,ms-lugares,ms-ventas,ms-preferencias -Dtest=AuthFilterTest,SeguridadRolesTest,AdministradorInicialTest -DfailIfNoTests=false test
mvn -pl ms-eventos test    # incluye EventoIntegrationTest (necesita Docker por Testcontainers)
```

En IntelliJ también pueden ejecutarse individualmente haciendo clic en **Run** sobre cada clase de test (`AuthFilterTest` y los `SeguridadRolesTest` de cada servicio).

---

## 5. F1 cerrada

Lo que quedaba pendiente de [06-entrega2-fase1.md](06-entrega2-fase1.md) (*Avisos para el grupo*) ya está hecho y cubierto por tests:

* **Registro público restringido:** `POST /usuarios` sin token solo crea `CLIENTE`; los demás roles los crea un `ADMINISTRADOR` con su token (el gateway lo valida y propaga su rol). El primer administrador se carga al arrancar `ms-usuarios` (`ADMIN_EMAIL` / `ADMIN_PASSWORD`).
* **`ms-usuarios`:** `GET /usuarios` solo `ADMINISTRADOR`; `GET /usuarios/{id}` el propio usuario o un `ADMINISTRADOR`.
* **`ms-eventos`:** crear y modificar eventos solo `ORGANIZADOR` / `ADMINISTRADOR`, y el organizador solo los suyos.
* **`ms-lugares`:** alta de lugares y sectores solo `ADMINISTRADOR` / `ORGANIZADOR`.
* **`ms-ventas`:** compras, entradas, pedido de devolución y turnos de la fila virtual solo `CLIENTE`.

Las reglas viven en cada microservicio (el gateway solo autentica), así que los casos `403` están en los `SeguridadRolesTest` de cada servicio y en los tests de `ms-eventos`; `AuthFilterTest` suma el alta de usuarios con token.

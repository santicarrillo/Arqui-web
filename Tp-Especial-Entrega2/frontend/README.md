# TicketYa · GUI del cliente

Web en **React + Vite** con el flujo del cliente: iniciar sesión o crear cuenta → buscar eventos → elegir
sector y asientos → comprar y pagar → ver tus entradas con su QR. Le habla solo al **API Gateway**
(`http://localhost:8080`); nunca llama a un microservicio directo.

## Cómo levantarla

Requisito: **Node 20.19 o más nuevo** (recomendado Node 22 LTS) y el sistema andando (gateway en el 8080).

```bash
cd frontend
npm install        # la primera vez
npm run dev        # http://localhost:5173
```

Para usar otro gateway, copiar `.env.example` como `.env` y cambiar `VITE_API_URL`.

### Con Docker

Está en el `docker-compose.yml` con el perfil `web`, así no cambia el `docker compose up` de siempre:

```bash
docker compose --profile web up -d --build     # http://localhost:3000
```

## Para probarla

Crear una cuenta desde la pantalla de registro (queda como CLIENTE). Para que haya algo que comprar, primero
hay que crear un lugar, un evento y publicarlo: la colección `documentacion/TicketYa-E2.postman_collection.json`
lo hace en las carpetas 1 a 4.

### Usuarios de prueba

Todos con contraseña `1234`. El administrador ya viene cargado (ms-usuarios lo crea al arrancar); los demás los
crean las carpetas 1 y 2 de esa colección:

| Rol | Email | Cómo se crea | Para qué sirve |
|---|---|---|---|
| ADMINISTRADOR | `admin@a.com` | **Precargado** al arrancar ms-usuarios (`ADMIN_EMAIL` / `ADMIN_PASSWORD`) | **En la web**: pantalla "Devoluciones" para aprobar o rechazar los pedidos. Crea organizadores y personal de acceso, lugares y sectores (por Postman/Swagger). |
| ORGANIZADOR | `orga@a.com` | Lo crea el admin, con su token | Crea y publica sus eventos (por Postman/Swagger). |
| ACCESO | `acc@a.com` | Lo crea el admin, con su token | Valida los QR en la puerta (por Postman/Swagger). |
| CLIENTE | `cli@a.com` | Registro público | **El que se usa en la web**: busca eventos, compra y ve sus entradas. |

La web es para clientes, más la pantalla de devoluciones del administrador: con ORGANIZADOR o ACCESO se puede
iniciar sesión y ver los eventos, pero no comprar (el backend responde `403 SOLO_CLIENTES`) y la web lo avisa.
También se puede crear una cuenta nueva desde "Crear cuenta", que siempre queda como CLIENTE: el registro
público no permite otro rol.

Después de las carpetas 3 y 4 queda publicado "Recital de Prueba" (Tandil, sector Campo sin numerar, 100
lugares a $15.000), listo para comprar desde la web.

## Estructura

```
src/
  api.js           cliente HTTP: token, errores {error, mensaje}, sesion vencida (401)
  servicios.js     una funcion por endpoint del gateway
  catalogo.js      evento, lugar, sectores y asientos guardados en memoria mientras la pagina esta abierta
  sesion.jsx       login, registro y token JWT (localStorage)
  paginas/         Login, Registro, Eventos, EventoDetalle, Compra, MisEntradas
  componentes/     Layout, MapaAsientos, FilaVirtual, Contador, Entrada, Mensaje
```

Las decisiones de diseño están en `documentacion/09-gui.md`.

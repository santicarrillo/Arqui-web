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

# MongoDB en ms-eventos (Entrega 2)

La consigna pide que al menos un microservicio use una base NoSQL. Elegimos **MongoDB para el
catálogo de eventos** (`ms-eventos`). El resto de los servicios sigue en PostgreSQL.

## Por qué eventos y no otro servicio

- **Un evento se lee siempre entero.** Para mostrar un evento o para vender (ventas llama a
  `GET /eventos/{id}`) hacen falta sus datos, los precios de cada sector y los participantes. En
  PostgreSQL eso eran cuatro tablas (`evento`, `evento_sector`, `participante`,
  `evento_participante`) y varias consultas; en MongoDB es un solo documento y una sola lectura.
- **Es un catálogo: se lee mucho más de lo que se escribe.** El organizador lo arma una vez y después
  miles de clientes lo buscan y lo consultan.
- **Los datos varían según el tipo de evento.** Un recital tiene artistas, un partido tiene equipos,
  una conferencia tiene disertantes. Un documento admite esas diferencias sin cambiar el esquema.
- **Ventas, en cambio, necesita una base relacional.** La garantía de no doble venta se apoya en
  `UPDATE` condicionales, restricciones `UNIQUE`/`CHECK` y transacciones sobre varias filas (compra,
  detalle, asientos). Ese es el terreno de PostgreSQL, así que ventas no se tocó.

## El documento

```json
{
  "_id": 7,
  "nombre": "Recital de rock",
  "descripcion": "...",
  "tipo": "RECITAL",
  "fechaHora": "2026-12-01T21:00:00",
  "estado": "PUBLICADO",
  "lugarId": 3,
  "ciudad": "Tandil",
  "organizadorId": 12,
  "limitePorCliente": 6,
  "motivoCancelacion": null,
  "sectores": [
    { "sectorId": 12, "numerado": true,  "precio": 30000 },
    { "sectorId": 13, "numerado": false, "precio": 15000 }
  ],
  "participantes": [
    { "nombre": "Los Piojos", "tipo": "ARTISTA" }
  ]
}
```

**Qué va embebido y qué no:**

| Dato | Decisión | Motivo |
|---|---|---|
| Precios por sector | Embebidos | Pertenecen a ese evento, son pocos y se leen siempre con él (ventas los necesita en cada compra). |
| Participantes | Embebidos | Son pocos por evento, se muestran con el evento y la búsqueda por participante filtra dentro del arreglo. |
| Lugar | Solo `lugarId` y una copia de `ciudad` | El lugar es de ms-lugares. La ciudad se copia para poder buscar sin llamar a otro servicio. |

## Consistencia

Cada cambio sobre un evento (cargar precios, sumar participantes, publicar, cancelar) modifica **un
solo documento**, y en MongoDB una operación sobre un documento es atómica. Por eso el servicio ya no
usa transacciones (`@Transactional`).

Cargar dos veces el mismo sector actualiza su precio en el lugar del arreglo; si no estaba, se agrega
con una condición que evita duplicarlo aunque lleguen dos pedidos a la vez.

## Ids numéricos

MongoDB genera ids de tipo `ObjectId`, pero ventas y los demás servicios guardan `eventoId` como
número. Se usa una colección `contadores` con un incremento atómico (`findOneAndUpdate` con `$inc`),
así cada evento nuevo recibe el número siguiente y dos altas simultáneas nunca comparten id.

## Índices

- `{estado, fechaHora}`: la búsqueda siempre filtra por `PUBLICADO` y ordena por fecha.
- `tipo`, `ciudad` y `organizadorId`: los otros filtros de la búsqueda.

Se crean solos al arrancar (`spring.data.mongodb.auto-index-creation: true`).

## Acceso a la base

MongoDB corre con autenticación (`--auth`) y un usuario `root` que se crea en la base `admin` al
levantar el contenedor por primera vez. Por eso la cadena de conexión lleva `authSource=admin`:

```
mongodb://root:password@localhost:27017/eventos?authSource=admin
```

El puerto se publica solo en `127.0.0.1`, así que la base no queda abierta a otras máquinas de la red.
Cuando eventos corra en su propio contenedor, la conexión se pasa con la variable `EVENTOS_MONGO_URI`
usando el nombre del servicio como host (`eventos-db:27017`).

## El contrato con ventas no cambió

`GET /eventos/{id}` devuelve exactamente el mismo JSON que en la Entrega 1 (`id`, `estado`,
`fechaHora`, `limitePorCliente`, `sectores[{sectorId, numerado, precio}]`). Ventas no tuvo que
modificarse; el test de integración de eventos lo verifica. (En la F7 se agregó al final `organizadorId`, que
ventas usa para los reportes; agregar un campo no rompe a quien no lo lee. Para la GUI se agregaron, también
al final, `nombre`, `descripcion`, `tipo`, `ciudad`, `lugarId` y `participantes`: ver `09-gui.md`.)

## Simplificaciones

- La búsqueda por participante usa una expresión regular ("contiene", sin distinguir mayúsculas). Con
  mucho volumen convendría un índice de texto.
- El orden por precio se calcula en el servicio a partir de los sectores de cada evento. Con mucho
  volumen se haría en la base con una agregación (`$min` sobre `sectores.precio`).
- Los participantes se guardan por nombre, sin un id propio. Alcanza para buscar y para las
  notificaciones de "un artista favorito publicó un evento"; si hiciera falta distinguir homónimos se
  agregaría un id.

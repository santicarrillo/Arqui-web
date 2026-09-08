# Trabajo Practico Integrador

Este es el **esqueleto base**: la infraestructura ya esta hecha (entities, factory,
utils/HelperMySQL, Main cableado). Cada persona implementa **solo su metodo**,
marcado con `// TODO INCISO n`.

Patrones aplicados: **DAO + Abstract Factory + Singleton**.

---

## 1. Requisitos

- **Java 21** (JDK) e **IntelliJ IDEA**.
- **Docker Desktop** instalado.
- Maven (viene con IntelliJ).

---

## 2. Poner en marcha la base de datos (Docker)

### La primera vez (una sola vez)
1. Abrir **Docker Desktop** y esperar a que abajo a la izquierda diga **"Engine running"** (verde).
2. Crear el contenedor de MySQL (en una terminal / PowerShell):
   ```
   docker run --name mysql-jdbc -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=jdbcbasico -p 3306:3306 -d mysql:8.4
   ```
3. Crear la base del integrador:
   ```
   docker exec mysql-jdbc mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS integrador;"
   ```

### Todos los dias (rutina)
Al prender la compu el contenedor queda apagado. Solo hay que prenderlo:
```
docker start mysql-jdbc
```
Verificar que este arriba (tiene que aparecer `mysql-jdbc` con estado `Up`):
```
docker ps
```
Para apagarlo al terminar: `docker stop mysql-jdbc`.

> Si el programa Java tira **"Communications link failure"**, casi siempre es que falto
> el `docker start mysql-jdbc` o Docker Desktop no esta abierto.

---

## 3. Como correr el proyecto (paso a paso)

1. Abrir el proyecto en IntelliJ con **File -> Open** (elegir la carpeta del proyecto).
   No usar "New Project" (evita que IntelliJ cree un git anidado).
2. Poner **JDK 21**: File -> Project Structure -> Project -> SDK 21, Language level 21.
3. Hacer **Maven Reload** (panel Maven, boton de refrescar) para que baje las dependencias.
4. Asegurarse de que Docker este corriendo y el contenedor arriba (ver seccion 2).
5. Correr **`Main`** (boton derecho -> Run 'Main.main()').

`Main` hace, en orden: dropTables -> createTables (inciso 1) -> populateDB (inciso 2),
y despues las consultas (incisos 3 y 4). Como dropea y recrea en cada corrida,
**se puede correr las veces que haga falta sin errores de datos duplicados.**

---

## 4. Reparto de los incisos

Cada uno implementa SU metodo (no toca el de otro):

| Inciso | Archivo / metodo | Que hace |
|--------|------------------|----------|
| 1 | `utils/HelperMySQL.createTables()` | Crear las 4 tablas (HECHO) |
| 2 | `utils/HelperMySQL.populateDB()` | Cargar los CSV de src/main/resources |
| 3 | `dao/ProductoDAO.obtenerQueMasRecaudo()` | Producto que mas recaudo |
| 4 | `dao/ClienteDAO.obtenerPorFacturacion()` | Clientes ordenados por facturacion |

`HelperMySQL` lo comparten el inciso 1 y el 2: **coordinen entre esas dos personas**.
`Main` y `factory` **no se tocan** (son infraestructura comun).

---

## 5. Como trabajar con git (sin pisarse)

1. Clonar el repo **una vez**: `git clone <url>`.
2. Antes de arrancar SIEMPRE: `git checkout main` y `git pull`.
3. Crear tu rama: `git checkout -b inciso-<tu-inciso>`.
4. Implementar tu metodo. Commits chicos y seguidos:
   ```
   git add .
   git commit -m "Inciso 3: producto que mas recaudo"
   ```
5. Subir tu rama: `git push -u origin inciso-3`.
6. Abrir un **Pull Request** en GitHub y mergear a `main`.

---

## 6. Reglas de oro

1. **`git pull` antes de empezar y antes de cada `git push`.**
2. **No editar el metodo de otra persona** — cada uno el suyo.
3. **Commits chicos**, uno por cosa terminada, no uno gigante al final.
4. El `.gitignore` ya excluye `target/` y `.idea/`: **no subir esos** (generan conflictos en archivos que ni escribiste).

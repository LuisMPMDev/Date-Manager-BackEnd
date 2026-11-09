# DateManager API

API REST (Java 21, Spring Boot 3.5, SQL Server) que reemplaza el almacén local de la demo Angular: login real,
permisos verificados en el servidor, reservas sin doble agendamiento y enlaces de invitación para que los clientes
agenden sin cuenta.

El diseño completo está en `docs/superpowers/specs/2026-11-08-date-manager-api-java-design.md`.

## Requisitos

- **JDK 21** (`maven.compiler.release` = 21).
- **Maven** 3.6.3 o superior (no hay wrapper en el repositorio).
- **SQL Server 2022** con **TCP/IP habilitado** (puerto 1433) y **autenticación mixta** (Windows + SQL), porque la
  API entra con un login SQL. Dos bases vacías: `DateManager` (la aplicación) y `DateManagerTest` (las pruebas).

## 1. Preparar la base de datos

### Opción A: script de PowerShell (recomendada)

`scripts/preparar-sqlserver.ps1` prepara tu SQL Server local de una vez. **Ejecútalo como administrador**
(el script lo exige) desde esta carpeta:

```powershell
.\scripts\preparar-sqlserver.ps1
```

Qué cambia en tu equipo (nada más):

1. Habilita TCP/IP en la instancia con el puerto fijo 1433 y pasa a autenticación mixta.
2. **Reinicia el servicio** de esa instancia (se cae unos segundos).
3. Crea las bases `DateManager` y `DateManagerTest` si no existen.
4. Crea el login SQL `datemanager_app`, dueño solo de esas dos bases.
5. Escribe `.env.local` (ignorado por git) con las variables de conexión y un secreto JWT aleatorio.

Te pide confirmación (`SI`) y **la contraseña del login la escribes tú en el terminal**: no la pases por chat ni la
guardes en ningún archivo del repositorio. No toca otras bases. El script apunta a la instancia
`.\MSSQLSERVER01`; si la tuya se llama distinto, cambia la variable `$instancia` al inicio del script.

### Opción B: a mano en SSMS

Si prefieres no usar el script, habilita TCP/IP (puerto 1433) y la autenticación mixta con SQL Server Configuration
Manager y las propiedades del servidor, reinicia el servicio, y ejecuta esto conectado como administrador. Cambia
`<CLAVE_NUEVA>` por una contraseña tuya (es solo un marcador: nunca subas la real al repositorio):

```sql
CREATE DATABASE [DateManager];
CREATE DATABASE [DateManagerTest];
GO
CREATE LOGIN [datemanager_app] WITH PASSWORD = N'<CLAVE_NUEVA>', CHECK_POLICY = ON;
GO
USE [DateManager];
CREATE USER [datemanager_app] FOR LOGIN [datemanager_app];
ALTER ROLE db_owner ADD MEMBER [datemanager_app];
GO
USE [DateManagerTest];
CREATE USER [datemanager_app] FOR LOGIN [datemanager_app];
ALTER ROLE db_owner ADD MEMBER [datemanager_app];
GO
```

Después crea tú mismo el archivo `.env.local` (ver más abajo). Las tablas las crea la propia aplicación con
Flyway al arrancar (`src/main/resources/db/migration`).

## 2. Variables de entorno

La configuración sale de variables de entorno `DATEMANAGER_*`; **ningún secreto vive en el repositorio**.
`.env.local` es un archivo de texto en esta carpeta, una variable por línea (`NOMBRE=valor`), **ignorado por git**
(`.gitignore`); el script del paso anterior lo genera. Las variables no se cargan solas: hay que cargarlas en el
terminal antes de usar Maven (ver abajo).

| Variable | Obligatoria | Para qué |
| --- | --- | --- |
| `DATEMANAGER_DB_URL` | sí | URL JDBC de la base, p. ej. `jdbc:sqlserver://localhost:1433;encrypt=true;trustServerCertificate=true;databaseName=DateManager` |
| `DATEMANAGER_DB_USUARIO` | sí | Login SQL (`datemanager_app`) |
| `DATEMANAGER_DB_CLAVE` | sí | Contraseña de ese login |
| `DATEMANAGER_JWT_SECRETO` | sí | Clave para firmar los tokens (HS256), al menos 32 bytes; sin ella la aplicación no arranca |
| `DATEMANAGER_ADMIN_USUARIO`, `DATEMANAGER_ADMIN_CLAVE` | no | Administrador inicial: solo se crea si al arrancar no hay **ningún** usuario (rol Administrador con todos los permisos). Si faltan, arranca igual y avisa en el log |
| `DATEMANAGER_ZONA_HORARIA` | no | Zona del negocio; por defecto `America/Bogota`. «Hoy» y «ahora» se calculan en ella, no en la de la JVM |
| `DATEMANAGER_CORS_ORIGENES` | no | Orígenes permitidos separados por comas; por defecto `http://localhost:4200` |
| `DATEMANAGER_TEST_DB_URL`, `DATEMANAGER_TEST_DB_USUARIO`, `DATEMANAGER_TEST_DB_CLAVE` | solo para pruebas | Conexión a la base `DateManagerTest` |

### Cargar `.env.local` en el terminal

Las URLs llevan `;`, así que **no** sirve `source .env.local` en bash: hay que leerlo línea a línea.

**PowerShell** (Windows), desde esta carpeta:

```powershell
Get-Content .env.local | ForEach-Object {
  if ($_ -match '^\s*(DATEMANAGER_\w+)=(.*)$') { Set-Item -Path "Env:$($Matches[1])" -Value $Matches[2] }
}
```

**Bash** (Git Bash, Linux, macOS), desde esta carpeta:

```bash
while IFS='=' read -r k v || [ -n "$k" ]; do
  case "$k" in DATEMANAGER_*) export "$k=$v";; esac
done < <(tr -d '\r' < .env.local)
```

Las variables valen solo para ese terminal. No imprimas su valor ni las copies a archivos versionados.

## 3. Arrancar con el perfil `dev`

Con las variables cargadas:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

En **PowerShell** el argumento va entre comillas (si no, PowerShell parte el nombre en el punto):

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

La API queda en <http://localhost:8080>. Al primer arranque Flyway crea las tablas y, **solo en el perfil `dev` y
solo si la base está vacía**, se siembran los datos de ejemplo (ver «Usuarios de ejemplo»).

- **Swagger UI:** <http://localhost:8080/swagger-ui.html> (redirige a `/swagger-ui/index.html`); el contrato
  OpenAPI en JSON está en <http://localhost:8080/v3/api-docs>. Solo existen en el perfil `dev`: en los demás
  perfiles no hay ni Swagger ni siembra. Para llamar a las rutas del panel
  desde Swagger UI: haz login con `POST /api/auth/login` (por ejemplo `marta` / `demo123`), copia el valor de
  `token` de la respuesta, pulsa *Authorize*, pega el token (sin el prefijo `Bearer`) y confirma; desde ahí las
  peticiones del panel llevan la cabecera `Authorization`. También puedes usar `curl`, Postman o el cliente Angular.
- Prueba rápida:

```bash
curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
     -d '{"usuario":"marta","clave":"demo123"}'
```

Para detenerla, `Ctrl+C` en el terminal.

## 4. Correr las pruebas

> **Cuidado:** las pruebas usan la base `DateManagerTest` y **la borran por completo** al arrancar y antes de cada
> prueba (Flyway `clean` solo existe en las pruebas y se niega a ejecutarse si la base conectada no termina en
> `Test`). Nunca apuntes `DATEMANAGER_TEST_DB_URL` a una base con datos que quieras conservar.

Con las variables cargadas (necesitan `DATEMANAGER_TEST_DB_*`):

```bash
mvn test                                           # toda la suite
mvn test -Dtest=RecorridoCompletoTest                # una prueba (en PowerShell: "-Dtest=A,B" entre comillas)
```

`RecorridoCompletoTest` ejecuta el recorrido de punta a punta (login → enlace de 24 h con 1 uso → servicios →
horarios → reserva → la cita aparece en el panel → segunda reserva `410 ENLACE_AGOTADO` → cancelar → el horario
vuelve a salir libre). El reloj de las pruebas es falso y fijo; lo demás es HTTP real contra la base de pruebas.

## Usuarios de ejemplo (solo demostraciones locales)

El perfil `dev` siembra, equivalente a `core/seed.ts` de la demo:

| Usuario | Clave | Rol |
| --- | --- | --- |
| `marta` | `demo123` | Administrador (todos los permisos) |
| `andres` | `demo123` | Recepción (`ver_citas`, `gestionar_citas`, `atender_citas`, `gestionar_clientes`) |

Más tres servicios (Consulta inicial 30 min, Sesión estándar 60 min, Sesión extendida 90 min, con ventanas
distintas), ocho clientes y 23 citas repartidas alrededor de «ahora» (pasadas atendidas o canceladas, y hasta diez
días hacia adelante).

**`demo123` es una clave de demostración deliberadamente falsa. La siembra es solo para demos locales y no debe
ejecutarse nunca en producción.** Por eso solo existe con el perfil `dev`, no duplica nada si se reinicia y
se salta por completo si la base ya tiene cualquier dato propio (usuarios, roles, servicios, clientes, citas o
enlaces). En la base solo se guarda el hash BCrypt de la clave. En un entorno real no actives el perfil `dev`: crea
el primer administrador con `DATEMANAGER_ADMIN_USUARIO` / `DATEMANAGER_ADMIN_CLAVE` (con una clave tuya) y cámbiala.

## Resumen de rutas

Todas bajo `/api`. Fechas `YYYY-MM-DD`, horas `HH:mm` (hora del negocio, sin zona), instantes en ISO UTC, ids UUID.

**`/api/auth`** (sin token salvo `yo`)

- `POST /api/auth/login` `{usuario, clave}` → `{token, usuario, rol}`
- `GET /api/auth/yo` → sesión actual (el rol y sus permisos se leen de la base en cada petición)

**`/api/panel/*`** (cabecera `Authorization: Bearer <token>` y un permiso; basta uno de la lista)

| Ruta | Permiso |
| --- | --- |
| `GET /inicio[?fecha=]` (tablero) | `ver_panel` |
| `GET /servicios` | `ver_panel`, `ver_citas` o `gestionar_servicios` |
| `PUT /servicios/{id}` | `gestionar_servicios` |
| `GET /servicios/{id}/horarios?fecha=[&excluirCitaId=]` | `gestionar_citas` |
| `GET /clientes` | `ver_citas` o `gestionar_clientes` |
| `PUT /clientes/{id}` | `gestionar_clientes` |
| `GET /citas[?desde=&hasta=&estado=]` | `ver_panel` o `ver_citas` |
| `POST /citas`, `POST /citas/{id}/mover`, `POST /citas/{id}/cancelar` | `gestionar_citas` |
| `POST /citas/{id}/atender` | `atender_citas` |
| `GET /roles` | `gestionar_roles` o `gestionar_usuarios` |
| `PUT /roles/{id}`, `DELETE /roles/{id}`, `GET /permisos` | `gestionar_roles` |
| `/usuarios` (listar, `PUT /{id}`, `DELETE /{id}`) | `gestionar_usuarios` |
| `GET /enlaces`, `POST /enlaces`, `POST /enlaces/{id}/desactivar` | `gestionar_enlaces` |

**`/api/publico/invitaciones/{codigo}/*`** (zona del cliente: sin token, solo el código del enlace)

- `GET /{codigo}` → estado del enlace (`Activo`, `Vencido`, `Agotado`, `Desactivado`) y cuándo vence
- `GET /{codigo}/servicios` → servicios activos
- `GET /{codigo}/servicios/{id}/horarios?fecha=` → horas libres
- `POST /{codigo}/citas` → reserva (gasta un uso del enlace; una reserva fallida no lo gasta)

### Contrato de errores

Todo error es JSON (problem details) con las propiedades `codigo` y `mensaje` (en español) y, si aplica, `campos`
(`{campo: mensaje}`), además de los estándar `status`, `title`, `detail` e `instance`:

```json
{"status":401,"codigo":"CREDENCIALES_INVALIDAS","mensaje":"Usuario o contraseña incorrectos."}
```

| Código | HTTP |
| --- | --- |
| `VALIDACION` | 400 |
| `NO_AUTENTICADO`, `CREDENCIALES_INVALIDAS` | 401 |
| `SIN_PERMISO` | 403 |
| `NO_ENCONTRADO`, `ENLACE_NO_ENCONTRADO` | 404 |
| `HORARIO_NO_DISPONIBLE`, `ESTADO_INVALIDO`, `CITA_NO_HA_INICIADO`, `CONFLICTO` | 409 |
| `ENLACE_VENCIDO`, `ENLACE_AGOTADO`, `ENLACE_DESACTIVADO` | 410 |
| `ERROR_INTERNO` | 500 (mensaje genérico, sin detalles) |

## Límites de operación

- **No hay límite de tasa** (rate limiting): ni en el login ni en la zona pública. Si la API se expone a Internet,
  ponla detrás de un proxy o una puerta de enlace que lo aplique.
- **Los tokens duran 60 minutos** y no hay renovación: al vencer hay que volver a iniciar sesión. Los permisos
  se leen de la base en cada petición, así que cambiar un rol o borrar un usuario surte efecto de inmediato.
- **Los datos de ejemplo son solo para el perfil `dev`** y para demostraciones locales (ver arriba).

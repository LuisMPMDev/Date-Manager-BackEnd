# DateManager API

API REST (Java 21, Spring Boot 3.5, SQL Server) que reemplaza el almacén local de la demo Angular: login real,
permisos verificados en el servidor, reservas sin doble agendamiento y enlaces de invitación para que los clientes
agenden sin cuenta. El cliente Angular de `src/Date.Manager.Front` ya la consume (ver su README para arrancarlo
contra esta API).

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

## Reglas en la base de datos

Desde las migraciones `V7`–`V9` la base rechaza por sí misma lo que la aplicación ya valida antes de guardar. Es
la última defensa ante escrituras directas (SQL a mano, otro cliente) y ante carreras; el flujo normal de la API
no debería llegar a ellas. Todas las restricciones se crean validadas (`WITH CHECK`), así que valen también para
los datos ya guardados.

| Regla | Restricción / disparador | Regla de la aplicación que respalda | Cómo se prueba |
| --- | --- | --- | --- |
| `cancelada_en` tiene valor si y solo si la cita está `Cancelada` | `ck_cita_cancelada_coherente` (`V7`) | Cancelar una cita fija la fecha de cancelación; ningún otro estado la lleva | `EsquemaRestriccionesTest` |
| `cancelada_en >= creada_en` | `ck_cita_cancelada_posterior` (`V7`) | La cancelación es posterior a la creación | `EsquemaRestriccionesTest` |
| `nombre`, `apellido` y `telefono` de cliente, `nombre` de usuario, de servicio y de rol: no vacíos ni solo espacios | `ck_cliente_nombre_no_vacio`, `ck_cliente_apellido_no_vacio`, `ck_cliente_telefono_no_vacio`, `ck_usuario_nombre_no_vacio`, `ck_servicio_nombre_no_vacio`, `ck_rol_nombre_no_vacio` (`V7`) | `@NotBlank` y recorte de espacios de la capa de aplicación | `EsquemaRestriccionesTest` |
| Correo de cliente y de usuario: una sola `@`, un punto en el dominio, sin espacios y sin letras A-Z ASCII (en usuario se admite vacío: el administrador inicial se crea sin correo) | `ck_cliente_correo_forma`, `ck_usuario_correo_forma` (`V7`) | La aplicación guarda el correo recortado y en minúsculas, con la forma `^[^\s@]+@[^\s@.]+(\.[^\s@.]+)+$` (la de SQL es algo más laxa, nunca más estricta) | `EsquemaRestriccionesTest` |
| Nombre de usuario (`usuario.usuario`): no vacío, sin espacios en los extremos y sin A-Z ASCII | `ck_usuario_usuario_forma` (`V7`) | La aplicación recorta y pasa a minúsculas | `EsquemaRestriccionesTest` |
| `clave_hash` tiene forma de hash BCrypt (`$2a$`/`$2b$`/`$2y$`, dos dígitos de costo y 53 caracteres, 60 en total) | `ck_usuario_clave_hash_bcrypt` (`V7`) | Nunca se guarda la contraseña, solo el hash del codificador | `EsquemaRestriccionesTest` |
| `servicio.duracion_min <= 1440` | `ck_servicio_duracion_max` (`V7`) | `Validaciones.MAX_DURACION_MIN` | `EsquemaRestriccionesTest` |
| `desactivado_en >= creado_en` en los enlaces | `ck_enlace_invitacion_desactivado` (`V7`) | Un enlace se desactiva después de crearse | `EsquemaRestriccionesTest` |
| Dos citas no canceladas del mismo servicio y día no se solapan (que una termine donde empieza la otra sí vale) | Disparador `tr_cita_sin_solape` e índice filtrado `ix_cita_servicio_ocupada` (`V8`); `uq_cita_servicio_inicio` se conserva | La reserva ya lo impide bajo el bloqueo de la fila del servicio | `EsquemaDisparadoresTest` (incluye la carrera de dos conexiones y los traslados y reactivaciones), `ReservaConcurrenteTest`, `UsoConcurrenteEnlaceTest` |
| Dos ventanas de disponibilidad del mismo servicio y día no se cruzan (pegadas sí) | Disparador `tr_ventana_sin_cruce` (`V9`) | `Validaciones.mensajeDeVentanasCruzadas`; `ServiciosServicio.guardar` borra todas las ventanas, vacía y las vuelve a insertar para que ningún paso intermedio las cruce | `EsquemaDisparadoresTest` (SQL directo) y `ServiciosControladorTest` (guardados válidos que antes se solapaban un instante y el cruce real, rechazado con el mensaje en español) |
| La migración no corrige datos: aborta si los hay inválidos | `THROW 50001` en `V7`, `V8` y `V9` | Nunca se arreglan datos de usuario en silencio | `MigracionDatosTest` |

### Errores de la base y cómo llegan a la API

| Número | Origen | Resultado en la API |
| --- | --- | --- |
| `50001` | Aborto de la migración (`V7`, `V8`, `V9`) por datos que violan una regla | La API no arranca; el mensaje dice la regla, la tabla y hasta 5 ids |
| `50010` | `tr_cita_sin_solape`: cita solapada | Al reservar o mover una cita, `409 HORARIO_NO_DISPONIBLE`; en otras rutas, `409 CONFLICTO` |
| `50020` | `tr_ventana_sin_cruce`: ventanas cruzadas | `409 CONFLICTO` |

Un `CHECK` o un índice único violado llega como `409 CONFLICTO`. Los mensajes hacia la API nunca llevan valores de
la base. Se prueban en `ManejadorErroresTest` y `ErroresBaseTest` (cualquier `THROW` 50000–50099 se trata como
rechazo de un disparador).

### Si la migración aborta (`THROW 50001`)

Al arrancar, Flyway falla con un mensaje que nombra la regla, la tabla y hasta 5 ids (o pares de ids) de ejemplo.
La migración **nunca corrige ni borra datos por su cuenta**: `V7`, `V8` y `V9` auditan primero (solo lectura), se
ejecutan cada una en una sola transacción y, si algo falla, la base queda en la versión anterior, sin
restricciones ni disparadores a medias. Qué hacer: corregir a mano las filas que indica el mensaje (completar un
texto vacío, cancelar o mover una de las citas solapadas, ajustar una ventana cruzada…) y volver a arrancar; el
aborto se repite, con la siguiente regla violada, hasta que no quede ninguna.

### Notas operativas

- **Índice filtrado y bloqueos de rango.** `tr_cita_sin_solape` lee solo de `ix_cita_servicio_ocupada` (citas no
  canceladas por servicio, día e inicio, con `hora_fin` incluida) con `UPDLOCK` y `HOLDLOCK`. El bloqueo de rango
  puede alcanzar la clave frontera, que a veces es de otro día u otro servicio; una escritura sobre esa clave
  podría esperar un momento (nunca da un resultado incorrecto). Dentro de un mismo servicio, la aplicación toma antes el bloqueo de su fila y
  serializa las reservas. Ante dos escrituras directas que se solapan a la vez, una confirma y la otra recibe
  `50010` o es la víctima de un interbloqueo (error 1205, que la API devuelve como `409 CONFLICTO`); basta reintentarla.
- **`tr_ventana_sin_cruce` y el aislamiento.** Su lectura no lleva sugerencias de bloqueo: ante escrituras
  directas simultáneas se apoya en que la base **no** tenga `READ_COMMITTED_SNAPSHOT` activo (con él, una
  transacción no ve ni espera las filas aún sin confirmar de la otra y podrían ganar las dos). Azure SQL lo
  activa por defecto. Se comprueba con `SELECT is_read_committed_snapshot_on FROM sys.databases WHERE name =
  DB_NAME()`. Si está activo, añade `WITH (UPDLOCK, HOLDLOCK)` a la lectura de `ventana_disponibilidad` del
  disparador en una migración nueva (no se edita `V9`: Flyway valida su checksum). La API no se ve afectada porque
  `ServiciosServicio.guardar` toma el bloqueo de la fila del servicio y reemplaza sus ventanas en una sola transacción, así que dos guardados del mismo servicio se serializan.
- **Reloj del servidor atrasado.** Si el reloj retrocede (p. ej. un ajuste NTP hacia atrás) y queda antes del
  instante en que se creó una cita o un enlace, cancelar la cita o desactivar el enlace da `409 CONFLICTO` por
  `ck_cita_cancelada_posterior` / `ck_enlace_invitacion_desactivado`. Se resuelve solo cuando el reloj alcanza
  ese instante; no hay que tocar datos.
- **Lo que las reglas no cubren.** `LTRIM`/`RTRIM` solo quitan espacios: un nombre formado solo por tabuladores
  o saltos de línea pasa la base (la aplicación sí lo rechaza con su `strip()`). No hay regla de «no vacío» para
  `usuario.telefono`, `rol.descripcion` ni `usuario.apellido` (la aplicación los permite vacíos). El correo y el
  nombre de usuario solo prohíben A-Z ASCII (las tildes y eñes son válidas) y el correo no se valida tan
  estrictamente como en Java. El hash solo comprueba la forma BCrypt, no que corresponda a una contraseña real.
  Tampoco hay regla para los espacios internos de un nombre de usuario.

## Límites de operación

- **No hay límite de tasa** (rate limiting): ni en el login ni en la zona pública. Si la API se expone a Internet,
  ponla detrás de un proxy o una puerta de enlace que lo aplique.
- **Los tokens duran 60 minutos** y no hay renovación: al vencer hay que volver a iniciar sesión. Los permisos
  se leen de la base en cada petición, así que cambiar un rol o borrar un usuario surte efecto de inmediato.
- **Los datos de ejemplo son solo para el perfil `dev`** y para demostraciones locales (ver arriba).

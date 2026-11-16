# Guía de DateManager

Explicación sencilla de cómo funciona el proyecto, cómo ejecutarlo, qué pasa en cada flujo (del navegador a la base de datos) y los comandos de Git para cambiar de rama en CMD.

---

## 1. ¿Qué es DateManager?

Una plataforma para **reservar citas** en negocios que atienden por servicios con duración y horario (barberías, peluquerías, veterinarias, consultorios).

Hay dos tipos de personas que la usan:

| Quién | Qué hace | Por dónde entra |
|---|---|---|
| **Cliente** | Reserva una cita **sin crear cuenta**, usando un enlace de invitación que le manda el negocio | `http://localhost:4200/#/agendar/<codigo>` |
| **Negocio** (administrador, recepción…) | Gestiona servicios, horarios, citas, clientes, usuarios, roles y enlaces | `http://localhost:4200/#/login` y luego el panel |

## 2. Las tres piezas

```
 Navegador ──► Angular (puerto 4200) ──► API en Java (puerto 8080) ──► SQL Server (puerto 1433)
   el usuario     pantallas                reglas de negocio              guarda todo
                                           login, permisos, JWT
```

| Pieza | Carpeta | Tecnología | Su trabajo |
|---|---|---|---|
| **Front** | `src/Date.Manager.Front` | Angular 21 | Dibuja las pantallas y llama a la API. Solo *adapta* el menú a los permisos; **quien decide es la API** |
| **Back** (API) | `src/Date.Manager.Back` | Java 21 **puro** (sin Spring ni otros frameworks), solo el driver `mssql-jdbc` | Valida, aplica las reglas, controla quién puede qué y habla con la base |
| **Base de datos** | SQL Server 2022 | Scripts `V1…V10` en `src/main/resources/db/migration` | Guarda los datos y es la **última defensa**: rechaza datos inválidos aunque algo falle arriba |

> La API en Java puro vive en la rama `java-puro`. En `main` sigue la versión original con Spring Boot (ver sección 7).

### Qué trae la API por dentro (todo escrito a mano)

| Pieza | Qué hace |
|---|---|
| Servidor HTTP | Recibe las peticiones (`com.sun.net.httpserver` del JDK) |
| Enrutador | Decide qué código atiende cada ruta y qué permisos pide |
| JSON | Lee y escribe JSON |
| JWT + BCrypt | Firma los tokens de sesión y protege las contraseñas |
| Pool y transacciones | Reutiliza conexiones a la base y garantiza «todo o nada» |
| Migrador | Crea/actualiza las tablas al arrancar leyendo `V1…V10` |
| Swagger (solo perfil `dev`) | Página para probar la API: `/swagger-ui.html` |

### Cómo viaja una petición dentro de la API

```
Petición HTTP
  → ServidorHttp (límites de tamaño y tiempo, CORS)
  → Enrutador (¿existe la ruta y el método?)
  → Autenticación (¿token válido?  — las rutas públicas no lo leen)
  → Permisos (¿su rol tiene alguno de los permisos que pide la ruta?)
  → Controlador (valida el cuerpo de la petición)
  → Servicio (reglas de negocio)
  → Repositorio (SQL a mano, dentro de una transacción)
  → SQL Server
  ← la respuesta vuelve en JSON (o un error con `codigo` y `mensaje`)
```

Carpetas del código de la API (`src/main/java/com/datemanager`): `api` (controladores y formatos JSON), `aplicacion` (servicios de negocio), `dominio` (reglas puras: horarios, estados, validaciones), `infraestructura` (HTTP, base de datos, seguridad, arranque).

## 3. Conceptos clave

- **Servicio**: lo que se reserva (nombre, duración en minutos, valor). Tiene **ventanas de disponibilidad**: franjas por día de la semana (p. ej. lunes 09:00–13:00).
- **Horario libre**: se calcula a partir de las ventanas del servicio menos las citas ya ocupadas.
- **Cita**: servicio + fecha + hora + cliente. Estados: *Confirmada*, *Cancelada*, *Atendida*.
- **Enlace de invitación**: código que el negocio crea con vencimiento y número máximo de usos. Estados: *Activo*, *Vencido*, *Agotado*, *Desactivado*.
- **Rol y permisos**: cada usuario tiene un rol; el rol tiene permisos (`ver_panel`, `ver_citas`, `gestionar_citas`, `atender_citas`, `gestionar_clientes`, `gestionar_servicios`, `gestionar_usuarios`, `gestionar_roles`, `gestionar_enlaces`).
- **Token (JWT)**: lo entrega el login y dura **60 minutos**. El front lo manda en la cabecera `Authorization: Bearer <token>`. Los permisos se leen de la base en **cada** petición, así que un cambio de rol surte efecto de inmediato.
- **Sin doble reserva**: la API bloquea el servicio mientras reserva y, además, la base tiene un disparador (`tr_cita_sin_solape`) y un índice que impiden que dos citas se solapen aunque dos personas reserven a la vez.

---

## 4. Tutorial paso a paso para ejecutarlo (Windows, CMD)

### 4.1 Requisitos

- **JDK 21** (el `java` del PATH puede ser otro: comprueba con `java -version`).
- **Maven 3.6.3 o superior** (solo para compilar y probar).
- **Node 20.19 o superior** con npm.
- **SQL Server 2022** con TCP/IP habilitado (puerto 1433) y autenticación mixta.
- **Git**.

### 4.2 Obtener el código y elegir la rama

```cmd
git clone https://github.com/LuisMPMDev/Date-Manager-BackEnd.git
cd Date-Manager-BackEnd
git switch java-puro
```

### 4.3 Preparar la base de datos

La forma más fácil es el script de PowerShell, **como administrador**:

```cmd
cd src\Date.Manager.Back
powershell -ExecutionPolicy Bypass -File .\scripts\preparar-sqlserver.ps1
```

Qué hace: habilita TCP/IP en el puerto 1433, pasa a autenticación mixta, **reinicia el servicio de SQL Server**, crea las bases `DateManager` y `DateManagerTest`, crea el login `datemanager_app` y escribe el archivo `.env.local` (ignorado por git) con las variables de conexión. La contraseña del login la escribes tú cuando el script la pide; no la compartas por chat ni la subas al repositorio.

> Si tu instancia no se llama `.\MSSQLSERVER01`, cambia la variable `$instancia` al inicio del script. Alternativa manual (SSMS): ver `src/Date.Manager.Back/README.md`, sección 1, opción B.

### 4.4 Cargar las variables de `.env.local` en la ventana de CMD

Las variables solo valen para esa ventana. Desde `src\Date.Manager.Back`:

```cmd
for /f "usebackq eol=# tokens=1,* delims==" %a in (".env.local") do set "%a=%b"
```

(Si lo pones dentro de un archivo `.bat`, escribe `%%a` y `%%b`.) No imprimas esos valores ni los copies a archivos versionados.

Variables principales: `DATEMANAGER_DB_URL`, `DATEMANAGER_DB_USUARIO`, `DATEMANAGER_DB_CLAVE`, `DATEMANAGER_JWT_SECRETO` (mínimo 32 bytes). Opcionales: `DATEMANAGER_PERFIL`, `DATEMANAGER_PUERTO` (8080), `DATEMANAGER_DB_POOL` (1–200, por defecto 10), `DATEMANAGER_ZONA_HORARIA` (`America/Bogota`), `DATEMANAGER_CORS_ORIGENES` (`http://localhost:4200`), `DATEMANAGER_ADMIN_USUARIO` / `DATEMANAGER_ADMIN_CLAVE`.

### 4.5 Compilar y arrancar la API con el perfil `dev`

Si `mvn` no está en el PATH, usa la ruta completa o agrégalo (`set PATH=C:\ruta\apache-maven\bin;%PATH%`). Fija el JDK 21:

```cmd
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd src\Date.Manager.Back
mvn -q package -DskipTests
set DATEMANAGER_PERFIL=dev
java -jar target\Date.Manager.Back-1.0-SNAPSHOT.jar
```

- Con `-DskipTests` no corre las pruebas (más rápido). Sin esa opción, `mvn package` también las ejecuta y necesita las variables `DATEMANAGER_TEST_DB_*`.
- Al primer arranque se crean las tablas y, **solo en el perfil `dev` y con la base vacía**, se cargan datos de ejemplo.
- La API queda en <http://localhost:8080>. Se detiene con `Ctrl+C`.
- Si falla (variable faltante, base inaccesible…) escribe un mensaje claro, sin secretos, y termina con código 1.

Prueba rápida del login (CMD):

```cmd
curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"usuario\":\"marta\",\"clave\":\"demo123\"}"
```

Swagger (solo `dev`): <http://localhost:8080/swagger-ui.html>. Hace login con `POST /api/auth/login`, copia el `token`, pulsa **Authorize** y pégalo sin el prefijo `Bearer`. Necesita internet porque carga la interfaz desde un CDN.

### 4.6 Arrancar el Angular

En **otra** ventana de CMD:

```cmd
cd src\Date.Manager.Front
npm install
npm start
```

Abre <http://localhost:4200>. La raíz te lleva a `#/login`.

> Abre el front por `localhost` (o HTTPS). Por una IP de red, el navegador no ofrece `crypto.randomUUID()` y fallará crear servicios, roles, etc.
> La dirección de la API (`http://localhost:8080/api`) está en `src/app/core/config.ts`; para cambiarla, ver el README del front.

### 4.7 Cuentas de ejemplo (solo perfil `dev`)

| Usuario | Clave | Rol | Puede |
|---|---|---|---|
| `marta` | `demo123` | Administrador | Todo |
| `andres` | `demo123` | Recepción | Citas y clientes |

Más tres servicios, ocho clientes y 23 citas de ejemplo. **No uses el perfil `dev` en producción**; allí crea el primer administrador con `DATEMANAGER_ADMIN_USUARIO` y `DATEMANAGER_ADMIN_CLAVE`.

### 4.8 Correr las pruebas

> **Cuidado:** las pruebas **borran por completo** la base `DateManagerTest`. Nunca apuntes `DATEMANAGER_TEST_DB_URL` a una base con datos que quieras conservar.

Con las variables cargadas (incluidas `DATEMANAGER_TEST_DB_*`):

```cmd
cd src\Date.Manager.Back
mvn test
```

Front: `npm test` (Vitest); una sola corrida: `npx ng test --watch=false`.

---

## 5. Los flujos, del front al back

Convenciones: **Front** = Angular, **API** = Java, **BD** = SQL Server. Las rutas de la API empiezan por `/api`. Los errores siempre llegan como JSON con `codigo` y `mensaje` en español (por ejemplo `HORARIO_NO_DISPONIBLE`, 409), y el front los convierte en un aviso en pantalla.

### 5.1 Iniciar sesión

1. Front (`#/login`): el usuario escribe usuario y clave.
2. `POST /api/auth/login` con `{usuario, clave}` (ruta pública, sin token).
3. API: busca el usuario y compara la clave con el hash **BCrypt** guardado (si el usuario no existe, igual gasta el tiempo de una comparación para no revelar cuáles usuarios existen).
4. API responde `{token, usuario, rol}`; el token (JWT firmado, 60 min) lleva el usuario y su vencimiento.
5. Front: guarda el token en `sessionStorage` (clave `datemanager.sesion.v2`), guarda usuario y rol, y te lleva a la primera pantalla que tu rol puede ver.
6. Errores: credenciales malas → 401 `CREDENCIALES_INVALIDAS`.

### 5.2 Mantener la sesión al recargar y expirarla

- **Recargar la página:** el front lee el token guardado y llama `GET /api/auth/yo`. La API lee **de la base** el rol y los permisos vigentes. Si cambiaron, el menú se actualiza solo.
- **Cada petición del panel:** un *interceptor* de Angular añade `Authorization: Bearer <token>` solo a `/panel/**` y a `/auth/yo`; **nunca** al login ni a la zona pública.
- **Token vencido o usuario borrado:** la API responde 401; el front borra el token, te manda a `#/login` y muestra «Tu sesión venció».
- **Protección de rutas del front:** *guards* piden sesión y permiso para cada pantalla; si no tienes permiso, te llevan a la primera pantalla permitida o a «sin acceso». Es solo comodidad: la API vuelve a comprobar todo.

### 5.3 Panel de inicio (tablero)

1. Front (`#/panel`): `GET /api/panel/inicio` (permiso `ver_panel`).
2. API: calcula en SQL los indicadores (citas de hoy, creadas y canceladas, próximas citas, alertas como «servicio sin horario») con la **hora del negocio** (`America/Bogota`, no la del servidor).
3. Front: dibuja tarjetas y gráficas, y se recarga cada minuto para mantener al día el «en N min» de la próxima cita.

### 5.4 Agenda y listado de citas

- **Agenda** (`#/agenda`): columnas por servicio. Pide `GET /api/panel/servicios` y `GET /api/panel/citas`.
- **Citas** (`#/citas`): `GET /api/panel/citas?desde=&hasta=&estado=` con permiso `ver_panel` o `ver_citas`. La API valida fechas (`YYYY-MM-DD`) y estado; si son inválidos devuelve 400 `VALIDACION` con el campo que falló.

### 5.5 Mover una cita

1. Front: «Mover cita» pide `GET /api/panel/servicios/{id}/horarios?fecha=&excluirCitaId=` (permiso `gestionar_citas`): solo ofrece horas libres del mismo servicio, sin contar la propia cita.
2. El usuario elige hora y el front envía `POST /api/panel/citas/{id}/mover` con `{fecha, inicio}`.
3. API (en **una transacción**): bloquea el servicio y luego la cita, vuelve a comprobar que el horario esté libre y reglas de estado, y actualiza.
4. Si alguien tomó esa hora un instante antes → 409 `HORARIO_NO_DISPONIBLE` (también lo detecta la base con el disparador). El front avisa y recarga horas.

### 5.6 Cancelar una cita

`POST /api/panel/citas/{id}/cancelar` (permiso `gestionar_citas`). La API cambia el estado a *Cancelada* y guarda cuándo; el horario vuelve a salir libre en la página pública. Si la cita ya estaba cancelada o atendida → 409 `ESTADO_INVALIDO`.

### 5.7 Marcar una cita como atendida

`POST /api/panel/citas/{id}/atender` (permiso `atender_citas`). Solo se puede si la cita **ya empezó**; si no, 409 `CITA_NO_HA_INICIADO` («La cita aún no ha iniciado.»). El botón solo aparece para citas ya iniciadas.

### 5.8 Crear una cita desde el panel

La API tiene `POST /api/panel/citas` (permiso `gestionar_citas`) con `{servicioId, fecha, inicio, cliente:{nombre, apellido, telefono, correo}}`; el panel actual crea las citas por la zona pública, no tiene formulario propio para esto.

### 5.9 Servicios y horarios de disponibilidad

1. Front (`#/servicios`): lista con `GET /api/panel/servicios`.
2. Crear o editar: `PUT /api/panel/servicios/{id}` (permiso `gestionar_servicios`) con `{nombre, duracionMin, activo, valor, ventanas}`. El id lo genera el front (`crypto.randomUUID()`).
3. API: valida (duración 1–1440 min, ventanas sin cruzarse) y, en **una transacción**, bloquea el servicio, borra sus ventanas y las vuelve a insertar. Si dos ventanas se cruzan → 400 con el mensaje en español (y la base también lo impide con `tr_ventana_sin_cruce`).
4. Un servicio **desactivado** deja de aparecer en la página pública; un servicio activo sin ventanas genera la alerta «Servicio sin horario» en el panel.

### 5.10 Clientes

`GET /api/panel/clientes` (permiso `ver_citas` o `gestionar_clientes`) y `PUT /api/panel/clientes/{id}` (`gestionar_clientes`) para editar. La API recorta espacios y guarda el correo en minúsculas; el correo es único.

### 5.11 Usuarios y roles

1. Front (`#/usuarios`): `GET /api/panel/roles`, `GET /api/panel/permisos` y `GET /api/panel/usuarios`.
2. Roles: `PUT /api/panel/roles/{id}` y `DELETE /api/panel/roles/{id}` (permiso `gestionar_roles`).
3. Usuarios: `PUT /api/panel/usuarios/{id}` y `DELETE` (permiso `gestionar_usuarios`). La contraseña se guarda **solo como hash BCrypt**.
4. **Protecciones** (la API las aplica y la base las respalda): no se puede quitar «Gestionar roles y permisos» al rol Administrador, no se puede eliminar un rol que tenga usuarios, y no se puede dejar el sistema sin quien pueda administrar. Si algo se viola → 409 con el motivo.

### 5.12 Enlaces de invitación (negocio)

1. Front (`#/enlaces`, permiso `gestionar_enlaces`): `GET /api/panel/enlaces` para listar.
2. Crear: `POST /api/panel/enlaces` con vigencia en minutos (por defecto 24 h) y máximo de usos (opcional). La API genera un **código de 22 caracteres** y devuelve el enlace.
3. Desactivar: `POST /api/panel/enlaces/{id}/desactivar`.
4. El negocio copia la ruta `#/agendar/<codigo>` y se la manda al cliente.

### 5.13 Reserva pública del cliente (sin cuenta)

1. El cliente abre `#/agendar/<codigo>` (sin sesión; si tenía un token vencido, el front lo descarta y no lo molesta).
2. `GET /api/publico/invitaciones/{codigo}` → estado del enlace. Si está vencido, agotado, desactivado o no existe, la pantalla muestra su mensaje (410 `ENLACE_VENCIDO` / `ENLACE_AGOTADO` / `ENLACE_DESACTIVADO` o 404 `ENLACE_NO_ENCONTRADO`).
3. `GET /api/publico/invitaciones/{codigo}/servicios` → servicios activos.
4. El cliente elige servicio y día: `GET .../servicios/{id}/horarios?fecha=` → horas libres (la «regla de tiempo»).
5. El cliente deja nombre, apellido, teléfono y correo y pulsa «Solicitar cita»: `POST /api/publico/invitaciones/{codigo}/citas`.
6. API (**una transacción**): comprueba el enlace; bloquea servicio → cliente → cita **siempre en ese orden** (evita bloqueos cruzados); reutiliza el cliente si el correo ya existe; verifica que el horario siga libre; crea la cita *Confirmada*; **gasta un uso del enlace** (una reserva fallida no lo gasta).
7. Respuesta 201 y el front muestra «Cita confirmada». La cita aparece de inmediato en Citas, Agenda y Panel.
8. Casos límite: dos clientes reservando la misma hora → uno recibe 201 y el otro 409 `HORARIO_NO_DISPONIBLE`; dos reservas con un enlace de 1 uso → una 201 y otra 410 `ENLACE_AGOTADO`; el front avisa si el horario se ocupó mientras reservabas.

### 5.14 Cómo se ve un error de punta a punta

Ejemplo: mover una cita a una hora ya ocupada.

1. Front envía `POST /api/panel/citas/{id}/mover`.
2. La API (o el disparador de la base) rechaza el cambio y lanza un error de negocio.
3. El manejador de errores responde `409` con `{"codigo":"HORARIO_NO_DISPONIBLE","mensaje":"…"}`.
4. El interceptor del front lo convierte en un `ApiError` y la pantalla muestra el mensaje.
5. Los errores inesperados salen como 500 `ERROR_INTERNO` con mensaje genérico; el detalle solo queda en el log del servidor.

### 5.15 Resumen de rutas de la API

| Grupo | Rutas | Token |
|---|---|---|
| `/api/auth` | `POST /login`, `GET /yo` | `login` no, `yo` sí |
| `/api/panel` | `inicio`, `servicios`, `citas`, `clientes`, `roles`, `permisos`, `usuarios`, `enlaces` | Sí, más el permiso de cada ruta |
| `/api/publico/invitaciones/{codigo}` | estado, `servicios`, `servicios/{id}/horarios`, `citas` | No (basta el código del enlace) |

Detalle completo y tabla de permisos por ruta: `src/Date.Manager.Back/README.md`, sección «Resumen de rutas».

---

## 6. Qué probar para entender el sistema

| Para ver… | Haz esto |
|---|---|
| Enlace de invitación | Como `marta`, en Enlaces crea uno (1440 min, 1 uso) y ábrelo en otra pestaña sin sesión |
| Reservar | En `#/agendar/<codigo>` elige servicio, día y hora, completa tus datos y confirma; revisa Citas, Agenda y Panel |
| Enlace agotado | Con el enlace de 1 uso ya usado, intenta otra reserva |
| Cancelar y mover | En Citas, cancela (el horario vuelve a salir en la página pública) y mueve otra |
| Roles y permisos | Crea un rol solo con «Ver citas», un usuario con ese rol y entra con él |
| Reglas protegidas | Intenta quitar «Gestionar roles y permisos» al Administrador |

---

## 7. Comandos de Git para cambiar de rama (CMD)

Ramas y etiquetas del repositorio:

| Rama | Qué contiene |
|---|---|
| `main` | Versión estable con la API en **Spring Boot** |
| `java-puro` | API en **Java puro** (sin Spring) — la rama de trabajo actual |
| `demo-sin-servidor` | Demo original Angular sin servidor (datos en el navegador) |

Etiquetas: `v1.0-demo-angular`, `v2.0-api-java`, `v2.1-conexion-angular`, `v3.0-validaciones-bd`.

```cmd
:: Ver en qué rama estás y si hay cambios sin guardar
git status
git branch

:: Traer del remoto las ramas y cambios nuevos (sin modificar tu trabajo)
git fetch --all --prune

:: Ver todas las ramas, locales y remotas
git branch -a

:: Cambiar de rama (forma moderna)
git switch java-puro
git switch main
git switch demo-sin-servidor

:: Cambiar de rama (forma clásica, equivalente)
git checkout java-puro

:: Si la rama solo existe en el remoto, crearla local y seguirla
git switch -c java-puro --track origin/java-puro

:: Crear una rama nueva a partir de la actual y cambiarte a ella
git switch -c mi-rama-nueva

:: Actualizar la rama en la que estás con lo del remoto
git pull

:: Ir a una etiqueta (queda en modo «solo lectura»; para trabajar, crea una rama)
git switch --detach v2.0-api-java
git switch -c mi-rama-desde-tag v2.0-api-java

:: Volver a la rama anterior
git switch -

:: Si git no te deja cambiar por tener cambios sin guardar:
::  a) guárdalos con un commit
git add .
git commit -m "mensaje descriptivo"
::  b) o ponlos a un lado temporalmente y recupéralos después
git stash push -u -m "mi-trabajo-temporal"
git switch main
git switch java-puro
git stash pop

:: Subir tu rama al remoto
git push -u origin java-puro
```

Notas:

- `git switch` y `git checkout` cambian la rama de **tu carpeta actual**. Si usas `git worktree` (varias carpetas del mismo repositorio) y una rama ya está abierta en otra carpeta, Git no te dejará abrirla en dos sitios a la vez: usa esa otra carpeta o crea una rama nueva.
- Al cambiar de rama, los archivos de la carpeta cambian (por ejemplo, el código de la API pasa de Java puro a Spring Boot). Después de cambiar, **recompila** la API (`mvn -q package -DskipTests`) y, en el front, `npm install` si cambió `package.json`.
- Los archivos ignorados por git (como `.env.local`) no cambian al cambiar de rama.

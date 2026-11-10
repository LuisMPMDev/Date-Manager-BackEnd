# DateManager Front (Angular)

Cliente web de la plataforma de reserva de citas, conectado a la API REST de `src/Date.Manager.Back`: una página pública donde el cliente reserva sin cuenta (con un enlace de invitación) y un panel de administración para gestionar servicios, citas, clientes, usuarios, roles y enlaces. Los permisos los decide la API; el menú y las rutas del front solo se adaptan a ellos.

> La demo original, sin servidor (datos en `localStorage`), vive en la rama local `demo-sin-servidor`. Esta versión ya no la incluye.

## Requisitos

- Node 20.19 o superior (con npm).
- La API en marcha en `http://localhost:8080` con el perfil `dev` (ver `src/Date.Manager.Back/README.md`, sección «Arrancar con el perfil dev»). Su CORS ya permite `http://localhost:4200`.

## Instalación y comandos

```
npm install
npm start          # servidor de desarrollo en http://localhost:4200
npm test           # pruebas unitarias (Vitest); para una sola corrida: npx ng test --watch=false
npm run build      # compilación a dist/
```

El `.npmrc` del proyecto activa `legacy-peer-deps=true`, así que `npm install` no necesita opciones extra.

Las rutas usan hash (`#/...`) y `<base href="./">`, por lo que el build de `dist/` se puede abrir desde cualquier servidor estático.

## Cómo arrancar contra la API

1. Arranca la API con el perfil `dev` (siembra los datos de ejemplo si la base está vacía).
2. En `src/Date.Manager.Front`: `npm start` y abre `http://localhost:4200`.

La raíz `/` lleva a `#/login`.

### Dirección de la API (`API_URL`)

La dirección base de la API es el token de inyección `API_URL` (`src/app/core/config.ts`) y vale `http://localhost:8080/api` por defecto; es el único lugar donde se escribe. Para apuntar a otra API, reemplázalo en `src/app/app.config.ts`:

```ts
import { API_URL } from './core/config';
// dentro de `providers`:
{ provide: API_URL, useValue: 'https://mi-servidor.example/api' },
```

Esa API debe permitir el origen del front en `DATEMANAGER_CORS_ORIGENES`.

### Contexto seguro (`crypto.randomUUID`)

Los ids de lo que se crea en pantalla (servicios, ventanas, roles, usuarios, clientes) son UUID generados con `crypto.randomUUID()`, que el navegador solo ofrece en un **contexto seguro**: `http://localhost` o `https://`. Si abres el front por `http://<ip-de-la-red>` esas altas fallarán; usa `localhost` o sirve el front con HTTPS.

## Cuentas de ejemplo (semilla de la API en perfil `dev`)

| Usuario  | Clave     | Rol           | Puede                                    |
| -------- | --------- | ------------- | ---------------------------------------- |
| `marta`  | `demo123` | Administrador | Todo                                     |
| `andres` | `demo123` | Recepción     | Citas y clientes                         |

Son cuentas de la base de desarrollo local; la autenticación es real (la API firma un token). El token vive en `sessionStorage` (clave `datemanager.sesion.v2`), vence a los 60 minutos y se borra al cerrar la pestaña; la contraseña nunca se guarda. Si el token falta o es inválido, vuelves a `#/login` con el aviso «Tu sesión venció».

## Rutas

Con el servidor en `http://localhost:4200`:

- `#/login`: inicio de sesión del negocio.
- `#/agendar/:codigo`: página pública del cliente (no requiere cuenta); el código viene de un enlace de invitación creado en `#/enlaces`.
- `#/panel`, `#/agenda`, `#/citas`, `#/servicios`, `#/clientes`, `#/usuarios`, `#/enlaces`: panel; piden iniciar sesión y se adaptan a los permisos del rol (si la ruta no está permitida te lleva a la primera pantalla que sí puedes ver, o a «sin acceso»).

## Qué probar

| Para ver…                | Haz esto                                                                                                                                              |
| ------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| Enlace de invitación     | Como `marta`, en Enlaces crea uno (por ejemplo 1440 minutos y 1 uso) y copia su ruta. Se abre en otra pestaña sin sesión.                             |
| Reservar una cita        | En `#/agendar/<codigo>` elige servicio, día y hora, completa tus datos y pulsa «Solicitar cita». Queda Confirmada y aparece en Citas, Agenda y Panel. |
| Enlace agotado           | Con el enlace de 1 uso ya usado, otra reserva muestra «Este enlace ya no tiene reservas disponibles». Vencido, desactivado o inexistente también muestran su mensaje. |
| Cancelar y mover         | En Citas, «Cancelar cita» libera el horario (vuelve a salir en la página pública). «Mover cita» solo ofrece horas libres del mismo servicio.          |
| Roles y permisos         | En «Usuarios y roles» crea un rol (por ejemplo solo «Ver citas»), crea un usuario con ese rol y entra con él: el menú y las rutas muestran solo lo permitido. `andres` no ve Servicios, Usuarios ni Enlaces. |
| Reglas del sistema       | Intenta quitar «Gestionar roles y permisos» al rol Administrador: la API no lo permite. Un rol con usuarios asignados no se puede eliminar.            |
| Servicios y horarios     | Desactiva un servicio y desaparece de la página pública. Quítale todas las ventanas a un servicio activo y aparece la alerta «Servicio sin horario» en el Panel. |

## Dónde se guardan los datos

En la base de datos de la API (SQL Server). El navegador solo guarda el token de sesión en `sessionStorage`. Las cifras del Panel las calcula la API (`GET /api/panel/inicio`), y el Panel se recarga cada minuto para mantener al día el «en N min» de la próxima cita.

## Estructura

El acceso a datos pasa por servicios HTTP en `src/app/core`: `AgendaRepo` (clase abstracta; `HttpAgendaRepo` es la implementación del panel y se registra en `app.config.ts`), `AuthApi`, `InicioApi`, `EnlacesApi` y `PublicoApi`. Un interceptor añade el `Bearer` solo a `/api/panel/**` y `GET /api/auth/yo` (nunca a `/api/publico/**` ni al login) y otro traduce los errores de la API (`{codigo, mensaje, campos?}`) en `ApiError`; un 401 con sesión abierta devuelve a `#/login` una sola vez. Las reglas de horarios y permisos (`core/horarios.ts`, `core/permisos.ts`) son lógica pura. Los dobles de prueba (`src/app/testing`) no forman parte de la aplicación.

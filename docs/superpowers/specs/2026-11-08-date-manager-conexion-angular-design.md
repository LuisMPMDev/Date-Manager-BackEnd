# DateManager — Conexión del Angular con la API (Fase 2b)

Estado: borrador para aprobación. Lo que dijo el usuario va sin marca; lo que decidí yo lleva *(Decisión mía.)* para que se pueda corregir.

## 1. Intención y por qué

La Fase 1 entregó una demo en Angular que guarda todo en el navegador. La Fase 2 entregó la API en Java (`src/Date.Manager.Back`, ya en `main`): login real con JWT, permisos comprobados en el servidor, enlaces de invitación y zona pública del cliente. Esta fase hace que el Angular **use esa API** en lugar de la demo, de modo que el proyecto funcione de punta a punta: el negocio inicia sesión, gestiona servicios, citas, clientes, usuarios y roles, genera enlaces de invitación, y el cliente agenda entrando por el enlace.

Después viene la Fase 3 (validaciones en la base de datos), con su propio plan.

## 2. Metas, no metas, anti-metas

**Metas**
- Todas las pantallas del negocio leen y escriben contra la API, con los permisos que devuelve el servidor.
- El **modo demo desaparece**: no hay datos sembrados en el navegador ni repositorio en memoria en el código de producción. La demo queda guardada en la rama local `demo-sin-servidor` (ya creada).
- El cliente solo agenda con un enlace: ruta `/agendar/<código>`, con el flujo de reserva que ya existe. **La ruta `/reservar` desaparece.**
- Pantalla nueva **Enlaces** (crear con duración y usos opcional, ver estado y usos, copiar la ruta, desactivar).
- Los errores de la API se muestran con su mensaje en español; los de campo, junto al campo.
- Se puede comprobar a mano de punta a punta contra la API real con los datos de ejemplo (`marta` / `demo123`).

**No metas**
- Validaciones de base de datos (Fase 3) y cambios de reglas de negocio.
- Que el cliente vea, cancele o mueva su cita; sigue siendo cosa del negocio.
- Despliegue, Docker, dominios, HTTPS. La API se usa en `http://localhost:8080`.
- Recuperar contraseña, registro de usuarios por su cuenta, «recordarme» entre sesiones.

**Anti-metas** (sería un fracaso aunque «funcione»)
- Dejar código de la demo vivo en producción (un `DemoAgendaRepo`, la semilla del navegador o el botón de reiniciar datos) que haga creer que algo se guardó sin API.
- Guardar la contraseña en el navegador o mostrar un enlace de invitación a quien no tiene `gestionar_enlaces`.
- Que un token vencido deje una pantalla en blanco o en un ciclo de redirecciones.
- Que dos clics rápidos en «Reservar» creen dos citas o muestren un error confuso.

## 3. Lo que ya está decidido

- El modo demo desaparece; la demo vive en `demo-sin-servidor`.
- `/reservar` desaparece; el cliente entra solo por `/agendar/<código>`.
- La API ya existe y no se rediseña aquí. Si al conectar aparece un desajuste real del contrato, se corrige con el cambio mínimo en la API y se anota (ver §9).
- Angular 21, pruebas con Vitest (`ng test`), rutas con hash (`withHashLocation`), como hoy.

## 4. Cómo se ve el uso

**Negocio.** La raíz `/` lleva a `/login` *(Decisión mía.)*. Tras iniciar sesión se ve el menú de siempre, filtrado por los permisos que devuelve la API, más **Enlaces** si el rol tiene `gestionar_enlaces`. Las pantallas existentes (panel, agenda, citas, servicios, clientes, usuarios y roles) funcionan igual de cara al usuario, pero leen y escriben en la API.

**Enlaces (nueva).** Lista de enlaces (más nuevos primero) con estado (Activo, Vencido, Agotado, Desactivado), usos y máximo, vencimiento y quién lo creó. Formulario «Crear enlace»: duración (por defecto 24 h; la API acepta de 60 minutos a 30 días) y máximo de usos opcional. Al crear, se muestra la ruta completa `…/#/agendar/<código>` con botón de copiar. Los enlaces se pueden desactivar. El código completo se muestra en la lista solo para quien tiene `gestionar_enlaces` *(Decisión mía.)*.

**Cliente.** Abre `/agendar/<código>`. Si el enlace está activo ve los servicios activos y sigue el flujo existente: servicio → fecha → horario libre → datos → confirmación con los datos de su cita (servicio, fecha, inicio, fin). Si el enlace está vencido, agotado o desactivado ve un mensaje claro de que debe pedir uno nuevo al negocio, sin formulario. Si el código no existe ve «enlace no encontrado». Si el horario se ocupó mientras llenaba los datos, ve el aviso y la lista de horarios se recarga.

**Sesión.** El token se guarda en `sessionStorage` (por pestaña, como la sesión de la demo) *(Decisión mía.)* y se envía en cada llamada del panel. Al recargar, la sesión se restaura consultando `GET /api/auth/yo`. Si el token vence (60 minutos) o el usuario fue borrado, se limpia la sesión y se vuelve a `/login` con un aviso. Si la API responde 403, se muestra «sin acceso» sin cerrar la sesión. La contraseña nunca se guarda.

## 5. Arquitectura

- **Interfaz de acceso a datos.** Se conserva la clase abstracta `AgendaRepo` como punto de unión que ya usan las pantallas *(Decisión mía: menos cambios en pantallas)*. Se escribe `HttpAgendaRepo` (con `HttpClient`) como única implementación en producción y se borran `DemoAgendaRepo`, `seed.ts` y `reiniciar()`.
- **La interfaz se ajusta a lo que la API ofrece**: sin `actorId` en `guardarRol` (lo deduce el servidor del token), `iniciarSesion` devuelve la sesión `{token, usuario, rol}`, y el modelo `Usuario` del Angular deja de llevar `clave` salvo en el formulario de alta o cambio de contraseña.
- **Zona pública aparte.** Un servicio `PublicoApi` (sin token) con las cuatro operaciones del enlace: estado del enlace, servicios, horarios de un servicio en una fecha, y reservar. No comparte el repositorio del panel.
- **Interceptores.** Uno añade `Authorization: Bearer` solo a `/api/panel/**` y `GET /api/auth/yo`; nunca a `/api/publico/**` ni al login. Otro traduce los errores `{codigo, mensaje, campos?}` de la API a un error tipado del Angular y maneja el 401 del panel (limpiar sesión y redirigir al login con aviso) sin ciclos.
- **Dirección de la API.** Un único punto de configuración con valor por defecto `http://localhost:8080/api` *(Decisión mía)*; la API ya permite el origen `http://localhost:4200`.
- **Tablero.** El panel deja de calcular cifras en el navegador y consume `GET /api/panel/inicio` (la API ya calcula ocupación, variaciones, ingresos, alertas, series y próximas citas con las definiciones de la demo). Se borra el cálculo duplicado del Angular (`metricas`, `panel-kpis`, `panel-datos` y lo que solo ellos usen). El formato (porcentajes, moneda, textos de alertas) y las gráficas se quedan en el Angular. El tablero mueve con `?fecha=` solo las cifras por día; próximas citas, nuevas/canceladas en 30 días y alertas son siempre «ahora» (decisión sobre el tablero de la Fase 2: el reloj real fija esas cifras).
- **Recarga tras escribir.** Se mantiene la señal `cambios` que sube tras cada escritura exitosa para refrescar las pantallas *(Decisión mía)*.
- **Pruebas.** Las pruebas de pantallas hoy usan el repositorio de la demo. En su lugar usan un doble en memoria que vive solo en `src/app/testing/` (movido de la demo, no usado en producción) *(Decisión mía: conserva la cobertura existente a bajo costo)*. El nuevo `HttpAgendaRepo`, `PublicoApi`, los interceptores y la sesión se prueban con `HttpTestingController`.

## 6. Contrato con la API (lo que el cliente debe respetar)

Detalle completo en `src/Date.Manager.Back/README.md` y en Swagger (`/swagger-ui.html`, perfil `dev`). Lo que cambia el comportamiento del Angular:

- **Errores:** JSON `{codigo, mensaje, campos?}`. 400 `VALIDACION` (con `campos`), 401 `NO_AUTENTICADO` / `CREDENCIALES_INVALIDAS`, 403 `SIN_PERMISO`, 404 `NO_ENCONTRADO` / `ENLACE_NO_ENCONTRADO`, 409 `HORARIO_NO_DISPONIBLE` / `ESTADO_INVALIDO` / `CITA_NO_HA_INICIADO` / `CONFLICTO`, 410 `ENLACE_VENCIDO` / `ENLACE_AGOTADO` / `ENLACE_DESACTIVADO`, 500 `ERROR_INTERNO`.
- **Claves nulas omitidas:** `canceladaEn`, `maxUsos`, `proxima`, `variacion`, `minutosHasta` pueden no venir; el cliente los trata como opcionales.
- **Claves de `campos`** pueden ser `cliente.correo` (reserva) o `correo` según la capa que rechaza; el cliente las normaliza.
- **Nueve permisos**, incluido `gestionar_enlaces`, que el tipo `Permiso` del Angular aún no tiene. Los permisos y roles los decide el servidor; las dependencias (`gestionar_citas` y `atender_citas` implican `ver_citas`) se siguen mostrando en el formulario de roles, pero el servidor es la autoridad.
- **Fechas** `YYYY-MM-DD`, horas `HH:mm` (hora del negocio), instantes ISO UTC; importes como número.
- `EnlaceDto` incluye además `creadoPorNombre`. La reserva pública responde `{servicioId, servicio, fecha, inicio, fin, estado}` sin id de cita.
- **Sin paginación** en citas, clientes ni enlaces. Es aceptable a escala de curso.

## 7. Criterios de aceptación

1. Con la API en perfil `dev` y el Angular en `ng serve`, se puede: iniciar sesión como `marta`, ver el tablero con cifras reales, crear un enlace de 24 h con 1 uso, copiar su ruta, abrirla **sin sesión** (otra pestaña o ventana privada), reservar un horario, ver la cita en el panel y en la agenda, cancelarla y comprobar que el horario vuelve a salir libre.
2. Con el mismo enlace de 1 uso, una segunda reserva muestra el mensaje de enlace agotado y no crea cita.
3. Entrando como `andres` (Recepción) no aparecen Servicios, Usuarios ni Enlaces en el menú, y abrir su URL directo lo manda a la primera pantalla que su rol sí puede ver (o a «sin acceso» si no tiene ninguna); la API además responde 403 si se fuerza la llamada.
4. Un token vencido o inválido (se simula editando `sessionStorage` o esperando) lleva a `/login` con aviso y sin ciclo.
5. `/reservar` ya no existe, y en el código de producción no queda `DemoAgendaRepo`, `seed.ts` ni el botón de reiniciar datos.
6. `ng test` y `ng build` pasan sin advertencias nuevas; las pruebas de la API (`mvn test`) siguen en verde si se tocó algo de la API.
7. Un enlace vencido, agotado, desactivado y uno inexistente muestran cada uno su mensaje correcto al cliente.

## 8. Qué se deja al constructor

Estructura de archivos y nombres internos; el diseño visual de la pantalla Enlaces dentro del estilo actual; el texto exacto de los mensajes que no vienen de la API; cómo organizar los dobles de prueba; el mecanismo exacto de la configuración de la dirección de la API.

## 9. Cambios permitidos en la API en esta fase

Solo los que aparezcan al conectar y sean un desajuste real del contrato (por ejemplo un campo que falta para una pantalla o una clave de `campos` incoherente). Cada uno es un cambio mínimo con su prueba, se anota en el informe de la fase y no cambia reglas de negocio. No se agrega paginación, límite de tasa ni nuevas funciones.

## 10. Decisiones menores que tomé yo *(Decisión mía.)*

Salieron de una lectura independiente del documento contra el código; las anoto para que se puedan corregir.

1. **«Próximas citas» del tablero.** La tabla del panel muestra cliente, servicio, teléfono y estado, pero `GET /api/panel/inicio` no devuelve el teléfono. Se agrega `clienteTelefono` a las próximas citas de la API (cambio mínimo del §9, con su prueba). La columna Estado se quita: todas las próximas son Confirmadas. Costo si me equivoco: el teléfono de un cliente queda visible para roles con solo `ver_panel`; se quita la columna y el campo.
2. **Pantallas que necesitan un segundo recurso sin permiso.** Un rol personalizado puede ver una pantalla pero no uno de sus datos auxiliares (por ejemplo Clientes carga también las citas, que exigen `ver_citas`). El 403 de una llamada secundaria se muestra como un aviso dentro de esa sección («no tienes permiso para ver esta información») y la pantalla sigue funcionando con lo que sí puede ver. El 403 de la llamada principal de una ruta lleva a «sin acceso». No se cambian permisos de la API.
3. **Guardia de permisos.** Se conserva la conducta actual: sin el permiso, la guardia manda a la primera ruta que el rol sí puede ver, y solo a «sin acceso» si no tiene ninguna. Por eso el criterio 3 dice eso.
4. **«Hoy» en el Angular.** El tablero no envía `?fecha=` cuando se ve el día actual, para que el servidor decida con su zona horaria. El flujo público y la agenda usan el reloj del navegador, y se acepta que en el cambio de día puedan diferir unos minutos de la hora del negocio.
5. **Botones de acceso rápido de la demo.** Los atajos `marta` / `demo123` y `andres` / `demo123` de la pantalla de login se eliminan. Son código de la demo.
6. **Ids.** Las pantallas generan ids UUID (la API exige UUID en `PUT /servicios|roles|usuarios|clientes/{id}`), en lugar de los `srv-…`, `rol-…`, etc. de la demo.
7. **Errores sin `campos`.** Muchos 400 `VALIDACION` traen solo `mensaje`. Cuando no hay `campos`, el mensaje se muestra como error general del formulario.
8. **Restaurar sesión.** `GET /api/auth/yo` no devuelve token: el token se conserva desde `sessionStorage` y `yo` solo confirma usuario, rol y permisos vigentes.

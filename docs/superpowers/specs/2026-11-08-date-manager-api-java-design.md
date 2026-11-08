# DateManager — API en Java (Fase 2)

Estado: borrador para aprobación. Lo que dijo el usuario va sin marca; lo que decidí yo lleva *(Decisión mía.)* para que se pueda corregir.

## 1. Intención y por qué

DateManager es una plataforma genérica para agendar citas (barbería, veterinaria, consultorio…) hecha para un curso. La Fase 1 entregó una demo en Angular que guarda todo en el navegador (`src/Date.Manager.Front`, ya integrada en `main`). La Fase 2 construye el **backend real**: una API en Java con base de datos SQL Server, con inicio de sesión de verdad, permisos comprobados en el servidor y un sistema de **enlaces de invitación** con los que el negocio deja que sus clientes agenden.

La meta de la fase: que la API haga, para usuarios normales, lo que hoy hace la demo, y que **más adelante** se pueda conectar el Angular sin rediseñarlo. Conectar el Angular **no** es parte de esta fase.

Las tres fases siguen siendo: (1) demo Angular ✅, (2) esta API, (3) validaciones de base de datos.

## 2. Metas, no metas, anti-metas

**Metas**
- API REST en Java + SQL Server que cubre todo lo que la demo hace: servicios con ventanas de disponibilidad, horarios libres, reservas, citas (cancelar, mover, marcar atendida), clientes, usuarios, roles y permisos configurables.
- Inicio de sesión real (contraseñas con hash, sesión por token) y **permisos comprobados en el servidor** en cada acción.
- Dos zonas separadas: **zona del cliente** (sin cuenta, solo con un enlace de invitación) y **zona del negocio** (con login y permisos).
- Enlaces de invitación generados por el admin: código único, caducidad configurable, máximo de usos opcional, se pueden desactivar.
- Las reglas de negocio de la demo se mantienen, pero ahora las garantiza el servidor, incluso con dos clientes reservando a la vez.
- Contrato de la API pensado para que el `AgendaRepo` del Angular se pueda implementar contra ella con poco esfuerzo.

**No metas (en esta fase)**
- Conectar el Angular a la API (fase posterior).
- Enviar correos o mensajes: el admin copia el enlace y lo manda él mismo.
- Varios negocios en la misma API. Hay un solo negocio.
- Que el cliente consulte, cancele o mueva su cita. Eso lo hace el negocio desde el panel, como en la demo.
- Despliegue en servidores, Docker de producción, monitoreo.
- Validaciones profundas de base de datos: son la Fase 3. Aquí solo van las restricciones básicas que la API necesita para ser correcta.

**Anti-metas** (sería un fracaso aunque «funcione»)
- Dos clientes que reservan el mismo horario a la vez y ambos reciben «confirmada».
- Un enlace con máximo de 1 uso que acepta dos citas porque llegaron juntas.
- Que un usuario sin permiso haga una acción editando la petición, porque solo el Angular lo bloqueaba.
- Contraseñas, claves de base de datos o secretos guardados en el repositorio.

## 3. Lo que ya está decidido

- Lenguaje **Java**, base de datos **SQL Server** (el usuario trabaja con SQL Server Management Studio).
- Inicio de sesión real, no el de juguete de la demo.
- Pantallas del cliente separadas de las del negocio.
- El admin genera un enlace; quien lo abre ve **todos los servicios disponibles** y agenda con el flujo ya definido en la Fase 1 (servicio → día → hora → datos → cita confirmada). El enlace no está atado a un servicio ni a un cliente.
- El enlace es único, como una invitación de Discord, caduca tras un tiempo configurable y tiene **máximo de usos** opcional.
- Esta fase es de **diseño y construcción** de la API; la conexión con el Angular viene después.

## 4. Cómo se construye *(Decisiones mías, salvo Java y SQL Server)*

- **Spring Boot** con Maven (el `pom.xml` ya existe). Versión estable más reciente que soporte Java 25; quien construya debe verificarlo antes de empezar y, si no hay soporte, bajar el nivel de Java del `pom.xml` y dejarlo anotado.
- Spring Web (REST), Spring Data JPA (Hibernate), Spring Security, Bean Validation, driver oficial de Microsoft para SQL Server.
- **Migraciones con Flyway** en scripts SQL versionados: el esquema queda legible y revisable, y la Fase 3 puede auditarlo y endurecerlo sin tocar Java.
- Documentación de la API con OpenAPI (springdoc), para probar desde el navegador.
- Pruebas con JUnit 5 y MockMvc. Las pruebas de concurrencia y de restricciones deben correr contra **SQL Server de verdad** (por ejemplo Testcontainers si hay Docker, o una base de pruebas local); no contra una base «parecida» que oculte diferencias.
- Paquetes en minúsculas y por responsabilidad (por ejemplo `dominio`, `aplicacion`, `api`, `infraestructura`). El esqueleto actual (`App.Domain`, clases con `Scanner` y métodos de consola) **se descarta y se rediseña**: el modelo no debe leer de consola ni imprimir.
- Configuración por perfiles (`dev`, `prod`) y variables de entorno para la conexión a la base y los secretos. Nada de secretos en el repositorio.
- CORS configurable; por defecto permite el origen del Angular en desarrollo (`http://localhost:4200`).

## 5. Modelo de datos

Entidades del PDF y de la demo: `Usuario`, `Rol` (+ permisos), `Servicio`, `VentanaDisponibilidad`, `Cliente`, `Cita`, y la nueva `EnlaceInvitacion`.

- **Identificadores:** UUID (`UNIQUEIDENTIFIER`) en todas las tablas, como en el esqueleto Java.
- **Cita:** guarda `fecha`, `hora_inicio` y `hora_fin` (como en el PDF y en la demo), así que cambiar un servicio después **no modifica citas ya hechas**. Estado con tres valores: `Confirmada`, `Atendida`, `Cancelada`. Nace `Confirmada`. Guarda la fecha de creación y la de cancelación, y, si vino por un enlace, cuál.
- **VentanaDisponibilidad:** día de la semana **ISO 1–7** (lunes = 1, domingo = 7), hora inicio < hora fin, sin cruzar la medianoche.
- **Rol y permisos:** el catálogo lo define el sistema, no se inventan permisos. El catálogo de la demo (`ver_panel`, `ver_citas`, `gestionar_citas`, `atender_citas`, `gestionar_servicios`, `gestionar_clientes`, `gestionar_usuarios`, `gestionar_roles`) se mantiene y se **añade `gestionar_enlaces`** para generar, ver y desactivar enlaces. *(Decisión mía.)* Los permisos se guardan por rol en una tabla de relación.
- **Usuario:** nombre de usuario único sin distinguir mayúsculas ni espacios laterales; contraseña **solo como hash** (BCrypt o equivalente); un rol.
- **Cliente:** se identifica por **correo**, sin distinguir mayúsculas. Si ya existe, se reutiliza y se actualizan nombre y teléfono (regla de la demo).
- **EnlaceInvitacion:** `id`, `codigo` (aleatorio, impredecible, mínimo 128 bits, apto para URL), `creado_por`, `creado_en`, `vence_en`, `max_usos` (nulo = sin límite), `usos`, `desactivado_en`. Se guarda el código tal cual porque el admin debe poder volver a copiarlo. *(Decisión mía.)*
- **Restricciones básicas en la base** (la Fase 3 las ampliará): llaves foráneas, `CHECK` de fin > inicio, de día 1–7 y de estados válidos, únicos de correo de cliente y de usuario, e índice único filtrado `(servicio, fecha, hora_inicio)` sobre citas no canceladas como red de seguridad contra el doble agendamiento exacto.

## 6. Reglas de negocio

Se mantienen **todas** las de la sección 7 de la Fase 1 (`docs/superpowers/specs/2026-11-06-date-manager-demo-angular-design.md`): agenda independiente por servicio, horarios cada 30 minutos dentro de las ventanas con la cita completa dentro de la ventana, no reservar en el pasado, máximo 30 días de anticipación, cita confirmada sin aprobación, revalidación al guardar, servicios inactivos fuera de lo público, dependencias de permisos (`gestionar_citas` y `atender_citas` exigen `ver_citas`), protecciones de roles y usuarios. La diferencia es **dónde viven**: en el servidor, como lógica pura con pruebas, no en el navegador.

Decisiones nuevas *(mías)*:
- **Zona horaria única del negocio**, configurable (por defecto `America/Bogota`). «Ahora», «hoy» y «ya pasó» se calculan con ella. Las fechas y horas viajan sin zona (`2026-11-08`, `09:30`).
- **El servidor es la autoridad.** Cada reserva o movimiento se valida dentro de una transacción que bloquea la agenda del servicio (por ejemplo bloqueo de la fila del servicio) para que dos peticiones simultáneas no ocupen el mismo horario. Gana una; la otra recibe `HORARIO_NO_DISPONIBLE`.
- **Protecciones que ahora comprueba el servidor:** un administrador no puede quitarse a sí mismo `gestionar_roles`; un rol con usuarios no se elimina; nadie se elimina a sí mismo; no se puede quitar o degradar al último usuario con `gestionar_roles`.
- Al cancelar o mover una cita **no se devuelve un uso** del enlace. *(Decisión mía.)*
- **Transiciones de estado de una cita** (la demo solo las impedía ocultando botones; el servidor las impone). *(Decisión mía.)*
  - Marcar como atendida: solo una cita `Confirmada` cuya hora de inicio ya llegó. Error `ESTADO_INVALIDO` si no es `Confirmada`, `CITA_NO_HA_INICIADO` si todavía no empieza.
  - Cancelar: solo una `Confirmada`. Cancelar una ya cancelada responde sin error y sin cambios (idempotente); cancelar una `Atendida` es `ESTADO_INVALIDO`.
  - Mover: solo una `Confirmada` y a un horario libre futuro; mover una `Cancelada` o `Atendida` es `ESTADO_INVALIDO`.
  - `Atendida` y `Cancelada` son finales: no se reabren en esta fase.

## 7. Enlaces de invitación

- **Quién:** el usuario con `gestionar_enlaces` genera enlaces desde el panel. En el futuro Angular, el enlace tendrá la forma `https://<sitio>/#/agendar/<codigo>`; la API solo devuelve el código y la ruta lógica, y el sitio base es configuración.
- **Al crearlo, el admin elige:** cuánto dura (mínimo 1 hora, máximo 30 días, 24 horas por defecto) y, opcionalmente, el máximo de usos. *(Los límites y el valor por defecto son decisión mía.)*
- **Un uso = una cita agendada con ese enlace.** El conteo y la reserva ocurren en **la misma transacción**; con máximo 1 uso y dos peticiones simultáneas, solo una cita se crea.
- **Estados** de un enlace, derivados: `Activo`, `Vencido`, `Agotado`, `Desactivado`. El admin ve la lista con estado, usos, vencimiento y quién lo creó, y puede **desactivar** cualquiera.
- **Validez en cada paso.** Las cuatro operaciones de la zona del cliente se comportan así *(decisión mía)*: «ver estado del enlace» siempre responde (con el estado `Activo`, `Vencido`, `Agotado` o `Desactivado`) para poder mostrar un mensaje claro; servicios activos, horarios libres y reservar **exigen un enlace `Activo`** y, si no lo es, fallan con el `ENLACE_*` correspondiente. Así nadie que no tenga un enlace vigente puede consultar la disponibilidad del negocio. El agotado por usos solo se detecta de forma definitiva al reservar (otro cliente puede gastar el último uso mientras tanto).
- **Al abrirlo**, el cliente recibe la lista de servicios activos y sigue el flujo normal. Si el enlace deja de valer a mitad del flujo, la reserva falla con un error claro (`ENLACE_VENCIDO`, `ENLACE_AGOTADO`, `ENLACE_DESACTIVADO`) y la pantalla futura podrá explicarlo.
- Un código desconocido responde igual que uno inexistente (sin pistas de cómo adivinar otros).
- No se hace límite de peticiones (rate limiting) en esta fase; queda anotado como pendiente.

## 8. Inicio de sesión y permisos

- `POST` de login con usuario y contraseña. El usuario no distingue mayúsculas ni espacios laterales; la contraseña es exacta.
- Respuesta: un **token de acceso** (JWT firmado) de vida corta *(60 minutos, decisión mía)* y los datos del usuario con su rol y permisos. Sin refresco de token en esta fase.
- El token solo identifica al usuario. **Los permisos se leen de la base en cada petición**, así que si el admin cambia un rol, el efecto es inmediato. *(Decisión mía.)*
- Cada operación del panel declara el permiso que exige; sin él, responde 403. Sin token válido, 401.
- La clave de firma del token y el administrador inicial **no están en el repositorio**: se leen de variables de entorno. Si no hay ningún usuario al arrancar, se crea el administrador inicial con esas variables. El perfil `dev` puede cargar los datos de ejemplo de la demo (roles Administrador y Recepción, tres servicios, clientes y citas) con claves de ejemplo claramente falsas. *(Decisión mía.)*

## 9. Contrato de la API

REST sobre JSON, con **los mismos nombres y formas que los modelos del Angular** (`src/Date.Manager.Front/src/app/core/models.ts`) para que la conexión posterior sea mecánica. Errores en un formato uniforme (problem details) con un `codigo` estable (`HORARIO_NO_DISPONIBLE`, `ENLACE_VENCIDO`, `SIN_PERMISO`…), un mensaje en español y, si aplica, los campos inválidos. Quien construya fija las rutas exactas; esta es la superficie mínima:

| Zona | Qué cubre |
|---|---|
| Autenticación | iniciar sesión; datos del usuario actual (para refrescar permisos) |
| Cliente (por código de invitación) | ver estado del enlace; servicios activos; horarios libres de un servicio en un día; reservar |
| Panel · enlaces | crear, listar, ver, desactivar |
| Panel · servicios | listar, crear, editar (con sus ventanas), activar/desactivar |
| Panel · citas | listar por rango y filtros; reservar a nombre de un cliente; mover; cancelar; marcar atendida; horarios libres con exclusión de la propia cita |
| Panel · clientes | listar, editar |
| Panel · usuarios y roles | listar, crear, editar, eliminar, con las protecciones de la sección 6; catálogo de permisos |
| Panel · inicio | los datos del dashboard (indicadores, alertas, citas por día, ocupación) |

Correspondencia con el `AgendaRepo` de la demo: `servicios`, `guardarServicio`, `horariosLibres`, `reservar`, `citas`, `cancelarCita`, `moverCita`, `marcarAtendida`, `clientes`, `guardarCliente`, `roles`, `guardarRol`, `eliminarRol`, `usuarios`, `guardarUsuario`, `eliminarUsuario`, `iniciarSesion` tienen cada uno su operación. `reiniciar` **no** va en la API; en `dev` se resuelve con un comando o perfil de siembra.

## 10. Pruebas y cuándo la fase se considera lista

Pruebas (quien construya decide el detalle):
- La lógica de horarios, permisos y métricas se porta como código puro y **reutiliza los casos de prueba de la demo** (`core/horarios.spec.ts` y compañía) como casos de referencia.
- Integración de cada operación contra SQL Server real, incluido el 401/403.
- **Concurrencia:** dos reservas simultáneas del mismo horario (gana una) y dos usos simultáneos de un enlace de 1 uso (gana uno).
- Protecciones de roles y usuarios, caducidad y agotamiento de enlaces, límites de 30 días y de pasado.

La fase está lista cuando:
1. `mvn test` pasa y la API arranca contra SQL Server con migraciones aplicadas desde cero.
2. Con los datos de ejemplo, un recorrido completo funciona solo con llamadas a la API (desde OpenAPI o un archivo de peticiones): iniciar sesión, generar un enlace, usarlo para reservar como cliente, ver la cita en el panel, cancelarla y ver el horario libre otra vez.
3. Un usuario sin el permiso correspondiente recibe 403 en cada operación protegida, y un enlace vencido, agotado o desactivado no permite reservar.
4. El repositorio no contiene secretos, y el README explica cómo preparar la base en SQL Server, qué variables definir y cómo arrancar.

## 11. Riesgos y pendientes

- **Datos que solo el usuario puede dar al construir:** nombre de la instancia de SQL Server, modo de autenticación (Windows o usuario/clave) y si tiene Docker. Con eso se fija la cadena de conexión y la estrategia de pruebas.
- **Java 25 y Spring Boot:** la compatibilidad se verifica al empezar (sección 4).
- **Agenda por servicio vs. por profesional:** sigue abierto desde la Fase 1; no cambia esta fase.
- **Sin límite de peticiones ni bloqueo por intentos fallidos** de login: aceptable para un proyecto de curso, anotado para producción.
- **Escalada de privilegios por asignar roles.** La demo dejó abierto que un rol con `gestionar_usuarios` pero sin `gestionar_roles` se asigne un rol más poderoso. En la API **queda cerrado**: al crear o editar un usuario, quien no tiene `gestionar_roles` solo puede asignar un rol cuyos permisos sean un subconjunto de los suyos; si no, `SIN_PERMISO`. *(Decisión mía.)*
- **Borrar usuarios que crearon enlaces:** no se permite borrar un usuario con enlaces creados (se desactiva su cuenta en una fase posterior); evita llaves foráneas colgando. *(Decisión mía.)*
- **Reservar con un correo existente** actualiza nombre y teléfono de ese cliente (regla de la demo). Quien tenga un enlace podría alterar los datos de un cliente conocido; se acepta en un proyecto de curso y queda anotado.
- **Conexión con Angular:** cuando llegue, requiere una implementación de `AgendaRepo` contra la API, la pantalla de «Enlaces» en el panel, la ruta `#/agendar/<codigo>` y el manejo del token. Queda para su propia fase.

## 12. Orden sugerido y qué queda a criterio de quien construya

Orden (se puede recortar desde el final): (1) proyecto Spring Boot, conexión y migraciones base; (2) modelo y reglas puras con sus pruebas; (3) usuarios, roles, permisos y login; (4) servicios y horarios libres; (5) reservas y concurrencia; (6) enlaces de invitación; (7) citas del panel, clientes y dashboard; (8) datos de ejemplo y README.

A criterio de quien construya: rutas exactas, nombres de paquetes y clases, versión de librerías, estrategia exacta de bloqueo para la concurrencia, formato exacto del token, y cómo organizar las migraciones, siempre dentro de lo anterior.

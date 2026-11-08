# DateManager API (Fase 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir la API REST en Java + SQL Server que reemplaza el almacén local de la demo: login real, permisos en el servidor, reservas sin doble agendamiento y enlaces de invitación para que los clientes agenden.

**Architecture:** Spring Boot en capas. `dominio` es Java puro (registros, enums y reglas sin Spring ni base de datos) y se prueba solo; `aplicacion` son casos de uso transaccionales; `infraestructura` tiene JPA, seguridad y reloj; `api` tiene controladores y DTOs. El esquema vive en migraciones Flyway (SQL). La lógica de horarios, permisos y métricas se **porta** de la demo Angular (`src/Date.Manager.Front/src/app/core/*.ts`), cuyas pruebas (`*.spec.ts`) son los casos de referencia.

**Tech Stack:** Java (nivel que soporte Spring Boot estable; ver Prerrequisitos), Spring Boot + Maven, Spring Web, Spring Data JPA/Hibernate, Spring Security (JWT HS256), Bean Validation, Flyway, driver `mssql-jdbc`, springdoc-openapi, JUnit 5 + MockMvc + AssertJ.

**Spec:** `docs/superpowers/specs/2026-11-08-date-manager-api-java-design.md` (reglas de la Fase 1 en `docs/superpowers/specs/2026-11-06-date-manager-demo-angular-design.md`, sección 7).

## Prerrequisitos (los da el usuario antes de la Tarea 1)

Instancia de SQL Server accesible (la del usuario, con SSMS) y dos bases vacías: `DateManager` y `DateManagerTest`; cadena de conexión y credenciales (usuario SQL o autenticación integrada). Se pasan por variables de entorno, nunca en el repositorio. Si el JDK instalado no es 25 o Spring Boot no lo soporta, bajar `maven.compiler.release` al más alto soportado y anotarlo en el README.

## Global Constraints

- Directorio del proyecto: `src/Date.Manager.Back`. Paquete base `com.datemanager` con subpaquetes `dominio`, `aplicacion`, `infraestructura`, `api`. Se **elimina** todo el esqueleto `App.*` (clases con `Scanner`, `Main` de plantilla).
- Variables de entorno: `DATEMANAGER_DB_URL`, `DATEMANAGER_DB_USUARIO`, `DATEMANAGER_DB_CLAVE`, `DATEMANAGER_JWT_SECRETO` (≥ 32 bytes), `DATEMANAGER_ADMIN_USUARIO`, `DATEMANAGER_ADMIN_CLAVE`, `DATEMANAGER_TEST_DB_URL` (+ `_USUARIO`, `_CLAVE`), `DATEMANAGER_ZONA_HORARIA` (por defecto `America/Bogota`), `DATEMANAGER_CORS_ORIGENES` (por defecto `http://localhost:4200`). Ningún secreto en el repositorio.
- Perfiles: `dev` (siembra datos de ejemplo, Swagger UI), `prod`, `test` (usa la base de pruebas; Flyway `clean` permitido **solo** aquí).
- Todo "ahora" sale de un bean `java.time.Clock` en la zona del negocio; ningún código llama `LocalDateTime.now()` ni `Instant.now()` directamente. Fechas `YYYY-MM-DD` y horas `HH:mm` viajan sin zona; `creadaEn`/`canceladaEn` van en ISO UTC.
- Días de la semana ISO 1–7 (lunes = 1, domingo = 7). Paso de horarios 30 min; la cita cabe completa en la ventana; sin horas pasadas; máximo 30 días de anticipación (el día 30 cuenta, el 31 no).
- Estados de cita: `Confirmada`, `Atendida`, `Cancelada`. Nace `Confirmada`.
- Permisos (ids exactos): `ver_panel`, `ver_citas`, `gestionar_citas`, `atender_citas`, `gestionar_servicios`, `gestionar_clientes`, `gestionar_usuarios`, `gestionar_roles`, `gestionar_enlaces`. Dependencia: `gestionar_citas` o `atender_citas` implica `ver_citas`.
- JSON con los nombres de los modelos de la demo (`core/models.ts`): `Servicio{id,nombre,duracionMin,activo,valor,ventanas[{id,dia,inicio,fin}]}`, `Cliente{id,nombre,apellido,telefono,correo}`, `Cita{id,servicioId,clienteId,fecha,inicio,fin,estado,creadaEn,canceladaEn?}`, `Rol{id,nombre,descripcion,permisos[]}`, `Usuario{id,nombre,apellido,correo,telefono,usuario,clave?,rolId}` (la clave solo entra, nunca sale). Ids UUID.
- Errores: JSON `{codigo, mensaje, campos?}` (problem details con propiedades `codigo` y `campos`). Códigos → HTTP: `VALIDACION` 400, `NO_AUTENTICADO` 401, `CREDENCIALES_INVALIDAS` 401, `SIN_PERMISO` 403, `NO_ENCONTRADO` 404, `ENLACE_NO_ENCONTRADO` 404, `HORARIO_NO_DISPONIBLE` 409, `ESTADO_INVALIDO` 409, `CITA_NO_HA_INICIADO` 409, `CONFLICTO` 409, `ENLACE_VENCIDO`/`ENLACE_AGOTADO`/`ENLACE_DESACTIVADO` 410. Mensajes en español.
- Usuario y correo se **normalizan** (trim + minúsculas, `Locale.ROOT`) antes de guardar y comparar; la base nunca se fía de su collation (SQL Server ignora espacios finales).
- Rutas: `/api/auth/*`, `/api/panel/*` (token + permiso), `/api/publico/invitaciones/{codigo}/*` (sin token, solo código). Permisos por ruta del panel (basta **uno** de la lista): servicios GET → `ver_panel`/`ver_citas`/`gestionar_servicios`; servicios PUT → `gestionar_servicios`; clientes GET → `ver_citas`/`gestionar_clientes`; clientes PUT → `gestionar_clientes`; citas GET → `ver_panel`/`ver_citas`; citas POST/mover/cancelar y horarios libres → `gestionar_citas`; atender → `atender_citas`; roles GET → `gestionar_roles`/`gestionar_usuarios`; roles PUT/DELETE y catálogo de permisos → `gestionar_roles`; usuarios → `gestionar_usuarios`; enlaces → `gestionar_enlaces`; inicio → `ver_panel`.
- Los permisos se leen de la base en **cada petición** (el token solo lleva el id de usuario; vida 60 min).
- Los controladores no contienen reglas: delegan en `aplicacion`, que usa `dominio`.

## Review Focus

Entradas que el diseño implica pero ningún caso feliz ejercita, de más a menos probable:
1. **Hora del negocio vs. UTC del servidor.** A las 23:30 en Bogotá, «hoy» debe ser la fecha de Bogotá aunque la JVM esté en UTC (ahí ya es mañana). Pruebas en T2 y T7.
2. **Ventanas que no empiezan en :00/:30 y duraciones que no son múltiplo de 30.** Pruebas en T2.
3. **Espacios finales y mayúsculas en usuario y correo** (SQL Server los ignora o no según el caso): duplicados «Marta » vs «marta». Pruebas en T5 y T7.
4. **Token de un usuario eliminado o con rol modificado después de emitirse.** Pruebas en T4.
5. **Bordes del enlace:** `vence_en == ahora` es vencido; el último uso con dos peticiones simultáneas; una reserva fallida no gasta un uso. Pruebas en T8.

---

### Task 1: Proyecto Spring Boot, conexión, Flyway, errores y base de pruebas

**Files:**
- Modify: `src/Date.Manager.Back/pom.xml` (groupId `com.datemanager`, parent Spring Boot, dependencias de Tech Stack salvo Security)
- Delete: `src/Date.Manager.Back/src/main/java/App/**`
- Create: `com/datemanager/DateManagerApplication.java`, `infraestructura/RelojConfig.java`, `dominio/CodigoError.java`, `dominio/ErrorNegocio.java`, `api/ManejadorErrores.java`
- Create: `src/main/resources/application.yml`, `application-dev.yml`, `application-test.yml`, `db/migration/V1__base.sql` (línea base vacía con comentario)
- Test: `src/test/java/com/datemanager/PruebaIntegracion.java`, `ArranqueTest.java`

**Interfaces:**
- Produces: `enum CodigoError` con los códigos de Global Constraints y su `HttpStatus`; `class ErrorNegocio extends RuntimeException` (`ErrorNegocio(CodigoError, String mensaje)`, getter `codigo()`); bean `Clock`; clase base `PruebaIntegracion` (`@SpringBootTest`, `@AutoConfigureMockMvc`, perfil `test`, un `RelojMutable` reemplazable que fija "ahora" con `fijarAhora(LocalDateTime)` en la zona del negocio, y `limpiarDatos()` que vacía las tablas en orden de dependencias) de la que heredan todas las pruebas de integración.

- [ ] **Step 1: Write the failing test** `ArranqueTest`: (a) el contexto arranca contra `DATEMANAGER_TEST_DB_URL` y Flyway aplica V1; (b) un controlador **solo de prueba** que lanza `ErrorNegocio(CodigoError.CONFLICTO, "x")` responde 409 con JSON `codigo = "CONFLICTO"` y `mensaje = "x"`; (c) `ErrorNegocio` con `HORARIO_NO_DISPONIBLE` responde 409 y con `ENLACE_VENCIDO` responde 410.
- [ ] **Step 2: Run** `mvn test -Dtest=ArranqueTest` — Expected: FAIL (proyecto no compila / sin contexto).
- [ ] **Step 3: Implement** el `pom.xml`, la aplicación, `RelojConfig` (`Clock.system(ZoneId.of(zona))` desde la propiedad `datemanager.zona-horaria`), `CodigoError`/`ErrorNegocio`, `ManejadorErrores` (`@RestControllerAdvice`: `ErrorNegocio`, validación de Bean Validation → `VALIDACION` con `campos`, cualquier otra excepción → 500 sin filtrar detalles), los YAML con las variables de Global Constraints y `application-test.yml` con `spring.flyway.clean-disabled=false` y limpieza al iniciar.
- [ ] **Step 4: Run** `mvn test` — Expected: PASS.
- [ ] **Step 5: Commit** `feat: proyecto Spring Boot con SQL Server, Flyway y manejo de errores`

### Task 2: Dominio puro — modelos, horarios libres y validaciones

**Files:**
- Create: `dominio/{EstadoCita,VentanaDisponibilidad,Servicio,Cliente,Cita}.java` (registros), `dominio/Horarios.java`, `dominio/Validaciones.java`
- Test: `src/test/java/com/datemanager/dominio/HorariosTest.java`, `ValidacionesTest.java`

**Interfaces:**
- Produces: `Horarios.PASO_MIN = 30`, `Horarios.MAX_DIAS_ANTICIPACION = 30`; `static List<LocalTime> horariosLibres(Servicio servicio, LocalDate fecha, List<Cita> citas, LocalDateTime ahora, UUID excluirCitaId)` (porta `core/horarios.ts`; `excluirCitaId` puede ser `null`; un servicio inactivo devuelve vacío salvo que `excluirCitaId != null`); `Validaciones.errorServicio(Servicio): Optional<String>` (mensajes en español de `core/validaciones.ts`), `Validaciones.esCorreoValido(String)`, `Validaciones.normalizarCorreo(String)`, `Validaciones.normalizarUsuario(String)`.
- Los registros llevan los campos de Global Constraints (`Servicio(UUID id, String nombre, int duracionMin, boolean activo, BigDecimal valor, List<VentanaDisponibilidad> ventanas)`, `VentanaDisponibilidad(UUID id, int dia, LocalTime inicio, LocalTime fin)`, `Cita(UUID id, UUID servicioId, UUID clienteId, LocalDate fecha, LocalTime inicio, LocalTime fin, EstadoCita estado, Instant creadaEn, Instant canceladaEn)`).

- [ ] **Step 1: Write the failing tests** (`ahora` = domingo 2026-11-08 08:00 salvo que se indique; servicio ventana lunes 09:00–12:00, 60 min): lunes 2026-11-09 → `[09:00,09:30,10:00,10:30,11:00]`; con cita 10:00–11:00 → `[09:00,11:00]`; cita `Cancelada` o de **otro servicio** no bloquea; `excluirCitaId` libera la propia cita; hoy con `ahora` lunes 2026-11-09 10:15 y fecha el mismo lunes omite inicios < 10:15 (`[10:30,11:00]`); **23:30 del día anterior**: con `ahora` 2026-11-08 23:30 y fecha 2026-11-09 el día cuenta como mañana (no como hoy); límite: `2026-12-08` permitido, `2026-12-09` vacío, ayer vacío; inactivo → vacío, y con `excluirCitaId` ≠ null calcula; ventana 09:15–11:15 con 60 min → `[09:15,09:45,10:15]`; ventana 09:00–11:00 con 45 min → `[09:00,09:30,10:00]`; dos ventanas el mismo día se unen sin duplicados y ordenadas; `ValidacionesTest` porta `validaciones.spec.ts` (nombre vacío, duración ≤ 0, valor < 0, fin ≤ inicio, ventanas cruzadas rechazadas y ventanas que se tocan permitidas, correo `a@b.co` válido, `a@b` y `a b@c.co` inválidos, normalización `" Marta "`→`"marta"`).
- [ ] **Step 2: Run** `mvn test -Dtest='HorariosTest,ValidacionesTest'` — Expected: FAIL.
- [ ] **Step 3: Implement** los registros y `Horarios`/`Validaciones` como código puro (sin Spring). La diferencia de días se calcula con `ChronoUnit.DAYS` entre `LocalDate`.
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `feat: reglas puras de horarios libres y validaciones`

### Task 3: Dominio puro — permisos, protecciones y estados de cita

**Files:**
- Create: `dominio/{Permiso,Rol,Usuario}.java`, `dominio/Permisos.java`, `dominio/TransicionesCita.java`
- Test: `dominio/PermisosTest.java`, `dominio/TransicionesCitaTest.java`

**Interfaces:**
- Consumes: `Cita`, `EstadoCita`, `ErrorNegocio`, `CodigoError` (T1–T2).
- Produces: `enum Permiso` con `id()` (el snake_case de Global Constraints) y `Permiso.deId(String)`; `Rol(UUID id, String nombre, String descripcion, Set<Permiso> permisos)`; `Usuario(UUID id, String nombre, String apellido, String correo, String telefono, String usuario, UUID rolId)`; `Permisos.conDependencias(Set<Permiso>): Set<Permiso>`; `Permisos.validarCambioRol(UUID actorRolId, Rol antes, Rol despues): Optional<String>` (el rol del actor no puede perder `gestionar_roles`); `Permisos.puedeEliminarRol(UUID rolId, List<Usuario>)`; `Permisos.puedeAsignarRol(Set<Permiso> actor, Set<Permiso> destino)` (verdadero si el actor tiene `gestionar_roles` o `destino ⊆ actor`); `TransicionesCita.atender(Cita, LocalDateTime ahora): Cita`, `.cancelar(Cita, Instant ahora): Cita`, `.validarMovible(Cita)` — lanzan `ErrorNegocio`.

- [ ] **Step 1: Write the failing tests:** `conDependencias({atender_citas})` incluye `ver_citas`; sin dependencias no cambia; quitar `gestionar_roles` al rol del actor → mensaje; quitarlo a otro rol → vacío; `puedeEliminarRol` falso si hay usuarios con el rol; `puedeAsignarRol`: con `gestionar_roles` cualquier destino, sin él solo subconjuntos; **atender:** `Confirmada` con inicio ≤ ahora ok (borde inicio == ahora ok), futura → `CITA_NO_HA_INICIADO`, `Cancelada`/`Atendida` → `ESTADO_INVALIDO`; **cancelar:** `Confirmada` → `Cancelada` con `canceladaEn`, ya `Cancelada` → devuelve la misma sin error, `Atendida` → `ESTADO_INVALIDO`; **validarMovible:** solo `Confirmada`.
- [ ] **Step 2: Run** `mvn test -Dtest='PermisosTest,TransicionesCitaTest'` — Expected: FAIL.
- [ ] **Step 3: Implement** como código puro; porta `core/permisos.ts` y `permisos.spec.ts`.
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `feat: reglas puras de permisos, protecciones de roles y estados de cita`

### Task 4: Autenticación — usuarios, roles, login, JWT y permisos por petición

**Files:**
- Modify: `pom.xml` (spring-security, soporte JWT)
- Create: `db/migration/V2__roles_usuarios.sql` (tablas `rol`, `rol_permiso`, `usuario`; único en `usuario.usuario`; FK `usuario.rol_id`)
- Create: `infraestructura/persistencia/{RolEntidad,UsuarioEntidad}.java` + repositorios, `infraestructura/seguridad/{ConfiguracionSeguridad,ServicioJwt,PermisosSeguridad}.java`, `aplicacion/AutenticacionServicio.java`, `api/AuthControlador.java`, `infraestructura/AdministradorInicial.java`
- Test: `api/AuthControladorTest.java`, `infraestructura/AdministradorInicialTest.java`

**Interfaces:**
- Consumes: `Permiso`, `Rol`, `Usuario`, `Validaciones.normalizarUsuario`, `Clock`, `PruebaIntegracion`.
- Produces: `POST /api/auth/login {usuario, clave}` → `{token, usuario:{…sin clave}, rol:{id,nombre,descripcion,permisos[]}}`; `GET /api/auth/yo` → igual sin token nuevo; bean `permisos` con `boolean tiene(String... cualquieraDe)` para `@PreAuthorize("@permisos.tiene('ver_citas','gestionar_servicios')")` (lee usuario y rol de la base en cada petición); `UsuarioActual` (id y rol del token); `/api/publico/**`, `/swagger-ui/**` (solo dev) y `/api/auth/login` permitidos sin token; resto exige token. Contraseñas con BCrypt. CORS habilitado para los orígenes de `DATEMANAGER_CORS_ORIGENES` (prueba: una petición con `Origin: http://localhost:4200` recibe `Access-Control-Allow-Origin`; otro origen no). El `JwtDecoder` valida expiración con el `Clock` del contexto.

- [ ] **Step 1: Write the failing tests:** login correcto devuelve token, usuario sin `clave` y permisos del rol; `" MARTA "` entra igual que `marta`; clave mala y usuario inexistente responden **el mismo** 401 `CREDENCIALES_INVALIDAS`; la clave guardada en la base empieza por `$2` (hash); sin token → 401 `NO_AUTENTICADO`; token con `Clock` +61 min → 401; **usuario eliminado tras emitir el token** → 401; **quitarle un permiso al rol** hace que la siguiente petición protegida dé 403 `SIN_PERMISO` sin volver a iniciar sesión; `AdministradorInicial`: sin usuarios y con las variables crea rol Administrador (los 9 permisos) y su usuario; con usuarios existentes no crea nada; sin variables arranca sin crear nada y registra un aviso.
- [ ] **Step 2: Run** `mvn test -Dtest='AuthControladorTest,AdministradorInicialTest'` — Expected: FAIL.
- [ ] **Step 3: Implement** lo anterior. El token solo lleva el id del usuario y la expiración (60 min). Un endpoint de prueba protegido (solo en `src/test`) sirve para las pruebas de 401/403.
- [ ] **Step 4: Run** `mvn test` — Expected: PASS.
- [ ] **Step 5: Commit** `feat: inicio de sesión real con JWT y permisos leídos de la base`

### Task 5: Gestión de roles y usuarios (panel)

**Files:**
- Create: `aplicacion/{RolesServicio,UsuariosServicio}.java`, `api/{RolesControlador,UsuariosControlador}.java`, DTOs
- Test: `api/RolesControladorTest.java`, `api/UsuariosControladorTest.java`

**Interfaces:**
- Consumes: todo T3–T4.
- Produces: `GET /api/panel/roles`, `PUT /api/panel/roles/{id}` (crea si no existe), `DELETE /api/panel/roles/{id}`, `GET /api/panel/permisos` (catálogo `[{id, etiqueta}]`); `GET /api/panel/usuarios`, `PUT /api/panel/usuarios/{id}`, `DELETE /api/panel/usuarios/{id}`. Permisos por ruta según Global Constraints. `PUT` de usuario con `clave` vacía conserva el hash.

- [ ] **Step 1: Write the failing tests:** guardar un rol con `gestionar_citas` devuelve `ver_citas` incluido; el admin que se quita `gestionar_roles` a su propio rol → 409 `CONFLICTO`; borrar un rol con usuarios → 409; borrar a uno mismo → 409; quitar o degradar al **último** usuario con `gestionar_roles` → 409; `PUT` usuario con clave vacía no cambia el hash y con clave nueva sí; **usuario duplicado** `"Marta "` frente a `marta` → 409 y nunca dos filas; correo de usuario inválido → 400 `VALIDACION`; **escalada:** actor con `gestionar_usuarios` sin `gestionar_roles` asigna un rol con permisos de más → 403 `SIN_PERMISO`, uno con subconjunto → 200; ninguna respuesta incluye `clave` ni el hash; sin permiso → 403.
- [ ] **Step 2: Run** `mvn test -Dtest='RolesControladorTest,UsuariosControladorTest'` — Expected: FAIL.
- [ ] **Step 3: Implement** reutilizando `Permisos`; las protecciones viven en `aplicacion` dentro de la transacción.
- [ ] **Step 4: Run** `mvn test` — Expected: PASS.
- [ ] **Step 5: Commit** `feat: roles, permisos y usuarios con protecciones en el servidor`

### Task 6: Servicios y ventanas de disponibilidad (panel)

**Files:**
- Create: `db/migration/V3__servicios.sql` (`servicio`, `ventana_disponibilidad`; `CHECK dia BETWEEN 1 AND 7`, `CHECK hora_fin > hora_inicio`, `CHECK duracion_min > 0`, `CHECK valor >= 0`; FK con borrado en cascada)
- Create: persistencia, `aplicacion/ServiciosServicio.java`, `api/ServiciosControlador.java`
- Test: `api/ServiciosControladorTest.java`

**Interfaces:**
- Consumes: `Servicio`, `Validaciones.errorServicio`, `PermisosSeguridad`.
- Produces: `GET /api/panel/servicios`, `PUT /api/panel/servicios/{id}` (crea si no existe; reemplaza las ventanas por las recibidas); un servicio inactivo se conserva con sus datos. `ServiciosServicio.buscar(UUID): Servicio` para T7.

- [ ] **Step 1: Write the failing tests:** crear con ventanas y leer; editar reemplaza ventanas; error de validación → 400 `VALIDACION` con el mensaje español de `Validaciones`; ventanas cruzadas rechazadas; desactivar conserva el servicio; ver_panel puede leer pero no escribir (403); la base rechaza un día 8 (insertar con `JdbcTemplate` lanza `DataIntegrityViolationException`).
- [ ] **Step 2: Run** `mvn test -Dtest=ServiciosControladorTest` — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `mvn test` — Expected: PASS.
- [ ] **Step 5: Commit** `feat: servicios con ventanas de disponibilidad`

### Task 7: Clientes y citas — reservar, mover, cancelar, atender y concurrencia

**Files:**
- Create: `db/migration/V4__clientes_citas.sql` (`cliente` con correo único normalizado; `cita` con `CHECK hora_fin > hora_inicio`, `CHECK estado IN ('Confirmada','Atendida','Cancelada')`, FK a servicio y cliente, e índice único filtrado `uq_cita_servicio_inicio` sobre `(servicio_id, fecha, hora_inicio) WHERE estado <> 'Cancelada'`)
- Create: persistencia, `aplicacion/{ClientesServicio,CitasServicio}.java`, `api/{ClientesControlador,CitasControlador}.java`
- Test: `api/CitasControladorTest.java`, `api/ClientesControladorTest.java`, `aplicacion/ReservaConcurrenteTest.java`

**Interfaces:**
- Consumes: `Horarios.horariosLibres`, `TransicionesCita`, `ServiciosServicio.buscar`, `Clock`.
- Produces: `CitasServicio.reservar(UUID servicioId, LocalDate fecha, LocalTime inicio, DatosCliente cliente, UUID enlaceId /*nullable*/): Cita` — **en una transacción**: bloquea la fila del servicio (`PESSIMISTIC_WRITE`), carga las citas del servicio ese día, comprueba que `inicio` está en `horariosLibres` (y que el servicio está activo), busca o crea al cliente por correo normalizado actualizando nombre y teléfono, inserta la cita `Confirmada` con `fin = inicio + duración`. Mismos pasos para `mover(id, fecha, inicio)` con `excluirCitaId`. Endpoints: `GET /api/panel/citas?desde=&hasta=&estado=`, `POST /api/panel/citas`, `POST /api/panel/citas/{id}/mover|cancelar|atender`, `GET /api/panel/servicios/{id}/horarios?fecha=&excluirCitaId=`, `GET /api/panel/clientes`, `PUT /api/panel/clientes/{id}`.

- [ ] **Step 1: Write the failing tests:** reservar devuelve 201 `Confirmada` con `fin` correcto; mismo horario otra vez → 409 `HORARIO_NO_DISPONIBLE`; hora pasada, día 31 y servicio inactivo → 409/`HORARIO_NO_DISPONIBLE`; **a las 23:30 hora Bogotá** el día de «hoy» es el de Bogotá (fijar el `Clock` y reservar para hoy 23:00 falla, para mañana 09:00 funciona); mismo horario en **otro servicio** funciona; correo existente (`"ANA@x.co"` vs `"ana@x.co "`) reutiliza el cliente y actualiza nombre/teléfono; correo inválido → 400; mover a un horario libre y a uno ocupado; mover **una cita de un servicio inactivo** funciona; cancelar libera el horario (vuelve a salir en `/horarios`); atender antes de su hora → 409 `CITA_NO_HA_INICIADO`; transiciones inválidas → 409 `ESTADO_INVALIDO`; filtros de `GET citas`; matriz de permisos (`ver_citas` lee, no escribe; `atender_citas` solo atiende); **concurrencia:** 8 hilos reservan el mismo horario → exactamente un 201, el resto 409 y una sola fila; reservar el mismo horario en dos servicios a la vez → ambos 201; el índice filtrado rechaza una segunda cita activa con el mismo inicio insertada directo por SQL.
- [ ] **Step 2: Run** `mvn test -Dtest='CitasControladorTest,ClientesControladorTest,ReservaConcurrenteTest'` — Expected: FAIL.
- [ ] **Step 3: Implement** (la lógica de elegir hueco ya está en `Horarios`; no se duplica en el servicio).
- [ ] **Step 4: Run** `mvn test` — Expected: PASS.
- [ ] **Step 5: Commit** `feat: clientes y citas con reserva atómica`

### Task 8: Enlaces de invitación y zona del cliente

**Files:**
- Create: `db/migration/V5__enlaces.sql` (`enlace_invitacion`: `id`, `codigo` único, `creado_por` FK a usuario, `creado_en`, `vence_en`, `max_usos` NULL o ≥ 1, `usos` ≥ 0, `desactivado_en`; y `cita.enlace_id` FK nullable)
- Create: `dominio/{EstadoEnlace,Enlace}.java`, `dominio/Enlaces.java`, persistencia, `aplicacion/EnlacesServicio.java`, `api/{EnlacesControlador,PublicoControlador}.java`
- Test: `dominio/EnlacesTest.java`, `api/EnlacesControladorTest.java`, `api/PublicoControladorTest.java`, `aplicacion/UsoConcurrenteEnlaceTest.java`

**Interfaces:**
- Consumes: `CitasServicio.reservar(..., enlaceId)`, `Clock`, `PermisosSeguridad`.
- Produces: `Enlaces.estado(Enlace, Instant ahora): EstadoEnlace` con precedencia `Desactivado` > `Vencido` (`ahora >= vence_en`) > `Agotado` (`usos >= maxUsos`) > `Activo`; `Enlaces.nuevoCodigo()` (≥ 128 bits aleatorios con `SecureRandom`, Base64 URL); panel: `POST /api/panel/enlaces {duracionMinutos, maxUsos?}` (60 ≤ duración ≤ 43200, por defecto 1440; `maxUsos` ≥ 1), `GET /api/panel/enlaces`, `POST /api/panel/enlaces/{id}/desactivar` (idempotente); público: `GET /api/publico/invitaciones/{codigo}` (siempre responde `{estado, venceEn}` para un código existente), `GET .../servicios`, `GET .../servicios/{id}/horarios?fecha=`, `POST .../citas`; los tres últimos exigen enlace `Activo` y si no responden el `ENLACE_*` correspondiente. El uso se consume en la **misma transacción** de la reserva con `UPDATE … SET usos = usos + 1 WHERE id = ? AND desactivado_en IS NULL AND vence_en > ? AND (max_usos IS NULL OR usos < max_usos)`; 0 filas → se relee el enlace y se lanza el `ENLACE_*` que toque. Orden de bloqueo fijo: primero el servicio, después el enlace.

- [ ] **Step 1: Write the failing tests:** estados por reloj (`vence_en == ahora` → `Vencido`; `maxUsos 2` con 2 usos → `Agotado`; desactivado gana a todo); crear con duración < 60 o > 43200 y `maxUsos 0` → 400; el código tiene ≥ 22 caracteres y no se repite; público: código inexistente → 404 `ENLACE_NO_ENCONTRADO` (mismo cuerpo siempre), estado de un enlace vencido responde 200 con `Vencido`, listar servicios/horarios/reservar con enlace vencido, agotado o desactivado → 410 con su código; un enlace activo permite el flujo completo y la cita queda ligada a él; **reserva fallida por horario ocupado no gasta un uso**; cancelar la cita no devuelve el uso; **concurrencia:** `maxUsos = 1` y dos reservas simultáneas a horarios distintos → una 201, otra 410 `ENLACE_AGOTADO`, una sola cita; respuesta pública de reservar no incluye datos de otros clientes; panel lista estado, usos, `venceEn`, creador; sin `gestionar_enlaces` → 403; **borrar un usuario que creó enlaces → 409 `CONFLICTO`**.
- [ ] **Step 2: Run** `mvn test -Dtest='EnlacesTest,EnlacesControladorTest,PublicoControladorTest,UsoConcurrenteEnlaceTest'` — Expected: FAIL.
- [ ] **Step 3: Implement.** El panel devuelve además `ruta` lógica `/agendar/{codigo}`; el sitio base lo pone el cliente futuro.
- [ ] **Step 4: Run** `mvn test` — Expected: PASS.
- [ ] **Step 5: Commit** `feat: enlaces de invitación y zona del cliente`

### Task 9: Datos del tablero (inicio del panel)

**Files:**
- Create: `dominio/Metricas.java`, `aplicacion/InicioServicio.java`, `api/InicioControlador.java`
- Test: `dominio/MetricasTest.java`, `api/InicioControladorTest.java`

**Interfaces:**
- Consumes: `Servicio`, `Cita`, `Clock`.
- Produces: `Metricas` con `ocupacion`, `ocupacionServicio`, `ingresosEstimados`, `citasPorDia`, `alertas`, `variacion` (porta `core/metricas.ts`; `variacion` devuelve `OptionalDouble` vacío si el previo es 0); `GET /api/panel/inicio?fecha=` (por defecto hoy en la zona del negocio) con los mismos indicadores que calcula la demo en `features/panel/panel-kpis.ts` y `panel-datos.ts` (citas hoy y atendidas, próxima cita, ocupación de hoy y variación frente al mismo día de la semana pasada, citas nuevas y canceladas en 30 días con el periodo anterior, ingresos estimados, alertas, citas por día de 30 días, ocupación por servicio de la semana y próximas citas). Definiciones en spec Fase 1 §7 «Definiciones del panel»; el constructor lee esos dos archivos para los detalles de cada cifra.

- [ ] **Step 1: Write the failing tests:** `MetricasTest` porta `metricas.spec.ts` (ocupación = minutos reservados no cancelados ÷ minutos de ventanas del día de servicios activos; ingresos = suma de valor de citas no canceladas; alerta solo para servicio **activo sin ninguna ventana**; `variacion(25, 12.5)` ≈ 100 %; previo 0 → vacío); `InicioControladorTest` con datos sembrados en la prueba y `Clock` fijo comprueba cada cifra y que sin `ver_panel` hay 403, y que un día sin citas devuelve ceros sin error.
- [ ] **Step 2: Run** `mvn test -Dtest='MetricasTest,InicioControladorTest'` — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `mvn test` — Expected: PASS.
- [ ] **Step 5: Commit** `feat: datos del tablero del panel`

### Task 10: Datos de ejemplo, README y recorrido completo

**Files:**
- Create: `infraestructura/SemillaDatos.java` (perfil `dev`, solo si la base está vacía), `scripts/crear-bases.sql` (crea `DateManager` y `DateManagerTest`), `src/Date.Manager.Back/README.md`
- Test: `api/RecorridoCompletoTest.java`, `infraestructura/SemillaDatosTest.java`

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: datos de ejemplo equivalentes a `core/seed.ts` de la demo (roles Administrador y Recepción, usuarios `marta` y `andres` con clave de ejemplo falsa `demo123` guardada con hash, los tres servicios de 30/60/90 min con ventanas distintas, clientes y citas repartidas relativas a «ahora»); README con requisitos, cómo crear las bases en SSMS, variables de entorno, cómo arrancar (`mvn spring-boot:run` con perfil `dev`), URL de Swagger UI y cómo correr las pruebas.

- [ ] **Step 1: Write the failing tests:** `SemillaDatosTest`: en una base vacía crea los datos y los servicios sembrados tienen horarios libres; una segunda ejecución no duplica. `RecorridoCompletoTest` (criterio 2 del diseño): login admin → crear enlace de 24 h con 1 uso → estado `Activo` → servicios → horarios → reservar → la cita aparece en `GET /api/panel/citas` → una segunda reserva con el mismo enlace da 410 `ENLACE_AGOTADO` → cancelar la cita → el horario vuelve a salir libre.
- [ ] **Step 2: Run** `mvn test -Dtest='SemillaDatosTest,RecorridoCompletoTest'` — Expected: FAIL.
- [ ] **Step 3: Implement** la semilla (idempotente, solo `dev`) y el README; revisar que el repositorio no contiene secretos (`git grep -n -i -E "password|secret|clave" -- ':!*.md' ':!src/test'` solo muestra nombres de campos).
- [ ] **Step 4: Run** `mvn test` completo — Expected: PASS; arrancar con perfil `dev` contra la base real y abrir Swagger UI.
- [ ] **Step 5: Commit** `feat: datos de ejemplo, README y recorrido completo de la API`

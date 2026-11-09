# Conexión del Angular con la API — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el Angular (`src/Date.Manager.Front`) use la API Java (`src/Date.Manager.Back`) en lugar de la demo en memoria: login real, panel completo, pantalla de enlaces y zona del cliente en `/agendar/<código>`.

**Architecture:** Se conserva la clase abstracta `AgendaRepo` como punto de unión de las pantallas y se implementa `HttpAgendaRepo` con `HttpClient`; la demo (repositorio en memoria y semilla) sale del código de producción y queda solo como doble de pruebas en `src/app/testing/`. Servicios aparte para lo que no es el repositorio del panel: `AuthApi`, `InicioApi`, `EnlacesApi`, `PublicoApi`. Dos interceptores: uno pone el `Bearer` solo en rutas del panel y `GET /auth/yo`, otro traduce `{codigo, mensaje, campos?}` a `ApiError`.

**Tech Stack:** Angular 21 (señales, componentes standalone, `HttpClient`), Vitest vía `ng test`, `HttpTestingController`; API en Spring Boot (solo un cambio mínimo, Task 4).

**Spec:** `docs/superpowers/specs/2026-11-08-date-manager-conexion-angular-design.md` (vinculante; léelo completo, sobre todo §4, §5, §6 y §10).

## Global Constraints

- Trabajar en `src/Date.Manager.Front`. Pruebas: `npx ng test --watch=false`; compilación: `npx ng build`. Ninguna advertencia nueva.
- API base por defecto `http://localhost:8080/api`, definida en UN solo lugar (token de inyección `API_URL`). CORS de la API ya permite `http://localhost:4200`.
- Rutas con hash (`withHashLocation`). `/reservar` NO debe existir. La raíz `/` redirige a `/login`. La ruta pública es `/agendar/:codigo` y no pasa por `conSesion`.
- El `Bearer` se envía **solo** a `/api/panel/**` y `GET /api/auth/yo`. Nunca a `/api/publico/**` ni a `POST /api/auth/login`, ni siquiera con sesión abierta en la misma pestaña.
- Token en `sessionStorage`, clave `datemanager.sesion.v2` (contenido `{token}`); la contraseña nunca se guarda. Vence a los 60 min.
- Errores de la API: `{codigo, mensaje, campos?}`. Códigos y HTTP: 400 `VALIDACION`, 401 `NO_AUTENTICADO`/`CREDENCIALES_INVALIDAS`, 403 `SIN_PERMISO`, 404 `NO_ENCONTRADO`/`ENLACE_NO_ENCONTRADO`, 409 `HORARIO_NO_DISPONIBLE`/`ESTADO_INVALIDO`/`CITA_NO_HA_INICIADO`/`CONFLICTO`, 410 `ENLACE_VENCIDO`/`ENLACE_AGOTADO`/`ENLACE_DESACTIVADO`, 500 `ERROR_INTERNO`. El texto que ve el usuario es el `mensaje` de la API (ya en español).
- Claves nulas se **omiten** en las respuestas (`canceladaEn`, `maxUsos`, `proxima`, `variacion`, `minutosHasta`, `token` en `/auth/yo`): tratarlas como opcionales.
- Nueve permisos (snake_case): `ver_panel, ver_citas, gestionar_citas, atender_citas, gestionar_servicios, gestionar_clientes, gestionar_usuarios, gestionar_roles, gestionar_enlaces`.
- Ids de servicios, ventanas, roles, usuarios y clientes creados en pantalla: UUID (`crypto.randomUUID()`); la API exige UUID en `PUT /…/{id}`.
- Fechas `YYYY-MM-DD`, horas `HH:mm` (hora del negocio, sin zona), instantes ISO UTC, importes como número JSON.
- Rutas de la API: `/api/auth/login` (POST `{usuario,clave}` → `{token, usuario, rol}`), `/api/auth/yo` (GET → `{usuario, rol}`), `/api/panel/{inicio[?fecha=], servicios, servicios/{id}/horarios?fecha=&excluirCitaId=, clientes, citas[?desde&hasta&estado], citas/{id}/{mover|cancelar|atender}, roles, permisos, usuarios, enlaces, enlaces/{id}/desactivar}`, `/api/publico/invitaciones/{codigo}` y `…/{codigo}/{servicios, servicios/{id}/horarios?fecha=, citas}`. Contrato completo en `src/Date.Manager.Back/README.md` y Swagger.
- Respuestas: `Usuario{id,nombre,apellido,correo,telefono,usuario,rolId}` (sin clave); `Rol{id,nombre,descripcion,permisos[]}`; `PUT usuarios/{id}` acepta `clave` opcional (vacía/ausente = conservar); `PUT roles/{id}` body `{nombre,descripcion,permisos[]}`; `PUT servicios/{id}` body `{nombre,duracionMin,activo,valor,ventanas:[{id,dia,inicio,fin}]}`; `PUT clientes/{id}` body `{nombre,apellido,telefono,correo}`; `POST citas` body `{servicioId,fecha,inicio,cliente:{nombre,apellido,telefono,correo}}`; `POST citas/{id}/mover` body `{fecha,inicio}`; `POST enlaces` body `{duracionMinutos?,maxUsos?}` (60–43200, por defecto 1440; `maxUsos` ≥ 1 u omitido); `Enlace{id,codigo,ruta,estado(Activo|Vencido|Agotado|Desactivado),usos,maxUsos?,venceEn,creadoEn,creadoPor,creadoPorNombre}`; público: estado `{estado, venceEn}`, reserva `{servicioId, servicio, fecha, inicio, fin, estado}`.
- Código de la demo no se reescribe sin necesidad: los componentes siguen usando `AgendaRepo` y `SesionService`.
- Comentarios y textos en español; commits en español con el trailer `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

## Review Focus

Condiciones que el diseño implica pero ninguna tarea prueba por sí sola; cada una tiene su prueba en la tarea indicada:
1. **Token vencido a mitad de una escritura** (guardar, cancelar): vuelve a `/login` con aviso, una sola vez, sin ciclo y sin dejar la pantalla en «guardando» (Task 1 y Task 2).
2. **Doble clic en Reservar / Guardar / Crear enlace**: una sola petición mientras hay una en curso (Task 6, Task 7 y las pantallas existentes que ya deshabilitan el botón, verificadas en Task 8).
3. **Enlace que deja de servir a mitad del flujo** (agotado o vencido entre elegir hora y enviar): el `POST` da 410 y el cliente ve el mensaje del estado, no un formulario atascado (Task 7).
4. **`Bearer` jamás llega a `/api/publico/**`** aunque haya sesión del negocio abierta en la misma pestaña (Task 1 y Task 7).
5. **Pantalla accesible con un dato auxiliar sin permiso** (clientes necesita citas; panel necesita clientes; usuarios necesita usuarios y roles): aviso en esa sección, el resto funciona (Task 8).

---

### Task 1: Núcleo HTTP — configuración, errores tipados, interceptores y permiso nuevo

**Files:**
- Create: `src/app/core/config.ts`, `src/app/core/api-error.ts`, `src/app/core/interceptores.ts`
- Modify: `src/app/app.config.ts` (agregar `provideHttpClient(withInterceptors([...]))`, sin quitar aún `DemoAgendaRepo`), `src/app/core/models.ts` (`Permiso` incluye `'gestionar_enlaces'`), `src/app/core/permisos.ts` (catálogo y `MENU`)
- Test: `src/app/core/interceptores.spec.ts`, `src/app/core/api-error.spec.ts`, ajustes en `permisos.spec.ts`

**Interfaces:**
- Produces: `API_URL: InjectionToken<string>` (por defecto `http://localhost:8080/api`); `class ApiError extends Error { status: number; codigo: string; campos: Record<string,string>; static desde(e: HttpErrorResponse): ApiError }` (si el cuerpo no es JSON de la API, `codigo = 'ERROR_RED'` o `'ERROR_INTERNO'` y un mensaje en español que explique que no se pudo contactar al servidor); `authInterceptor`, `errorInterceptor` (funciones `HttpInterceptorFn`); `SESION_EXPIRADA: InjectionToken<() => void>` o un `Subject`/señal equivalente que el interceptor dispara al recibir 401 en una ruta que llevó `Bearer` (la sesión lo escucha en Task 2; aquí solo se emite y se prueba que se emite UNA vez por ráfaga de respuestas 401). El `Bearer` se lee de un `TokenStore` mínimo (`core/token-store.ts`: `leer(): string|null`, `guardar(t)`, `borrar()` sobre `sessionStorage` con try/catch, clave `datemanager.sesion.v2`).

- [ ] **Step 1: Escribir las pruebas que fallan** (`HttpTestingController`): el interceptor añade `Authorization: Bearer abc` a `GET {API_URL}/panel/servicios` y a `GET {API_URL}/auth/yo`; **no** lo añade a `GET {API_URL}/publico/invitaciones/X`, ni a `POST {API_URL}/auth/login`, ni a una URL ajena aunque el token exista; sin token no pone cabecera. `ApiError.desde` convierte `{codigo:'HORARIO_NO_DISPONIBLE', mensaje:'…'}` con status 409, un 400 con `campos`, y un cuerpo no JSON / status 0. Un 401 en una ruta con `Bearer` emite el evento de sesión expirada una sola vez aunque lleguen tres 401 seguidos; un 401 en `POST /auth/login` (credenciales inválidas) NO lo emite y llega como `ApiError` al llamador. `permisos.spec`: el catálogo tiene 9 permisos con etiqueta «Gestionar enlaces de invitación» y `MENU` incluye `{ruta:'/enlaces', etiqueta:'Enlaces', permisos:['gestionar_enlaces']}`; `primeraRuta` de un rol que solo tiene `gestionar_enlaces` es `/enlaces`.
- [ ] **Step 2: Ejecutar** `npx ng test --watch=false` — Expected: FAIL (símbolos inexistentes).
- [ ] **Step 3: Implementar** los archivos listados. `errorInterceptor` envuelve todo `HttpErrorResponse` en `ApiError` (`throwError`). `authInterceptor` decide por URL con una función pura exportada `llevaToken(url, metodo)`.
- [ ] **Step 4: Ejecutar** `npx ng test --watch=false` y `npx ng build` — Expected: PASS, sin advertencias nuevas.
- [ ] **Step 5: Commit** `feat: núcleo HTTP del cliente (errores tipados, token y permiso de enlaces)`

### Task 2: Sesión real y pantalla de login

**Files:**
- Create: `src/app/core/auth-api.ts`
- Modify: `src/app/core/sesion.ts`, `src/app/core/guards.ts`, `src/app/core/models.ts` (`Usuario` sin `clave`; el alta de usuarios usa `UsuarioForm = Usuario & { clave?: string }` en la pantalla), `src/app/features/login/login.ts` (+ su plantilla), `src/app/layout/panel-shell.ts` (+ plantilla: se quita «Reiniciar demo» y su confirmación)
- Test: `src/app/core/sesion.spec.ts`, `src/app/core/guards.spec.ts`, `src/app/features/login/login.spec.ts`, `src/app/layout/panel-shell.spec.ts`

**Interfaces:**
- Consumes: `TokenStore`, `ApiError`, evento de sesión expirada (Task 1), `API_URL`.
- Produces: `AuthApi { login(usuario: string, clave: string): Promise<{usuario: Usuario; rol: Rol}> /* guarda el token en TokenStore */; yo(): Promise<{usuario: Usuario; rol: Rol}> }`. `SesionService` mantiene `usuario`, `rol` (señales), `listo: Promise<void>` (al cargar: si hay token, llama `AuthApi.yo()`; si falla con 401 limpia y queda sin sesión), `iniciar(usuario, rol)`, `refrescar()` (usa `yo`, que lee rol y permisos vigentes; si 401 cierra), `cerrar()`, `puede(p)`, y una señal `aviso: Signal<string | null>` con el motivo de un cierre forzado («Tu sesión venció. Inicia sesión de nuevo.» o «Tu usuario ya no existe.»). Al emitirse la expiración, la sesión se cierra y el `Router` navega a `/login` (una vez). `sinSesion` y `conSesion` esperan `listo`. `conPermiso` conserva su conducta: sin permiso manda a `primeraRuta(rol)`, y a `/sin-acceso` solo si el rol no tiene ruta.

- [ ] **Step 1: Escribir las pruebas que fallan:** con token guardado y `yo` OK, `listo` se resuelve con usuario y rol cargados y el token se conserva (la respuesta de `yo` no trae token); con token y `yo` → 401, queda sin sesión y el token se borra; `login` correcto guarda token y deja la sesión iniciada; `login` con `CREDENCIALES_INVALIDAS` rechaza con `ApiError` y no guarda nada; la clave no se escribe en `sessionStorage` (inspeccionar el valor guardado); `cerrar` borra el token; un 401 a mitad de una llamada del panel cierra la sesión, navega a `/login` una sola vez y `aviso()` explica el motivo; `login.spec`: muestra el mensaje de la API en credenciales inválidas, deshabilita el botón mientras hay una petición, NO hay botones de acceso rápido (`marta`/`andres`), y tras entrar navega a `primeraRuta`; `panel-shell.spec`: ya no existe el botón «Reiniciar demo».
- [ ] **Step 2: Ejecutar** `npx ng test --watch=false` — Expected: FAIL.
- [ ] **Step 3: Implementar.** Quitar de `login.ts` los atajos y el uso de `AgendaRepo.iniciarSesion`; quitar de `panel-shell` todo lo de reinicio y la inyección de `AgendaRepo`. La pantalla de login muestra `sesion.aviso()` si existe.
- [ ] **Step 4: Ejecutar** `npx ng test --watch=false` — Expected: PASS (las pruebas de otras pantallas que dependan de `Usuario.clave` o de `AgendaRepo.iniciarSesion` se ajustan aquí o se difieren a Task 3 si su doble de pruebas aún no cambió; documenta cuáles).
- [ ] **Step 5: Commit** `feat: inicio de sesión real con token y restauración por /auth/yo`

### Task 3: `HttpAgendaRepo`, interfaz ajustada y doble de pruebas

**Files:**
- Create: `src/app/core/http-agenda-repo.ts`, `src/app/testing/demo-agenda-repo.ts` y `src/app/testing/seed.ts` (movidos con `git mv` desde `core/`), `src/app/core/ids.ts` (`nuevoId(): string` con `crypto.randomUUID()`)
- Modify: `src/app/core/agenda-repo.ts`, `src/app/app.config.ts` (provee `HttpAgendaRepo`), todos los `*.pruebas.ts` y specs que importaban `core/demo-agenda-repo` o `core/seed` (apuntan a `testing/`), `src/app/features/servicios/servicios-borrador.ts` y las secciones de usuarios/roles (usan `nuevoId()`; ya no hay prefijos)
- Test: `src/app/core/http-agenda-repo.spec.ts`, `src/app/core/ids.spec.ts`; los specs movidos de la demo (`demo-agenda-repo.spec.ts`) pasan a `src/app/testing/`

**Interfaces:**
- Consumes: `API_URL`, `HttpClient`, `ApiError`.
- Produces: `AgendaRepo` ajustada: se quitan `iniciarSesion` y `reiniciar`; `guardarRol(rol: Rol): Promise<Rol>` (sin `actorId`); `guardarUsuario(u: UsuarioForm): Promise<Usuario>` (envía `clave` solo si trae texto); el resto de métodos conserva su firma (`servicios`, `guardarServicio`, `horariosLibres(servicioId, fecha, excluirCitaId?)`, `reservar({servicioId,fecha,inicio,cliente})`, `citas`, `cancelarCita`, `moverCita`, `marcarAtendida`, `clientes`, `guardarCliente`, `roles`, `eliminarRol`, `usuarios`, `eliminarUsuario`) y `cambios: Signal<number>` sube tras cada escritura exitosa. `HttpAgendaRepo` traduce cada método a su ruta (§Global Constraints). `permisos` del rol llegan como `string[]` y se tipan `Permiso[]`.

- [ ] **Step 1: Escribir las pruebas que fallan** (`HttpTestingController`): una prueba por método con URL, verbo y cuerpo exactos (p. ej. `guardarServicio` hace `PUT /panel/servicios/{id}` con `ventanas` completas; `horariosLibres` con `excluirCitaId` agrega el parámetro; `guardarUsuario` sin clave no envía `clave`; `reservar` anida `cliente`); `cambios()` sube tras una escritura y no tras un fallo; un 409 `HORARIO_NO_DISPONIBLE` llega como `ApiError` con ese código; `nuevoId()` produce un UUID v4 válido y distinto cada vez.
- [ ] **Step 2: Ejecutar** `npx ng test --watch=false` — Expected: FAIL.
- [ ] **Step 3: Implementar.** Mover la demo y la semilla a `src/app/testing/` adaptando solo lo necesario para que el doble cumpla la interfaz nueva (sin `reiniciar`/`iniciarSesion`; `guardarRol` sin actor: la protección «no quitarte gestionar_roles» que antes recibía el actor se queda del lado del servidor y de `validarCambioRol` en la pantalla). Cambiar el proveedor en `app.config.ts` a `HttpAgendaRepo`. Que el código de producción no importe nada de `testing/` (comprobar con `git grep -n "testing/" -- src/app ':!*.spec.ts' ':!*.pruebas.ts'`).
- [ ] **Step 4: Ejecutar** `npx ng test --watch=false` y `npx ng build` — Expected: PASS; el build no incluye la demo.
- [ ] **Step 5: Commit** `feat: repositorio HTTP contra la API y la demo pasa a ser doble de pruebas`

### Task 4: API — teléfono del cliente en las próximas citas del tablero

**Files:**
- Modify (en `src/Date.Manager.Back`): `src/main/java/com/datemanager/api/InicioDto.java` (`ProximaCitaDto` suma `clienteTelefono`), `src/main/java/com/datemanager/aplicacion/Tablero.java` y `InicioServicio.java` (`ProximaCita` lleva el teléfono, leído con la misma consulta de clientes ya existente), `README.md` (la tabla de campos si la menciona)
- Test: `src/test/java/com/datemanager/api/InicioControladorTest.java` (añadir/ajustar), y los tests de dominio/aplicación que construyan `ProximaCita`

**Interfaces:**
- Produces: cada elemento de `proxima` y de `proximas` en `GET /api/panel/inicio` incluye `clienteTelefono: string`. Nada más cambia en el contrato.

- [ ] **Step 1: Escribir la prueba que falla:** con una cita futura Confirmada de un cliente con teléfono conocido, `$.proxima.clienteTelefono` y `$.proximas[0].clienteTelefono` valen ese teléfono.
- [ ] **Step 2: Ejecutar** `mvn test -Dtest=InicioControladorTest` (Maven con JDK 21 y las variables `DATEMANAGER_*` de `.env.local`; ver `src/Date.Manager.Back/README.md` §2) — Expected: FAIL.
- [ ] **Step 3: Implementar** el campo sin consultas adicionales (el servicio ya carga los clientes con `findAllById`).
- [ ] **Step 4: Ejecutar** la suite completa `mvn test` — Expected: PASS (403+ pruebas, 0 fallos); el conteo de consultas de `InicioConsultasTest` no cambia.
- [ ] **Step 5: Commit** `feat: teléfono del cliente en las próximas citas del tablero`

### Task 5: Tablero desde la API

**Files:**
- Create: `src/app/core/inicio-api.ts`, `src/app/core/inicio.ts` (tipos `Inicio`, `ProximaCita`, `Alerta`, `Comparacion`)
- Modify: `src/app/features/panel/panel.ts` (+ plantilla): consume `InicioApi`; formato de porcentajes, moneda y textos de alertas se queda en el Angular
- Delete: `src/app/core/metricas.ts` y `metricas.spec.ts`, `src/app/features/panel/panel-kpis.ts`, `panel-datos.ts` y los specs que solo ellos usen (si algo de ahí sigue siendo necesario para presentación, se reubica con su prueba; documenta qué)
- Test: `src/app/core/inicio-api.spec.ts`, `src/app/features/panel/panel.spec.ts` (y `panel-carga.spec.ts` ajustado), `panel.pruebas.ts` (el helper provee un `InicioApi` falso con datos fijos)

**Interfaces:**
- Consumes: `GET /api/panel/inicio[?fecha=YYYY-MM-DD]` → `{fecha, citasHoy{total,atendidas}, proxima?, ocupacionHoy{actual,anterior,variacion?}, citasNuevas{…}, citasCanceladas{…}, ingresos{…}, alertas[{tipo,titulo,detalle,ref}], alertasExtra, citasPorDia[{fecha,total}], ocupacionSemana[{servicioId,nombre,ocupacion}], proximas[{id,servicioId,servicioNombre,clienteId,clienteNombre,clienteApellido,clienteTelefono,fecha,inicio,fin,esHoy,minutosHasta?}]}`. Las fracciones (ocupación, variación) van de 0 a 1.
- Produces: `InicioApi.inicio(fecha?: string): Promise<Inicio>` (sin `fecha` no manda el parámetro). El panel NO envía `fecha` cuando muestra el día actual.

- [ ] **Step 1: Escribir las pruebas que fallan:** `InicioApi` llama a la URL con y sin `fecha`; el panel con un `Inicio` fijo muestra cada cifra (citas hoy y atendidas, próxima cita, ocupación en %, variaciones con signo, ingresos con formato de moneda, citas nuevas y canceladas, alertas con su texto y «+N más» si `alertasExtra > 0`, gráfica de 30 días, ocupación por servicio de la semana, tabla de próximas con teléfono y SIN columna Estado); una variación ausente se muestra como «—» sin error; un día sin citas (todo en cero, listas vacías) renderiza sin error; un 403 del tablero muestra el aviso de «sin permiso» y no rompe.
- [ ] **Step 2: Ejecutar** `npx ng test --watch=false` — Expected: FAIL.
- [ ] **Step 3: Implementar** y borrar el cálculo duplicado. `grep -rn "metricas\|panel-kpis\|panel-datos" src/app` no debe encontrar nada al terminar.
- [ ] **Step 4: Ejecutar** `npx ng test --watch=false` y `npx ng build` — Expected: PASS.
- [ ] **Step 5: Commit** `feat: el tablero consume los indicadores de la API`

### Task 6: Pantalla de enlaces

**Files:**
- Create: `src/app/core/enlaces-api.ts`, `src/app/features/enlaces/enlaces.ts` (+ `.html`, `.css`)
- Modify: `src/app/app.routes.ts` (ruta `enlaces` hija del `PanelShell`, `canActivate: [conPermiso]`, `data: { permisos: ['gestionar_enlaces'] }`)
- Test: `src/app/core/enlaces-api.spec.ts`, `src/app/features/enlaces/enlaces.spec.ts`

**Interfaces:**
- Produces: `Enlace` (tipo, campos del contrato; `maxUsos?`), `EnlacesApi { listar(): Promise<Enlace[]>; crear(p: {duracionMinutos?: number; maxUsos?: number}): Promise<Enlace>; desactivar(id: string): Promise<Enlace> }`. La pantalla arma la URL completa a copiar como `${location.origin}${location.pathname}#${enlace.ruta}` (ruta lógica `/agendar/<código>`).

- [ ] **Step 1: Escribir las pruebas que fallan:** la API llama a `GET/POST /panel/enlaces` y `POST /panel/enlaces/{id}/desactivar`; la pantalla lista enlaces con estado, usos/máximo, vencimiento (en hora local legible) y creador, más nuevos primero (el orden lo trae la API; la pantalla lo respeta); el formulario valida duración (60 a 43200 minutos; por defecto 1440) y máximo de usos (entero ≥ 1 o vacío = sin límite) **antes** de enviar y muestra el `mensaje` de un 400; crear muestra la URL completa con botón «Copiar» (portapapeles; si no está disponible muestra el texto seleccionable); **doble clic en «Crear enlace» envía una sola petición**; desactivar pide confirmación en la propia pantalla y refresca la lista; un enlace `Desactivado` no ofrece desactivar; sin `gestionar_enlaces` la ruta manda a la primera pantalla permitida; el menú muestra «Enlaces» solo con el permiso.
- [ ] **Step 2: Ejecutar** `npx ng test --watch=false` — Expected: FAIL.
- [ ] **Step 3: Implementar**, con el estilo visual del resto de pantallas del panel (reutiliza clases y patrones de `servicios` o `usuarios`).
- [ ] **Step 4: Ejecutar** `npx ng test --watch=false` y `npx ng build` — Expected: PASS.
- [ ] **Step 5: Commit** `feat: pantalla de enlaces de invitación`

### Task 7: Zona del cliente en `/agendar/:codigo` y fin de `/reservar`

**Files:**
- Create: `src/app/core/publico-api.ts`, `src/app/features/agendar/agendar.ts` (+ plantilla y estilos; nace de mover/renombrar `features/reservar/reservar.*` con `git mv` para conservar el historial)
- Modify: `src/app/app.routes.ts` (se quita `reservar`; se agrega `agendar/:codigo` pública sin guardias; `''` redirige a `login`; `**` redirige a `login`)
- Delete: `src/app/features/reservar/` (lo que no se haya movido)
- Test: `src/app/core/publico-api.spec.ts`, `src/app/features/agendar/agendar.spec.ts`, `src/app/app.routes.spec.ts` (o el spec de rutas existente)

**Interfaces:**
- Produces: `PublicoApi { estado(codigo): Promise<{estado: 'Activo'|'Vencido'|'Agotado'|'Desactivado'; venceEn: string}>; servicios(codigo): Promise<Servicio[]>; horarios(codigo, servicioId, fecha): Promise<string[]>; reservar(codigo, p: {servicioId; fecha; inicio; cliente: DatosCliente}): Promise<CitaPublica> }` con `CitaPublica = {servicioId, servicio, fecha, inicio, fin, estado}`. La pantalla recibe `codigo` de la ruta.

- [ ] **Step 1: Escribir las pruebas que fallan:** enlace `Activo` → carga servicios activos y sigue el flujo (servicio → fecha → horario libre → datos → confirmación con servicio, fecha, inicio y fin de la respuesta); `Vencido`, `Agotado` y `Desactivado` muestran cada uno su mensaje, sin formulario ni llamadas a servicios; código inexistente (404 `ENLACE_NO_ENCONTRADO`) muestra «enlace no encontrado»; **ninguna llamada de la zona pública lleva `Authorization`, incluso con `SesionService` con sesión iniciada** (pasar por los interceptores reales con `HttpTestingController`); **doble clic en «Reservar» envía una sola petición**; si el `POST` da 410 (`ENLACE_AGOTADO` o `ENLACE_VENCIDO`) a mitad del flujo, se muestra el mensaje del estado y se cierra el formulario; si da 409 `HORARIO_NO_DISPONIBLE`, se avisa, se recargan los horarios de la fecha y se conserva lo escrito; errores 400 con `campos` (`cliente.correo`, `correo`, etc.) se muestran junto al campo y, sin `campos`, como error general; en `app.routes` ya no existe `reservar`, `''` y `**` llevan a `login`, y `agendar/:codigo` no pasa por `conSesion`.
- [ ] **Step 2: Ejecutar** `npx ng test --watch=false` — Expected: FAIL.
- [ ] **Step 3: Implementar.** Reutilizar tal cual la lógica de pasos, validaciones (`core/validaciones`), `core/horarios` (para ordenar/mostrar) y fechas del flujo anterior; sustituir las llamadas al repositorio por `PublicoApi`. La fecha mínima/máxima se calcula con el reloj del navegador (la API valida de todos modos).
- [ ] **Step 4: Ejecutar** `npx ng test --watch=false` y `npx ng build` — Expected: PASS; `git grep -n "reservar" -- src/app/app.routes.ts` no encuentra la ruta.
- [ ] **Step 5: Commit** `feat: zona del cliente en /agendar/:codigo y se elimina /reservar`

### Task 8: Permisos auxiliares, errores de campo y estados de carga en las pantallas del panel

**Files:**
- Create: `src/app/shared/errores.ts` (utilidades: `mensajeDe(e: unknown): string`, `camposDe(e: unknown): Record<string,string>`, `esSinPermiso(e: unknown): boolean`)
- Modify: `features/clientes/clientes.ts`, `shared/agenda-dia.ts`, `features/panel/panel.ts` (sección de agenda del día), `features/usuarios/usuarios.ts`, `usuarios-seccion.ts`, `roles-seccion.ts`, `features/servicios/servicios.ts`, `features/citas/citas.ts`, `mover-cita.ts` (+ plantillas)
- Test: specs de cada una más `shared/errores.spec.ts`

**Interfaces:**
- Consumes: `ApiError`.
- Produces: en pantallas con datos auxiliares (clientes ← citas; agenda del día y panel ← clientes; usuarios y roles ← usuarios y roles), un 403 en la llamada **secundaria** deja la pantalla funcionando con lo que sí puede ver y muestra en esa sección el aviso «No tienes permiso para ver esta información.»; un 403 en la llamada **principal** de la ruta manda a `/sin-acceso`. Los formularios muestran errores de campo (`campos`) junto al campo con normalización de claves (`cliente.correo` y `correo` apuntan al campo `correo`), y el `mensaje` como error general cuando no hay `campos`. Todo botón de guardar/cancelar/atender/mover queda deshabilitado mientras hay una petición en curso. Un 409 `CONFLICTO`/`ESTADO_INVALIDO`/`CITA_NO_HA_INICIADO`/`HORARIO_NO_DISPONIBLE` se muestra con su `mensaje`, y la pantalla recarga sus datos.

- [ ] **Step 1: Escribir las pruebas que fallan:** por pantalla, con el repositorio HTTP real y `HttpTestingController`: (a) clientes con 403 en `GET /panel/citas` sigue mostrando la lista de clientes y el aviso en la sección de historial; (b) panel de un rol con solo `ver_panel` con 403 en `GET /panel/clientes` muestra el tablero y la agenda sin nombres de cliente más el aviso; (c) usuarios con solo `gestionar_roles` y 403 en `GET /panel/usuarios` muestra la sección de roles y avisa en la de usuarios; (d) 400 con `campos: {correo: …}` y con `campos: {'cliente.correo': …}` marcan el campo correo; 400 sin `campos` muestra el mensaje general; (e) un 401 durante guardar lleva a `/login` con aviso, una vez, y el botón no queda en «guardando» (Review Focus 1); (f) doble clic en guardar envía una sola petición en servicios, clientes, usuarios y roles.
- [ ] **Step 2: Ejecutar** `npx ng test --watch=false` — Expected: FAIL.
- [ ] **Step 3: Implementar** con la utilidad compartida; no duplicar la lógica en cada pantalla.
- [ ] **Step 4: Ejecutar** `npx ng test --watch=false` y `npx ng build` — Expected: PASS.
- [ ] **Step 5: Commit** `feat: avisos por permisos auxiliares y errores de campo de la API en las pantallas`

### Task 9: Limpieza de la demo, documentación y verificación de punta a punta

**Files:**
- Modify: `src/Date.Manager.Front/README.md` (cómo arrancar contra la API, variable `API_URL`, usuarios de ejemplo de la API, que la demo está en la rama `demo-sin-servidor`), `src/Date.Manager.Back/README.md` (quitar la frase de «conectar Angular más adelante» si existe)
- Delete: restos de la demo en producción (`localStorage` de datos, textos «demo»/«Reiniciar» en plantillas) que `git grep` encuentre
- Test: ninguna nueva salvo lo que la limpieza exija; la verificación es manual contra la API real

- [ ] **Step 1: Buscar restos:** `git grep -n -i -E "DemoAgendaRepo|sembrar|seed|reiniciar|localStorage|datemanager\.demo|/reservar" -- src/Date.Manager.Front/src ':!*.spec.ts' ':!*.pruebas.ts' ':!src/app/testing/*'` — Expected: sin resultados que sean código de producción (documenta cada coincidencia que dejes y por qué).
- [ ] **Step 2: Suites completas:** `npx ng test --watch=false`, `npx ng build` (Angular) y `mvn test` (API) — Expected: todo en verde, sin advertencias nuevas.
- [ ] **Step 3: Recorrido manual contra la API real** (criterios de aceptación del diseño §7): arrancar la API con el perfil `dev` (README del backend §3; los datos de ejemplo `marta`/`demo123` y `andres`/`demo123` ya están en la base `DateManager` o se siembran si está vacía) y el Angular con `npx ng serve`; con el navegador integrado verificar: (1) login `marta`, tablero con cifras reales, crear enlace de 24 h con 1 uso, copiar la ruta, abrirla en otra pestaña **sin sesión** (ventana nueva sin `sessionStorage`), reservar, ver la cita en el panel y en la agenda, cancelarla y confirmar que el horario vuelve a salir libre; (2) segunda reserva con el mismo enlace de 1 uso → mensaje de enlace agotado; (3) `andres`: sin Servicios, Usuarios ni Enlaces en el menú, URL directa lo manda a su primera pantalla permitida; (4) token inválido (editar `sessionStorage`) → `/login` con aviso, sin ciclo; (5) `/#/reservar` ya no existe; (6) enlace vencido/agotado/desactivado/inexistente muestran su mensaje. Anotar el resultado de cada punto (pasó / no pasó con captura o texto). Detener los procesos que arrancaste y dejar libres los puertos 8080 y 4200.
- [ ] **Step 4: Corregir** cualquier desajuste del contrato que aparezca con el cambio mínimo en el lado correspondiente y su prueba (diseño §9); documentar cada uno.
- [ ] **Step 5: Commit** `docs: README del frontend conectado a la API y limpieza de restos de la demo`

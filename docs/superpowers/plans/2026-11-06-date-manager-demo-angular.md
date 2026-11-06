# DateManager — Demo en Angular (Fase 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir la demo funcional de DateManager en Angular: página pública de reserva sin cuenta y panel de administración con roles y permisos configurables, todo en el navegador con datos de ejemplo.

**Architecture:** Lógica de negocio pura y probada (`core/horarios`, `core/permisos`, `core/metricas`) separada de los componentes. Una capa de datos `AgendaRepo` (clase abstracta con métodos `Promise`) con una implementación `DemoAgendaRepo` en memoria + `localStorage`; en la Fase 2 se sustituye por una implementación que llame a la API. Pantallas standalone con signals que solo hablan con `AgendaRepo` y con las funciones puras.

**Tech Stack:** Angular 21, Chart.js, Vitest (vía `ng test`), CSS con variables, rutas con hash.

**Spec:** `docs/superpowers/specs/2026-11-06-date-manager-demo-angular-design.md` (maquetas en `docs/superpowers/specs/mockups/`). Léelo antes de empezar: este plan lo cita por sección.

## Global Constraints

- Proyecto en `src/Date.Manager.Front` (nombre de paquete `date-manager`), junto a `src/Date.Manager.Back`. Node 20.19 o superior, npm.
- Angular 21, componentes standalone, signals, `ChangeDetectionStrategy.OnPush`. Chart.js para gráficas. Vitest para pruebas (`npx ng test --watch=false`).
- Rutas con hash (`withHashLocation()`), `<base href="./">`. Sin backend, sin autenticación real.
- Interfaz en español, oración normal, verbos claros; una acción se llama igual en todo el flujo. Genérico: ningún texto, ícono ni logo de un rubro concreto.
- Colores de partida: fondo `#E9EEF2`, tinta `#101C33`, cobalto `#2B50E6`, apoyo `#51607A`, rayado `#C9D2DC`, ámbar `#D68A00`, rojo `#C0392B`. Bordes rectos, líneas de 1,5 px, sin sombras decorativas.
- Foco de teclado visible, contraste suficiente, respetar `prefers-reduced-motion`. El cliente usa la página sobre todo en celular (probar a 375 px).
- Horarios: horas locales del navegador, sin zonas horarias; inicios cada **30 min**; la cita cabe entera en una ventana; ventanas no cruzan medianoche; no se reservan horas pasadas; hasta **30 días** de anticipación.
- Cada servicio tiene agenda **independiente**. La cita nace **Confirmada**. Estados: `Confirmada`, `Atendida`, `Cancelada`.
- Valores de ejemplo en pesos colombianos; tres servicios genéricos de 30, 60 y 90 min.
- Cuentas y claves de ejemplo; nunca contraseñas reales.

## Review Focus

- Una cita que termina exactamente cuando empieza otra no es cruce (se pueden reservar horas contiguas).
- Una ventana más corta que la duración del servicio no ofrece ningún horario (no una cita que se sale de la ventana).
- Un domingo usa las ventanas del día 7 y un lunes las del día 1 (JavaScript numera el domingo como 0; el modelo usa ISO 1–7).
- Mover una cita no la choca consigo misma: su horario actual cuenta como libre al moverla.
- `localStorage` corrupto, vacío o no disponible no rompe la demo: se vuelve a los datos de ejemplo.

---

### Task 1: Proyecto base, modelos y arranque

**Files:**
- Create: `src/Date.Manager.Front/` (con `ng new`), `src/core/models.ts`, `src/app/app.routes.ts` (rutas vacías por ahora), `README.md` del front.
- Modify: `src/index.html` (`<base href="./">`, `lang="es"`), `src/app/app.config.ts` (`provideRouter(routes, withHashLocation())`), `package.json` (`chart.js`).
- Commit también (si siguen sin commit): `docs/superpowers/specs/**`, `docs/superpowers/plans/**`, `.gitignore`.

**Interfaces:**
- Produces (`core/models.ts`, usado por todas las tareas):
```ts
export type DiaSemana = 1 | 2 | 3 | 4 | 5 | 6 | 7; // ISO: 1 = lunes … 7 = domingo
export type EstadoCita = 'Confirmada' | 'Atendida' | 'Cancelada';
export type Permiso = 'ver_panel' | 'ver_citas' | 'gestionar_citas' | 'atender_citas'
  | 'gestionar_servicios' | 'gestionar_clientes' | 'gestionar_usuarios' | 'gestionar_roles';
export interface VentanaDisponibilidad { id: string; dia: DiaSemana; inicio: string; fin: string } // 'HH:mm'
export interface Servicio { id: string; nombre: string; duracionMin: number; activo: boolean; valor: number; ventanas: VentanaDisponibilidad[] }
export interface Cliente { id: string; nombre: string; apellido: string; telefono: string; correo: string }
export interface Cita { id: string; servicioId: string; clienteId: string; fecha: string /* 'YYYY-MM-DD' */; inicio: string; fin: string; estado: EstadoCita; creadaEn: string /* ISO */; canceladaEn?: string }
export interface Rol { id: string; nombre: string; descripcion: string; permisos: Permiso[] }
export interface Usuario { id: string; nombre: string; apellido: string; correo: string; telefono: string; usuario: string; clave: string; rolId: string }
export interface Datos { servicios: Servicio[]; clientes: Cliente[]; citas: Cita[]; roles: Rol[]; usuarios: Usuario[] }
```

- [ ] **Step 1:** Desde `src/`, crear el proyecto: `npx --yes @angular/cli@21 new date-manager --directory Date.Manager.Front --style=css --routing --ssr=false --skip-git --package-manager=npm`. Si `npm install` falla con `Cannot read properties of null (reading 'edgesOut')`, crear `.npmrc` con `legacy-peer-deps=true`.
- [ ] **Step 2:** `npm install chart.js@^4.5.1`; aplicar las modificaciones de `index.html` y `app.config.ts`; crear `core/models.ts` con los tipos de arriba.
- [ ] **Step 3: Prueba de humo.** Reemplazar `app.spec.ts` por una prueba `crea la aplicación` que monta `App` con `TestBed` y espera que exista una instancia. Ejecutar `npx ng test --watch=false` → PASS. Ejecutar `npx ng build` → termina sin errores.
- [ ] **Step 4: Commit** (`docs`, `.gitignore` y el front): `feat: base del proyecto Angular y modelos de DateManager`.

---

### Task 2: Cálculo de horarios libres

**Files:**
- Create: `src/app/core/horarios.ts`, `src/app/core/horarios.spec.ts`

**Interfaces:**
- Consumes: tipos de `core/models.ts`.
- Produces:
```ts
export const PASO_MIN = 30;
export const MAX_DIAS_ANTICIPACION = 30;
export function aMinutos(hhmm: string): number;          // '09:30' → 570
export function aHHmm(min: number): string;              // 570 → '09:30'
export function isoDia(fecha: string): DiaSemana;        // 'YYYY-MM-DD' → 1..7 (lunes=1, domingo=7)
export function sumarMinutos(hhmm: string, min: number): string;
export function horariosLibres(p: { servicio: Servicio; fecha: string; citas: Cita[]; ahora: Date; excluirCitaId?: string }): string[];
```
`horariosLibres` devuelve las horas de inicio ('HH:mm') ordenadas y sin repetir; `[]` si el servicio está inactivo, la fecha es pasada o supera `MAX_DIAS_ANTICIPACION` desde `ahora`. Una cita bloquea solo si es del mismo `servicioId` y `fecha`, no está `Cancelada` y se solapa estrictamente (`inicio < finCita && fin > inicioCita`); `excluirCitaId` la ignora.

- [ ] **Step 1: Escribir pruebas que fallan** en `horarios.spec.ts`. Datos: servicio de 60 min con ventana `{dia:1, inicio:'09:00', fin:'12:00'}`; hoy simulado `ahora = new Date(2026, 10, 8, 10, 0)` (domingo 8 nov 2026); lunes = `'2026-11-09'`. Casos (nombre → aserción):
  - `ofrece inicios cada 30 min mientras la cita quepa` → `['09:00','09:30','10:00','10:30','11:00']`.
  - `una cita contigua no bloquea` → con cita `10:00–11:00` del mismo servicio: `['09:00','11:00']`.
  - `ignora citas canceladas y de otros servicios` → igual que sin citas.
  - `ventana más corta que la duración no ofrece nada` → servicio de 90 min, ventana `09:00–10:00` → `[]`.
  - `usa ISO para el domingo` → `isoDia('2026-11-08') === 7` y `isoDia('2026-11-09') === 1`; ventana de día 7 ofrece horarios el domingo y ninguno el lunes.
  - `hoy no ofrece horas pasadas` → `ahora` lunes 10:15: no incluye `'09:00'`..`'10:00'`, sí `'10:30'`.
  - `límite de anticipación` → con ventanas todos los días: `'2026-12-09'` (30 días) ofrece horarios; `'2026-12-10'` → `[]`; una fecha anterior a hoy → `[]`.
  - `servicio inactivo` → `[]`.
  - `excluirCitaId libera el horario propio` → con cita `c1` a las `10:00`, sin excluir no incluye `'10:00'`; con `excluirCitaId:'c1'` sí.
  - `ventanas solapadas no repiten horas` → sin duplicados.
- [ ] **Step 2:** `npx ng test --watch=false` → FAIL (módulo no existe).
- [ ] **Step 3:** Implementar las funciones de la sección Interfaces en `horarios.ts`, lógica pura, sin Angular. Parsear `fecha` por partes (`new Date(y, m-1, d)`), no con `new Date('YYYY-MM-DD')`, para no caer en UTC.
- [ ] **Step 4:** `npx ng test --watch=false` → PASS.
- [ ] **Step 5: Commit:** `feat: cálculo de horarios libres por servicio`.

---

### Task 3: Permisos y métricas del panel

**Files:**
- Create: `src/app/core/permisos.ts`, `permisos.spec.ts`, `src/app/core/metricas.ts`, `metricas.spec.ts`

**Interfaces:**
- Consumes: modelos; `horarios.ts` (`aMinutos`, `isoDia`).
- Produces (`permisos.ts`):
```ts
export const CATALOGO_PERMISOS: { id: Permiso; etiqueta: string }[];   // los 8 permisos, con texto en español
export const MENU: { ruta: string; etiqueta: string; permisos: Permiso[] }[]; // visible si el rol tiene ALGUNO: /panel[ver_panel], /agenda[ver_citas], /citas[ver_citas], /servicios[gestionar_servicios], /clientes[gestionar_clientes], /usuarios[gestionar_usuarios,gestionar_roles]
export function conDependencias(permisos: Permiso[]): Permiso[];       // gestionar_citas y atender_citas añaden ver_citas
export function puede(rol: Rol | undefined, permiso: Permiso): boolean;
export function primeraRuta(rol: Rol | undefined): string;             // primera entrada de MENU visible; '/sin-acceso' si ninguna
export function validarCambioRol(p: { actor: Usuario; rolAntes: Rol; rolDespues: Rol }): string | null;   // mensaje de error o null
export function puedeEliminarRol(rolId: string, usuarios: Usuario[]): boolean;
```
- Produces (`metricas.ts`):
```ts
export type Alerta = { tipo: 'cita_nueva' | 'cita_cancelada' | 'servicio_sin_horario'; titulo: string; detalle: string; ref: string };
export function ocupacion(servicios: Servicio[], citas: Cita[], fecha: string): number;       // 0..1, 0 si no hay minutos disponibles
export function ocupacionServicio(servicio: Servicio, citas: Cita[], desde: string, hasta: string): number;
export function ingresosEstimados(servicios: Servicio[], citas: Cita[], desde: string, hasta: string): number;
export function citasPorDia(citas: Cita[], desde: string, hasta: string): { fecha: string; total: number }[];  // incluye días con 0
export function alertas(servicios: Servicio[], citas: Cita[], ahora: Date): Alerta[];
export function variacion(actual: number, previo: number): number | null;                     // fracción; null si previo = 0
```
Ocupación = minutos de citas no canceladas ÷ minutos de las ventanas de servicios **activos** en ese rango. Ingresos = suma de `valor` de citas no canceladas con `fecha` en el rango (ambos extremos incluidos). Alertas: `cita_nueva` si `creadaEn` < 24 h; `cita_cancelada` si `canceladaEn` < 24 h; `servicio_sin_horario` solo para un servicio **activo con `ventanas.length === 0`**.

- [ ] **Step 1: Pruebas que fallan.** `permisos.spec.ts`: `gestionar_citas añade ver_citas`; `ver_panel queda igual`; `primeraRuta de un rol con solo ver_citas es '/agenda'` y `con ninguno es '/sin-acceso'`; `validarCambioRol rechaza (mensaje no nulo) que el actor se quite gestionar_roles de su propio rol` y `permite quitárselo a otro rol`; `puedeEliminarRol es false si hay usuarios con ese rol`. `metricas.spec.ts`: servicio con ventana de 240 min el lunes y una cita de 60 min → `ocupacion === 0.25`; con la cita `Cancelada` → `0`; sin ventanas → `0` (no `NaN`); `ingresosEstimados` suma valores y excluye canceladas; `citasPorDia` devuelve ceros para días vacíos; `alertas`: servicio activo sin ventanas → una alerta, inactivo sin ventanas → ninguna, cita creada hace 2 h → `cita_nueva`, hace 30 h → ninguna; `variacion(5, 0) === null` y `variacion(12, 10) ≈ 0.2`.
- [ ] **Step 2:** Ejecutar tests → FAIL.
- [ ] **Step 3:** Implementar `permisos.ts` y `metricas.ts` como funciones puras con las firmas de Interfaces.
- [ ] **Step 4:** Ejecutar tests → PASS.
- [ ] **Step 5: Commit:** `feat: reglas de permisos y métricas del panel`.

---

### Task 4: Capa de datos, datos de ejemplo y almacén

**Files:**
- Create: `src/app/core/errores.ts`, `src/app/core/agenda-repo.ts`, `src/app/core/demo-agenda-repo.ts`, `src/app/core/seed.ts`, `demo-agenda-repo.spec.ts`
- Modify: `src/app/app.config.ts` (proveer `AgendaRepo` → `DemoAgendaRepo` y `RELOJ`)

**Interfaces:**
- Consumes: `horariosLibres`, `sumarMinutos`, `validarCambioRol`, `puedeEliminarRol`, modelos.
- Produces:
```ts
// errores.ts — todas extienden Error
export class HorarioNoDisponibleError extends Error {}   // mensaje: 'Ese horario ya no está disponible, elige otro.'
export class RolEnUsoError extends Error {}
export class CambioNoPermitidoError extends Error {}
export const RELOJ: InjectionToken<() => Date>;          // por defecto () => new Date()

// agenda-repo.ts
export interface DatosCliente { nombre: string; apellido: string; telefono: string; correo: string }
export abstract class AgendaRepo {
  abstract readonly cambios: Signal<number>;              // sube con cada escritura, para recalcular pantallas
  abstract servicios(): Promise<Servicio[]>;
  abstract guardarServicio(s: Servicio): Promise<Servicio>;           // crea si el id no existe
  abstract horariosLibres(servicioId: string, fecha: string, excluirCitaId?: string): Promise<string[]>;
  abstract reservar(p: { servicioId: string; fecha: string; inicio: string; cliente: DatosCliente }): Promise<Cita>;
  abstract citas(): Promise<Cita[]>;
  abstract cancelarCita(id: string): Promise<Cita>;
  abstract moverCita(id: string, fecha: string, inicio: string): Promise<Cita>;
  abstract marcarAtendida(id: string): Promise<Cita>;
  abstract clientes(): Promise<Cliente[]>;
  abstract guardarCliente(c: Cliente): Promise<Cliente>;
  abstract roles(): Promise<Rol[]>;
  abstract guardarRol(rol: Rol, actorId: string): Promise<Rol>;
  abstract eliminarRol(id: string): Promise<void>;
  abstract usuarios(): Promise<Usuario[]>;
  abstract guardarUsuario(u: Usuario): Promise<Usuario>;
  abstract eliminarUsuario(id: string): Promise<void>;
  abstract iniciarSesion(usuario: string, clave: string): Promise<Usuario | null>;
  abstract reiniciar(): Promise<void>;
}
// seed.ts
export function sembrar(ahora: Date): Datos;
```
`DemoAgendaRepo` guarda `Datos` en memoria y en `localStorage` bajo la clave `datemanager.demo.v1`; cada acceso a `localStorage` va en `try/catch`. Reglas: `reservar` revalida con `horariosLibres` y lanza `HorarioNoDisponibleError`; busca cliente por `correo` sin distinguir mayúsculas y, si existe, lo reutiliza y actualiza nombre y teléfono; `fin = sumarMinutos(inicio, duración del servicio)`; la cita guarda `inicio` y `fin`. `moverCita` valida con `excluirCitaId` y mismo servicio. `guardarRol` pasa por `validarCambioRol` y normaliza con `conDependencias` (lanza `CambioNoPermitidoError`). `eliminarRol` lanza `RolEnUsoError` si `puedeEliminarRol` es falso.
`sembrar`: roles `Administrador` (los 8 permisos) y `Recepción` (`ver_citas`, `gestionar_citas`, `atender_citas`, `gestionar_clientes`); usuarios `marta` (Administrador) y `andres` (Recepción), clave `demo123`; servicios «Consulta inicial» (30 min, $40.000), «Sesión estándar» (60 min, $80.000), «Sesión extendida» (90 min, $110.000), cada uno con ventanas distintas; ~8 clientes; citas de las últimas 3 semanas y de los próximos 10 días, relativas a `ahora`, ninguna cruzada; al menos una `cita_nueva`, una cancelada reciente y citas para hoy.

- [ ] **Step 1: Pruebas que fallan** en `demo-agenda-repo.spec.ts` (reloj fijo en lunes `2026-11-09 08:00`, `localStorage` limpio antes de cada prueba):
  - `reservar crea cita Confirmada con fin = inicio + duración`.
  - `la segunda reserva del mismo horario lanza HorarioNoDisponibleError`; la de otro servicio a la misma hora sí funciona.
  - `reservar en servicio inactivo lanza HorarioNoDisponibleError`.
  - `reutiliza el cliente por correo sin distinguir mayúsculas y actualiza el teléfono` (`clientes().length` no cambia).
  - `cancelarCita libera el horario` y fija `canceladaEn`.
  - `moverCita a su propio horario es válido; a uno ocupado lanza HorarioNoDisponibleError`.
  - `cambiar la duración de un servicio no modifica citas existentes`.
  - `eliminarRol con usuarios lanza RolEnUsoError`; `guardarRol quitando gestionar_roles al rol del actor lanza CambioNoPermitidoError`.
  - `iniciarSesion devuelve el usuario con credenciales correctas y null con incorrectas`.
  - `localStorage con JSON corrupto cae en los datos de ejemplo` (escribir `'{'` en la clave y crear un repo nuevo).
  - `reiniciar restaura los datos de ejemplo`.
  - `los datos de ejemplo son coherentes`: para cada cita no cancelada del seed, su `inicio` pertenece a `horariosLibres({..., excluirCitaId: cita.id, ahora: <inicio del día de la cita>})`.
- [ ] **Step 2:** Ejecutar tests → FAIL.
- [ ] **Step 3:** Implementar `errores.ts`, la clase abstracta, `DemoAgendaRepo` (estado en un `signal<Datos>`; leer con try/catch y validar que el JSON tenga las cinco listas, si no, usar `sembrar`) y `sembrar`. Registrar los proveedores en `app.config.ts`.
- [ ] **Step 4:** Ejecutar tests → PASS.
- [ ] **Step 5: Commit:** `feat: capa de datos, almacén en navegador y datos de ejemplo`.

---

### Task 5: Estilos, sesión, guardas, inicio de sesión y estructura del panel

**Files:**
- Create: `src/styles.css` (tokens y base), `src/app/core/sesion.ts`, `src/app/core/guards.ts`, `src/app/features/login/login.ts|html`, `src/app/layout/panel-shell.ts|html`, `src/app/core/guards.spec.ts`, `src/app/features/login/login.spec.ts`, componentes vacíos con título para `panel`, `agenda`, `citas`, `servicios`, `clientes`, `usuarios`, `sin-acceso` en `src/app/features/*`
- Modify: `src/app/app.routes.ts`

**Interfaces:**
- Consumes: `AgendaRepo.iniciarSesion/roles`, `puede`, `primeraRuta`, `MENU`.
- Produces:
```ts
// sesion.ts (providedIn root)
class SesionService { readonly usuario: Signal<Usuario | null>; readonly rol: Signal<Rol | undefined>;
  iniciar(u: Usuario, roles: Rol[]): void; cerrar(): void; puede(p: Permiso): boolean }   // persiste en sessionStorage con try/catch
// guards.ts
export const conSesion: CanActivateFn;       // sin sesión → UrlTree('/login')
export const conPermiso: CanActivateFn;      // lee route.data['permisos']: Permiso[] (basta uno); sin permiso → UrlTree(primeraRuta(rol))
```
Rutas: `''` → `/reservar`; `/reservar` y `/login` públicas; `/panel` (`ver_panel`), `/agenda` y `/citas` (`ver_citas`), `/servicios` (`gestionar_servicios`), `/clientes` (`gestionar_clientes`), `/usuarios` (`gestionar_usuarios` o `gestionar_roles`), `/sin-acceso`, todas bajo `PanelShell` con `conSesion`. `**` → `/reservar`.

- [ ] **Step 1: Pruebas que fallan.** `guards.spec.ts`: `sin sesión redirige a /login`; `rol sin ver_panel que abre /panel va a su primera ruta`; `rol con el permiso entra`. `login.spec.ts`: `credenciales incorrectas muestran «Usuario o clave incorrectos.»`; `entrar con una tarjeta de cuenta de ejemplo inicia sesión y navega a la primera ruta del rol`.
- [ ] **Step 2:** Ejecutar tests → FAIL.
- [ ] **Step 3:** Escribir `styles.css` con las variables de color de Global Constraints, bordes rectos, `:focus-visible` con contorno de tinta y `@media (prefers-reduced-motion: reduce)` que anula transiciones. Implementar `SesionService`, guardas, login (formulario más tarjetas «Marta · Administrador» y «Andrés · Recepción» que entran con un clic) y `PanelShell` (menú lateral oscuro filtrado con `MENU`, nombre y rol del usuario, botón «Cerrar sesión», botón «Reiniciar demo» que llama `reiniciar()`). En menos de 900 px el menú pasa a barra superior desplazable.
- [ ] **Step 4:** Ejecutar tests → PASS; `npx ng build` sin errores.
- [ ] **Step 5: Commit:** `feat: sesión, guardas, login de ejemplo y estructura del panel`.

---

### Task 6: Página pública de reserva con regla de tiempo

**Files:**
- Create: `src/app/shared/regla-tiempo.ts|html|css`, `src/app/features/reservar/reservar.ts|html|css`, `regla-tiempo.spec.ts`, `reservar.spec.ts`

**Interfaces:**
- Consumes: `AgendaRepo` (`servicios`, `horariosLibres`, `reservar`, `citas`), `RELOJ`, `HorarioNoDisponibleError`, `aMinutos`.
- Produces (usado también en Task 7):
```ts
export interface BloqueRegla { desdeMin: number; hastaMin: number; tipo: 'ocupado' | 'elegido' | 'cita' | 'atendida' | 'fuera'; etiqueta?: string }
// <app-regla-tiempo [desdeMin]="540" [hastaMin]="780" [bloques]="..." [ahoraMin]="..." (elegir)="..."> 
// Dibuja una regla vertical con una marca por hora; cada bloque se posiciona y mide proporcional a los minutos (misma escala px/min).
// Los bloques 'ocupado'/'fuera' van rayados; 'elegido' en cobalto; 'atendida' en gris; ahoraMin dibuja la línea roja.
```
Ruta `/reservar` con tres pasos (servicio y horario → datos → «Cita confirmada»), texto y orden de las pantallas según spec §3 y la maqueta `mockups/flujo-cliente-regla-de-tiempo.html`.

- [ ] **Step 1: Pruebas que fallan.** `regla-tiempo.spec.ts`: dado `desdeMin=540, hastaMin=780` y un bloque de 60 min, su alto en píxeles es el doble que el de un bloque de 30 min (`data-testid="bloque"`), y los bloques `ocupado` llevan la clase `rayado`. `reservar.spec.ts` (reloj fijo, repo de ejemplo): `muestra solo servicios activos`; `elegir servicio, día y hora y enviar datos válidos muestra «Cita confirmada» con servicio, fecha, hora y valor`; `datos incompletos o correo inválido no avanzan y muestran el error junto al campo`; `si el horario se ocupa antes de enviar, muestra «Ese horario ya no está disponible, elige otro.» y vuelve al paso 1 con la hora elegida deseleccionada`.
- [ ] **Step 2:** Ejecutar tests → FAIL.
- [ ] **Step 3:** Implementar `ReglaTiempoComponent` (`selector: 'app-regla-tiempo'`, escala fija, p. ej. 1 min = 1 px, `OnPush`) y la página: servicios activos como lista seleccionable (nombre, duración, valor); día con flechas ‹ › (no antes de hoy, no más de 30 días); el rango de la regla cubre las ventanas del día; los horarios libres son botones sobre la regla y el elegido se dibuja con la altura de la duración; paso 2 con resumen, nombre, apellido, teléfono, correo y «Solicitar cita»/«Cambiar horario»; paso 3 con estado Confirmada y «Reservar otra cita». Enlace discreto «Acceso del negocio» a `/login`. Diseño de una columna en celular.
- [ ] **Step 4:** Ejecutar tests → PASS; `npx ng build` sin errores.
- [ ] **Step 5: Commit:** `feat: página pública de reserva con regla de tiempo`.

---

### Task 7: Servicios, citas y agenda del panel

**Files:**
- Create: `src/app/core/validaciones.ts`, `validaciones.spec.ts`, `src/app/shared/agenda-dia.ts|html`, `src/app/features/servicios/servicios.ts|html`, `src/app/features/citas/citas.ts|html`, `src/app/features/agenda/agenda.ts|html`, `citas.spec.ts`
- Modify: las pantallas vacías creadas en Task 5.

**Interfaces:**
- Consumes: `AgendaRepo`, `SesionService.puede`, `<app-regla-tiempo>`/`BloqueRegla`, `horariosLibres`.
- Produces: `validarServicio(s: Servicio): string | null` (nombre no vacío; `duracionMin` entero > 0; `valor` ≥ 0; cada ventana con `inicio < fin` en `HH:mm`) y `<app-agenda-dia [fecha]="'YYYY-MM-DD'">` en `src/app/shared/agenda-dia.ts` (la agenda por columnas de un día, usada por Agenda en esta tarea y por el Panel en la Task 8).

- [ ] **Step 1: Pruebas que fallan.** `validaciones.spec.ts`: servicio válido → `null`; nombre vacío, duración 0, ventana con `fin <= inicio` → mensaje no nulo. `citas.spec.ts`: `cancelar una cita Confirmada la muestra como Cancelada`; `mover elige una hora libre del mismo servicio y actualiza la fila`; `sin gestionar_citas no aparecen los botones Cancelar y Mover`; `sin atender_citas no aparece «Marcar como atendida»`.
- [ ] **Step 2:** Ejecutar tests → FAIL.
- [ ] **Step 3:** Implementar. **Servicios:** tabla con activar/desactivar y formulario para crear/editar nombre, duración, valor y ventanas (día de la semana en español, inicio, fin) con `validarServicio`. **Citas:** listado con filtros por fecha, servicio y estado; acciones Cancelar, Mover (diálogo con la regla de tiempo del servicio y `excluirCitaId`) y Marcar como atendida, según permisos. **Agenda:** una columna por servicio activo para el día elegido (flechas y selector de fecha) con `<app-regla-tiempo>`: ventanas libres sin marca, fuera de ventana rayado, citas como bloques, atendidas o ya pasadas en gris, línea roja de «ahora» solo hoy.
- [ ] **Step 4:** Ejecutar tests → PASS; `npx ng build` sin errores.
- [ ] **Step 5: Commit:** `feat: gestión de servicios, citas y agenda`.

---

### Task 8: Panel de inicio y clientes

**Files:**
- Create: `src/app/shared/grafica.ts`, `src/app/features/panel/panel.ts|html`, `src/app/features/clientes/clientes.ts|html`, `panel.spec.ts`
- Modify: las pantallas `panel` y `clientes` de Task 5.

**Interfaces:**
- Consumes: `metricas.ts`, `AgendaRepo`, `<app-agenda-dia>` (Task 7), Chart.js.
- Produces: `<app-grafica tipo="barras" | "barras-h" [etiquetas]="string[]" [valores]="number[]" resumen="…">` — envuelve Chart.js en un `<canvas>` con un texto `resumen` accesible; destruye el gráfico al salir.

- [ ] **Step 1: Pruebas que fallan.** `panel.spec.ts` (reloj fijo, repo de ejemplo; Chart.js sustituido por un doble en la prueba): muestra seis indicadores con sus etiquetas (citas de hoy, próxima cita, ocupación de hoy, citas nuevas 30 días, canceladas 30 días, ingresos estimados); `la alerta «servicio sin horario» aparece al quitar todas las ventanas de un servicio activo y desaparece al restituirlas`; `cuando no hay alertas muestra «Todo en orden: no hay alertas.»`.
- [ ] **Step 2:** Ejecutar tests → FAIL.
- [ ] **Step 3:** Implementar según spec §4 y la maqueta `mockups/panel-dueno-dashboard.html`: saludo, seis indicadores (los de 30 días comparan con los 30 anteriores usando `variacion`), alertas (máximo 6; clic navega a Citas o Servicios), agenda de hoy por servicio (`<app-agenda-dia [fecha]="hoy">` de la Task 7), gráfica de citas por día (30 días), ocupación por servicio de la semana y tabla de las próximas citas con botón «Abrir». **Clientes:** listado con búsqueda por nombre, correo o teléfono, y edición de un cliente.
- [ ] **Step 4:** Ejecutar tests → PASS; `npx ng build` sin errores.
- [ ] **Step 5: Commit:** `feat: panel de inicio con indicadores y alertas, y clientes`.

---

### Task 9: Usuarios, roles y permisos

**Files:**
- Create: `src/app/features/usuarios/usuarios.ts|html`, `usuarios.spec.ts`
- Modify: la pantalla `usuarios` de Task 5.

**Interfaces:**
- Consumes: `AgendaRepo` (`usuarios`, `roles`, `guardarRol`, `eliminarRol`, `guardarUsuario`, `eliminarUsuario`), `CATALOGO_PERMISOS`, `conDependencias`, `RolEnUsoError`, `CambioNoPermitidoError`, `SesionService`.

- [ ] **Step 1: Pruebas que fallan.** `usuarios.spec.ts`: `marcar «Cancelar y mover citas» marca también «Ver citas»`; `el administrador no puede quitarse gestionar_roles de su rol y ve el mensaje de error`; `eliminar un rol con usuarios muestra el aviso y no lo elimina`; `crear un rol nuevo con permisos y asignarlo a un usuario lo hace aparecer en el selector`; `sin gestionar_roles se ven los usuarios pero no se editan los roles`.
- [ ] **Step 2:** Ejecutar tests → FAIL.
- [ ] **Step 3:** Implementar dos secciones en la pantalla: **Usuarios** (lista, crear, editar, eliminar; selector de rol; no permite que el usuario activo se elimine a sí mismo) y **Roles** (lista, crear, eliminar, y editor con la casilla de cada permiso del catálogo con su etiqueta; al marcar una casilla se aplica `conDependencias`; errores de `RolEnUsoError`/`CambioNoPermitidoError` se muestran junto a la acción).
- [ ] **Step 4:** Ejecutar tests → PASS; `npx ng build` sin errores.
- [ ] **Step 5: Commit:** `feat: gestión de usuarios, roles y permisos`.

---

### Task 10: Verificación completa y cierre

**Files:**
- Modify: `src/Date.Manager.Front/README.md` (comandos, cuentas de ejemplo, qué probar).

- [ ] **Step 1:** `npx ng test --watch=false` → todo PASS. `npx ng build` → sin errores ni avisos nuevos.
- [ ] **Step 2: Recorrido manual** con `npm start` (http://localhost:4200) y el navegador, comprobando cada criterio de spec §12; anotar el resultado de cada uno:
  - Reservar una cita en `#/reservar` a 375 px de ancho y verla **Confirmada** en el panel (Citas y Agenda); cancelarla y ver el horario libre otra vez.
  - Intentar reservar una hora ocupada (abrir dos pestañas con la misma hora): la segunda muestra «Ese horario ya no está disponible, elige otro.».
  - Un servicio inactivo no aparece en la página pública; un servicio activo sin ventanas genera la alerta y no ofrece horarios.
  - Crear un rol con solo «Ver citas», asignarlo a un usuario nuevo, entrar con él y ver solo Agenda y Citas, sin botones de Cancelar ni Mover.
  - Intentar quitar `gestionar_roles` al rol propio del administrador → mensaje de error.
  - Consola del navegador sin errores durante todo el recorrido.
- [ ] **Step 3:** Corregir lo que falle (con prueba que lo reproduzca cuando sea lógica) y repetir Step 1.
- [ ] **Step 4:** Completar el README (instalar, `npm start`, `npm test`, cuentas `marta`/`andres` con `demo123`, enlaces directos `#/reservar`, `#/panel`).
- [ ] **Step 5: Commit:** `docs: README del front y verificación de la fase 1`.

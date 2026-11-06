# DateManager — Demo funcional en Angular (Fase 1)

Fecha: 2026-11-06 · Estado: borrador para aprobación

## 1. Intención y por qué

DateManager es una plataforma de gestión y reserva de citas para negocios que atienden por servicios con duración y horario: barberías, peluquerías, veterinarias, consultorios médicos u odontológicos. Hoy esos negocios agendan por llamadas, mensajes o agendas de papel, lo que produce tiempo perdido, errores al anotar, cruces de horario y dificultad para saber qué hay libre. El cliente además tiene que escribir al negocio y esperar respuesta para saber si hay cupo.

La solución: una **página pública** donde el cliente, **sin crear cuenta**, ve los servicios, elige un horario libre y queda con la cita confirmada, y un **panel de administración** donde el negocio gestiona servicios, horarios, citas, clientes, usuarios y roles.

El proyecto es para un curso. La entrega es **un MVP que solucione el problema** descrito arriba. No hay un negocio ni un dueño reales: todo debe quedar **genérico**, sin nombre, logo ni vocabulario de un rubro concreto.

El problema, las entidades y el diagrama de clases vienen del archivo `EVIDENCIA PRODUCTO (SEM 6).pdf` en la raíz del repositorio. Ese PDF es la fuente de verdad de las entidades.

### Las tres fases

El proyecto se hace en este orden, cada fase con su propia planeación:

1. **Fase 1 (este documento): demo funcional en Angular.** Corre solo en el navegador, sin backend, con datos de ejemplo.
2. **Fase 2: API en Java** que reemplaza los datos de ejemplo. Se planea después. Ya existe un esqueleto en `src/Date.Manager.Back` (Maven, Java 25, paquete `App.Domain` con las seis entidades y una clase `Main`).
3. **Fase 3: validaciones de base de datos.** Se planea al final.

Este documento solo cubre la Fase 1, pero la Fase 1 debe dejar el terreno listo para la Fase 2 (ver sección 8).

## 2. Metas, no metas, anti-metas

**Metas**
- Un cliente puede reservar una cita completa sin cuenta, y la cita queda confirmada al instante si el horario es válido.
- El administrador del negocio ve de un vistazo cómo va el día y puede gestionar todo lo necesario para operar.
- Los roles y sus permisos los define el administrador desde la propia aplicación.
- Funciona bien en celular para el cliente.
- La demo se puede mostrar en clase de principio a fin sin explicaciones técnicas.

**No metas (fuera de alcance en esta fase)**
- Backend, base de datos real, autenticación real.
- Notificaciones al cliente (correo, WhatsApp, SMS) y recordatorios.
- Pagos en línea.
- Varios negocios en la misma instalación.
- Cambiar los nombres de la interfaz según el rubro (por ejemplo «cliente» por «paciente»). El administrador dijo expresamente que, además de roles y permisos, no necesita configurar nada más por ahora.
- Agenda por profesional o por recurso (ver riesgo en la sección 9).

**Anti-metas (la demo fracasa aunque técnicamente funcione si…)**
- Se siente hecha para un solo rubro (por ejemplo, textos o íconos de barbería).
- El cliente tiene que crear cuenta o esperar aprobación.
- Se puede reservar una hora que ya está ocupada o fuera del horario del servicio.
- El diseño parece una plantilla genérica; la «regla de tiempo» (sección 5) es lo que lo distingue.

## 3. Lo que ve el cliente (página pública)

Tres pantallas, sin cuenta. Maqueta de referencia: `docs/superpowers/specs/mockups/flujo-cliente-regla-de-tiempo.html` (se abre en el navegador).

1. **Servicio y horario.** Lista de servicios activos con nombre, duración y valor. Al elegir uno y un día, aparece el día como una **regla de horas**: tramos ocupados rayados, horarios libres disponibles y, al elegir uno, un bloque tan alto como la duración del servicio. Se puede cambiar de día con flechas.
2. **Datos del cliente.** Nombre, apellido, teléfono y correo, con el resumen de lo elegido y una opción para volver a cambiar el horario. Botón «Solicitar cita».
3. **Cita confirmada.** Resumen con servicio, fecha y hora, valor, y datos del cliente. Estado **Confirmada**. Opción de reservar otra cita. El cliente no puede ver, cancelar ni mover la cita después desde la página pública (ver sección 4).

## 4. Lo que ve el administrador (panel)

Estructura tomada del dashboard del proyecto de referencia `D:\Workshop\HephDev\Andromeda\src\demo-taller` (carpeta fuera de este repositorio): menú lateral oscuro, indicadores arriba, campanilla de alertas, gráficas y tablas con botón «Abrir». Maqueta de referencia: `docs/superpowers/specs/mockups/panel-dueno-dashboard.html`. El proyecto `demo-taller` queda fuera de este repositorio y quizá quien construya no lo tenga: esta sección y la maqueta deben bastar. **Las cifras y las alertas de la maqueta son inventadas.** El administrador aprobó la estructura general con la indicación de ajustar los detalles al construirla.

Secciones del menú: **Panel**, **Agenda**, **Citas**, **Servicios**, **Clientes**, **Usuarios y roles**.

- **Panel.** Indicadores propuestos: citas de hoy, próxima cita, ocupación de hoy, citas nuevas y canceladas de los últimos 30 días, ingresos estimados. Alertas propuestas: cita nueva, cita cancelada, servicio sin horario disponible. Gráfica de citas por día y de ocupación por servicio. Tabla de próximas citas. La pieza característica es la **agenda de hoy con una columna por servicio** (regla de tiempo): gris = atendida o con la hora ya pasada, rayado = fuera del horario del servicio, línea roja = ahora.
- **Agenda.** La misma vista por columnas, para cualquier día.
- **Citas.** Listado con filtros. **Cancelar** pasa la cita a Cancelada y libera el horario. **Mover** elige otra fecha y hora libre **del mismo servicio**, con la misma regla de tiempo y la misma revalidación que la reserva pública; no se puede cambiar de servicio ni forzar un cruce. Cancelar y mover son solo del panel: el cliente no puede hacerlo desde la página pública en esta fase. *(Decisiones mías.)*
- **Servicios.** Crear, editar, activar o desactivar servicios, y editar sus ventanas de disponibilidad (día de la semana, hora de inicio, hora de fin).
- **Clientes.** Listado y edición.
- **Usuarios y roles.** Gestión de usuarios, de roles y de permisos por rol (sección 7).

## 5. Dirección visual: «regla de tiempo»

Elegida por el administrador entre tres opciones. El tiempo se dibuja como una regla vertical de horas: la duración de un servicio es un bloque más alto o más bajo, lo ocupado va rayado. Es el único elemento «memorable»; el resto es sobrio.

- Colores de partida (de las maquetas, ajustables): fondo gris frío `#E9EEF2`, tinta azul oscuro `#101C33`, acento cobalto `#2B50E6`, gris de apoyo `#51607A`, rayado `#C9D2DC`. Alertas: ámbar `#D68A00`, rojo `#C0392B`.
- Bordes rectos, líneas de 1,5 px en tinta, sin sombras decorativas.
- Texto de la interfaz en español, en oración normal, con verbos claros («Solicitar cita», «Cancelar cita»); la misma acción se llama igual en todo el flujo.
- El cliente usa la página sobre todo en celular; el panel es de escritorio pero debe adaptarse a pantallas pequeñas.
- Accesibilidad mínima: foco de teclado visible, contraste suficiente, respeta «reducir movimiento».

## 6. Cómo se construye

Decisiones tomadas por mí (el administrador puede cambiarlas), basadas en el proyecto de referencia `demo-taller`, que el administrador mostró como ejemplo de lo que quiere:

- **Angular 21**, componentes standalone, signals, detección de cambios `OnPush`.
- **Chart.js** para las gráficas.
- **Vitest** para las pruebas.
- **Rutas con hash** (`#/panel`), para que funcione abierta desde cualquier servidor estático.
- **Sin backend:** un almacén de datos en el navegador con datos de ejemplo (`DemoStore`), con un botón para reiniciar la demo.
- **Ubicación:** `src/Date.Manager.Front` dentro de este repositorio, junto a `src/Date.Manager.Back`.
- `.superpowers/` (archivos de trabajo de la planeación) no se versiona: está en el `.gitignore`. Las dos maquetas finales sí se guardan en `docs/superpowers/specs/mockups/`.

## 7. Reglas de negocio

**Citas y horarios**
- Cada servicio tiene su **propia agenda independiente**: una cita en un servicio no bloquea a los otros, ni siquiera a la misma hora.
- Un horario está libre si cae dentro de alguna ventana de disponibilidad del servicio para ese día de la semana **y** no se cruza con otra cita no cancelada del mismo servicio. La duración de la cita es la del servicio.
- La cita queda **Confirmada** automáticamente al reservarla; el dueño no aprueba nada. Puede cancelar o mover citas desde el panel.
- La disponibilidad se **revalida al guardar**: si otro cliente ocupó ese horario mientras tanto, el cliente ve «Ese horario ya no está disponible, elige otro» y vuelve a la regla de tiempo. *(Decisión mía.)*
- Los servicios inactivos no aparecen en la página pública. Un servicio sin ventanas de disponibilidad no tiene horarios para reservar y genera una alerta en el panel. *(Decisión mía.)*
- Estados de una cita: **Confirmada**, **Atendida**, **Cancelada**. *(Decisión mía; el PDF solo dice «estado».)* «Atendida» se marca **a mano** desde el panel (con el permiso correspondiente); una cita pasada que nadie marcó sigue Confirmada y se ve en gris en la agenda.
- Si una persona reserva con un correo que ya existe (sin distinguir mayúsculas), se reutiliza su registro de cliente en vez de duplicarlo, y se actualizan nombre y teléfono con los últimos datos. El teléfono no sirve para identificar. *(Decisión mía.)*

**Cálculo de horarios libres** *(decisiones mías)*
- Las horas son siempre las del reloj local del navegador; no hay zonas horarias. Una ventana de disponibilidad no cruza la medianoche.
- Las horas de inicio se ofrecen cada **30 minutos** dentro de cada ventana, y la cita debe caber completa dentro de la ventana. El paso de 30 minutos es una constante fácil de cambiar.
- No se puede reservar una hora que ya pasó. Se puede reservar con hasta **30 días** de anticipación; no hay anticipación mínima.
- Este cálculo es lógica pura, separada de los componentes, con pruebas automáticas.

**Qué pasa al editar un servicio** *(decisiones mías)*
- La cita guarda su inicio y fin (como en el PDF), así que cambiar la duración de un servicio o sus ventanas **no modifica citas ya hechas**; solo afecta a las reservas nuevas.
- Desactivar un servicio lo quita de la página pública y conserva sus citas futuras, que siguen visibles y gestionables en el panel.

**Definiciones del panel** *(decisiones mías)*
- **Ocupación de hoy** = minutos reservados en citas no canceladas ÷ minutos totales de las ventanas de hoy de todos los servicios activos. Ocupación por servicio: lo mismo, por servicio.
- **Ingresos estimados** = suma del valor de las citas no canceladas del periodo.
- **Alerta «servicio sin horario»**: solo para un servicio **activo sin ninguna ventana** de disponibilidad. Otros casos (sin ventana hoy, completamente ocupado) no generan alerta.

**Roles y permisos (configurables)**
- El administrador crea los roles que necesite (recepción, profesional, etc.) y marca qué puede hacer cada uno. Encaja con la entidad `Rol` del PDF.
- La **lista de permisos la define el sistema** y no se pueden inventar permisos nuevos. *(Decisión mía.)* Catálogo propuesto: ver el panel; ver citas; crear, mover y cancelar citas; marcar citas como atendidas; gestionar servicios y horarios; gestionar clientes; gestionar usuarios; gestionar roles y permisos.
- El menú y las rutas se adaptan al rol: quien no tiene un permiso no ve esa opción ni entra por la dirección. Como en la matriz de permisos del proyecto de referencia.
- El sistema **no permite que un administrador se quite a sí mismo** el permiso de gestionar roles. *(Decisión mía.)*
- Dependencias: crear, mover, cancelar o marcar como atendida una cita exige también «ver citas»; el editor de roles marca «ver citas» solo al activar cualquiera de esas. Un rol que tiene usuarios asignados no se puede eliminar. Al entrar, cada usuario aterriza en la primera sección del menú que su rol puede ver (el Panel es la primera si tiene ese permiso). *(Decisiones mías.)*
- Roles de ejemplo con los que arranca la demo: **Administrador** (todo) y **Recepción** (citas y clientes). *(Decisión mía.)*

## 8. Datos de ejemplo y preparación para la Fase 2

Entidades del PDF: `User` (id, nombre, apellido, correo, teléfono, usuario, contraseña, rol), `Service` (id, nombre, duración, estado, valor, ventanas de disponibilidad), `AvailabilityWindow` (id, día de la semana, hora inicio, hora fin), `Client` (id, nombre, apellido, teléfono, correo), `Appointment` (id, servicio, cliente, inicio, fin, estado, fecha), `Rol` (id, nombre, descripción). Hay que añadir los **permisos** del rol.

Notas de modelo, para que el almacén de la demo no estorbe cuando llegue el backend:
- El esqueleto en Java hoy guarda el estado de la cita como `boolean` y mezcla entrada por consola con el modelo. La demo usa un estado con tres valores (sección 7); en la Fase 2 se rediseña el modelo de Java, no se adapta la demo al esqueleto.
- Los servicios y consultas de datos de la demo van en una capa propia (no dentro de los componentes), con una interfaz que luego se pueda implementar contra la API.
- El PDF incluye una contraseña en `User`. La demo no guarda contraseñas reales: usa cuentas de ejemplo con claves de ejemplo. La Fase 2 tendrá que guardarlas con hash.
- La «ventana de disponibilidad» usa el día de la semana (lunes a domingo) del PDF. Cuidado con la convención del número del día entre Angular/JavaScript (domingo = 0) y Java (lunes = 1).

**Cuentas de la demo** (pantalla de inicio de sesión como la del proyecto de referencia, con tarjetas para entrar con un clic): una de cada rol de ejemplo. Los nombres y claves son de mentira.

**Datos de ejemplo:** tres servicios genéricos (consulta inicial de 30 min, sesión estándar de 60 min, sesión extendida de 90 min), con ventanas distintas entre sí, citas repartidas en las últimas semanas y en los próximos días, y algunos clientes. Valores en pesos colombianos sin mencionar un rubro.

## 9. Riesgos y pendientes

- **Agenda por servicio vs. por profesional.** En una clínica o veterinaria la agenda suele ser por profesional. Con la agenda independiente por servicio se puede resolver creando un servicio por profesional («Consulta Dr. Pérez»). Se revisa antes de la Fase 2; no cambia la Fase 1.
- **Plazos del curso:** no se conocen fechas de entrega de cada fase. Si hay una fecha cercana, el orden de construcción (sección 10) permite recortar desde el final.
- **Alcance de «todo configurable».** Hoy se limita a roles y permisos, servicios, horarios y usuarios. Nombres según el rubro queda para una versión futura.

## 10. Orden sugerido de construcción (lo decide quien construya)

1. Base del proyecto, almacén de datos de ejemplo y reglas de horarios libres con pruebas.
2. Página pública completa (las tres pantallas).
3. Panel: servicios y ventanas de disponibilidad; citas; agenda.
4. Panel: inicio con indicadores, alertas y gráficas; clientes.
5. Usuarios, roles, permisos y guardas de rutas.

## 11. Qué queda a criterio de quien construya

- Tipografía y escala tipográfica concretas, espaciados, íconos.
- Estructura interna de carpetas y nombres de componentes.
- Textos finales de la interfaz, siguiendo los lineamientos de la sección 5.
- Cantidad y contenido exacto de los datos de ejemplo.
- Detalle de indicadores, alertas y gráficas del panel, dentro de lo propuesto en la sección 4.
- Formato exacto de pantallas pequeñas.

## 12. Cuándo la fase se considera lista

- Se puede recorrer, sin errores en consola, el camino completo: reservar una cita desde la página pública y verla confirmada en el panel; cancelarla y ver el horario libre otra vez.
- No se puede reservar una hora ocupada, fuera del horario del servicio o en un servicio inactivo.
- Crear un rol nuevo, darle permisos y entrar con un usuario de ese rol muestra solo lo permitido.
- Las reglas de horarios libres y de permisos tienen pruebas automáticas.
- Se puede iniciar con `npm install` y `npm start`, y mostrar en clase.

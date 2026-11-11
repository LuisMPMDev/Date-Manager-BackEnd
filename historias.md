# Historias de usuario

Épicas e historias de usuario de DateManager, agrupadas por fase del proyecto. Cada fase es un **hito** de GitHub con su **tag** de git; cada épica y cada historia es una *issue* (las historias son sub-issues de su épica). Todas están entregadas y cerradas.

**Roles:** *cliente* (agenda sin cuenta), *administrador del negocio*, *personal de recepción*, *usuario del panel* y *persona que mantiene el sistema*.

---

## Fase 1 · Demo Angular

Demo funcional en Angular que corre solo en el navegador, sin backend, con datos de ejemplo: página pública de reserva con «regla de tiempo» y panel de administración.

- **Tag:** [`v1.0-demo-angular`](https://github.com/LuisMPMDev/Date-Manager-BackEnd/tree/v1.0-demo-angular) · **Hito:** [Fase 1 · Demo Angular](https://github.com/LuisMPMDev/Date-Manager-BackEnd/milestone/1?closed=1)

### E01 · Reserva pública sin cuenta ([#1](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/1))

Un cliente ve los servicios, elige un horario libre en la regla de tiempo y queda con la cita confirmada al instante, sin crear cuenta y cómodo desde el celular.

#### Ver los servicios disponibles ([#2](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/2))

**Como** cliente, **quiero** ver la lista de servicios activos con su nombre, duración y valor **para** elegir con claridad qué quiero reservar.

*Criterios de aceptación*

- [x] Solo aparecen los servicios activos
- [x] Cada servicio muestra nombre, duración y valor
- [x] No se pide cuenta ni inicio de sesión

*Etiquetas:* frontend

#### Elegir un horario libre en la regla de tiempo ([#3](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/3))

**Como** cliente, **quiero** ver el día como una regla de horas con los tramos ocupados rayados y elegir un horario libre **para** saber de un vistazo qué hay disponible y cuánto dura mi cita.

*Criterios de aceptación*

- [x] Los horarios se ofrecen cada 30 minutos y la cita cabe completa en una ventana de disponibilidad
- [x] Los tramos ocupados y los que ya pasaron no se pueden elegir
- [x] Puedo reservar hasta 30 días hacia adelante y cambiar de día con flechas
- [x] Al elegir un horario aparece un bloque tan alto como la duración del servicio

*Etiquetas:* frontend

#### Dejar mis datos y confirmar la cita ([#4](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/4))

**Como** cliente, **quiero** escribir nombre, apellido, teléfono y correo y recibir la cita confirmada al instante **para** no esperar a que el negocio me responda.

*Criterios de aceptación*

- [x] La cita queda Confirmada sin aprobación del negocio
- [x] Se muestra un resumen con servicio, fecha, hora, valor y mis datos
- [x] Si ya existe un cliente con mi correo (sin distinguir mayúsculas) se reutiliza su registro y se actualizan nombre y teléfono

*Etiquetas:* frontend

#### Aviso si el horario se ocupó mientras reservaba ([#5](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/5))

**Como** cliente, **quiero** que se revalide la disponibilidad al confirmar y me avisen si ya no está libre **para** no quedarme con una cita que en realidad no existe.

*Criterios de aceptación*

- [x] Si otro cliente ocupó el horario veo «Ese horario ya no está disponible, elige otro»
- [x] Vuelvo a la regla de tiempo con los horarios actualizados

*Etiquetas:* frontend

#### Usar la reserva cómodamente desde el celular ([#6](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/6))

**Como** cliente, **quiero** que el flujo de reserva se adapte a pantallas pequeñas **para** poder agendar desde mi teléfono.

*Criterios de aceptación*

- [x] La página no se desborda en horizontal en pantallas estrechas
- [x] El foco de teclado es visible entre los pasos

*Etiquetas:* frontend, accesibilidad

### E02 · Agenda y citas del panel ([#7](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/7))

El negocio ve la agenda de cualquier día por columnas de servicio y gestiona las citas: filtrar, cancelar, mover y marcar como atendidas.

#### Ver la agenda por columnas de servicio ([#8](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/8))

**Como** administrador del negocio, **quiero** ver la agenda de un día con una columna por servicio y una línea de «ahora» **para** entender cómo va el día de un vistazo.

*Criterios de aceptación*

- [x] Cada servicio tiene su propia columna independiente
- [x] Gris para atendida o con la hora pasada, rayado fuera de horario, línea roja para ahora
- [x] Puedo cambiar de día
- [x] Las citas de un servicio inactivo siguen visibles y se pueden mover

*Etiquetas:* frontend

#### Listar y filtrar las citas ([#9](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/9))

**Como** personal de recepción, **quiero** ver el listado de citas con filtros **para** encontrar rápido la cita que busco.

*Criterios de aceptación*

- [x] El listado muestra cliente, servicio, horario y estado
- [x] Se puede filtrar por criterios básicos
- [x] «Abrir» solo aparece con permiso para ver citas

*Etiquetas:* frontend

#### Cancelar una cita y liberar el horario ([#10](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/10))

**Como** personal de recepción, **quiero** cancelar una cita desde el panel **para** que el horario quede libre para otro cliente.

*Criterios de aceptación*

- [x] La cita pasa a Cancelada
- [x] El horario vuelve a salir libre en la reserva pública

*Etiquetas:* frontend

#### Mover una cita a otra hora libre ([#11](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/11))

**Como** personal de recepción, **quiero** mover una cita a otra fecha y hora libre del mismo servicio **para** reagendar sin cruces de horario.

*Criterios de aceptación*

- [x] Solo se ofrecen horarios libres del mismo servicio con la misma regla de tiempo y revalidación que la reserva
- [x] No se puede cambiar de servicio ni forzar un cruce

*Etiquetas:* frontend

#### Marcar una cita como atendida ([#12](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/12))

**Como** personal de recepción, **quiero** marcar a mano una cita como Atendida cuando ya empezó **para** llevar el registro de lo que sí se atendió.

*Criterios de aceptación*

- [x] La opción solo se ofrece cuando la cita ya empezó
- [x] Requiere el permiso correspondiente

*Etiquetas:* frontend

### E03 · Servicios y horarios de disponibilidad ([#13](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/13))

El administrador define qué servicios ofrece el negocio, cuánto duran y en qué horarios se pueden reservar.

#### Crear y editar servicios ([#14](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/14))

**Como** administrador del negocio, **quiero** crear y editar servicios con nombre, duración y valor **para** ofrecer lo que mi negocio realmente vende.

*Criterios de aceptación*

- [x] Se validan nombre, duración y valor
- [x] Cambiar la duración o las ventanas no modifica citas ya hechas

*Etiquetas:* frontend

#### Activar o desactivar un servicio ([#15](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/15))

**Como** administrador del negocio, **quiero** desactivar un servicio sin borrarlo **para** quitarlo de la página pública conservando sus citas futuras.

*Criterios de aceptación*

- [x] Un servicio inactivo no aparece en la reserva pública
- [x] Sus citas futuras siguen visibles y gestionables en el panel

*Etiquetas:* frontend

#### Editar las ventanas de disponibilidad ([#16](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/16))

**Como** administrador del negocio, **quiero** definir día de la semana, hora de inicio y hora de fin de cada ventana de un servicio **para** que los clientes solo reserven dentro de mi horario.

*Criterios de aceptación*

- [x] Una ventana no cruza la medianoche y su inicio es menor que su fin
- [x] No se aceptan ventanas cruzadas dentro del mismo día

*Etiquetas:* frontend

#### Alerta de servicio sin horario ([#17](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/17))

**Como** administrador del negocio, **quiero** recibir una alerta cuando un servicio activo no tiene ninguna ventana **para** no tener un servicio visible pero imposible de reservar.

*Criterios de aceptación*

- [x] La alerta solo aparece para servicios activos sin ninguna ventana

*Etiquetas:* frontend

### E04 · Panel de inicio y clientes ([#18](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/18))

El negocio ve cómo va su operación en indicadores, alertas y gráficas, y administra su lista de clientes.

#### Ver indicadores y alertas del panel ([#19](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/19))

**Como** administrador del negocio, **quiero** ver citas de hoy, próxima cita, ocupación, citas nuevas y canceladas e ingresos estimados **para** saber cómo va el negocio de un vistazo.

*Criterios de aceptación*

- [x] Los indicadores usan las definiciones de ocupación e ingresos acordadas
- [x] Las alertas escriben la fecha en español

*Etiquetas:* frontend

#### Ver gráficas y próximas citas ([#20](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/20))

**Como** administrador del negocio, **quiero** ver la gráfica de citas por día, la ocupación por servicio y la tabla de próximas citas **para** detectar tendencias y lo que viene.

*Criterios de aceptación*

- [x] Las gráficas usan Chart.js
- [x] La tabla de próximas citas tiene el botón «Abrir»

*Etiquetas:* frontend

#### Buscar y editar clientes ([#21](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/21))

**Como** personal de recepción, **quiero** listar clientes con búsqueda y editar sus datos **para** mantener al día los datos de quienes reservan.

*Criterios de aceptación*

- [x] El listado tiene búsqueda
- [x] La edición valida nombre, apellido, teléfono y correo

*Etiquetas:* frontend

### E05 · Usuarios, roles y permisos ([#22](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/22))

El administrador crea roles con los permisos que necesite y asigna usuarios; el menú y las rutas se adaptan a lo que cada rol puede hacer.

#### Crear roles y elegir sus permisos ([#23](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/23))

**Como** administrador del negocio, **quiero** crear roles y marcar qué puede hacer cada uno desde la lista de permisos del sistema **para** adaptar el sistema a cómo trabaja mi equipo.

*Criterios de aceptación*

- [x] No se pueden inventar permisos nuevos
- [x] Crear, mover, cancelar o atender citas exige también «ver citas»
- [x] Existen los roles de ejemplo Administrador y Recepción

*Etiquetas:* frontend, seguridad

#### Menú y rutas según el rol ([#24](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/24))

**Como** usuario del panel, **quiero** ver solo las secciones que mi rol permite **para** no ver ni entrar a lo que no me corresponde.

*Criterios de aceptación*

- [x] Quien no tiene un permiso no ve la opción ni entra por la dirección
- [x] Al entrar aterrizo en la primera sección que mi rol puede ver

*Etiquetas:* frontend, seguridad

#### Gestionar usuarios y protecciones ([#25](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/25))

**Como** administrador del negocio, **quiero** crear, editar y eliminar usuarios con protecciones **para** no quedarme sin acceso por error.

*Criterios de aceptación*

- [x] Un administrador no puede quitarse a sí mismo el permiso de gestionar roles
- [x] Un rol con usuarios asignados no se puede eliminar
- [x] El usuario de inicio de sesión no distingue mayúsculas ni espacios

*Etiquetas:* frontend, seguridad

### E06 · Base técnica y calidad de la demo ([#26](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/26))

La demo se puede instalar, mostrar en clase de principio a fin y tiene reglas puras con pruebas automáticas.

#### Calcular horarios libres con lógica pura probada ([#27](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/27))

**Como** persona que mantiene el sistema, **quiero** que el cálculo de horarios libres sea lógica pura separada de los componentes y con pruebas **para** poder confiar en la regla central del negocio.

*Criterios de aceptación*

- [x] Hay pruebas automáticas de los casos de borde
- [x] El paso de 30 minutos es una constante fácil de cambiar

*Etiquetas:* frontend, pruebas

#### Datos de ejemplo y almacén en el navegador ([#28](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/28))

**Como** persona que mantiene el sistema, **quiero** tener un almacén con datos de ejemplo genéricos y una capa de acceso a datos propia **para** mostrar la demo sin backend y dejar el terreno listo para la API.

*Criterios de aceptación*

- [x] Hay tres servicios genéricos, clientes y citas de ejemplo
- [x] El acceso a datos va detrás de una interfaz que luego se pueda implementar contra la API

*Etiquetas:* frontend

#### Instalar y mostrar la demo ([#29](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/29))

**Como** estudiante que presenta el proyecto, **quiero** arrancar con «npm install» y «npm start» y recorrer el camino completo sin errores en consola **para** mostrar la demo en clase sin explicaciones técnicas.

*Criterios de aceptación*

- [x] El README explica cómo arrancarla
- [x] Se puede reservar y ver la cita en el panel, cancelarla y ver el horario libre otra vez

*Etiquetas:* frontend, documentación

---

## Fase 2 · API en Java

API REST en Java 21 y Spring Boot con SQL Server: login real con JWT, permisos comprobados en el servidor, reservas sin doble agendamiento y enlaces de invitación.

- **Tag:** [`v2.0-api-java`](https://github.com/LuisMPMDev/Date-Manager-BackEnd/tree/v2.0-api-java) · **Hito:** [Fase 2 · API en Java](https://github.com/LuisMPMDev/Date-Manager-BackEnd/milestone/2?closed=1)

### E07 · Plataforma de la API ([#30](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/30))

La API se construye sobre una base sólida: Spring Boot, SQL Server, migraciones versionadas, documentación OpenAPI, perfiles, secretos fuera del repositorio y datos de ejemplo.

#### Proyecto Spring Boot con SQL Server y migraciones ([#31](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/31))

**Como** persona que mantiene el sistema, **quiero** un proyecto Spring Boot conectado a SQL Server con migraciones de Flyway **para** que el esquema sea legible, versionado y revisable.

*Criterios de aceptación*

- [x] La API arranca contra SQL Server con las migraciones aplicadas desde cero
- [x] Los errores salen en un formato uniforme con código estable y mensaje en español

*Etiquetas:* backend, base-de-datos

#### Configuración por perfiles y sin secretos en el repositorio ([#32](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/32))

**Como** persona que mantiene el sistema, **quiero** configurar perfiles dev y prod y leer secretos de variables de entorno **para** no publicar claves ni contraseñas.

*Criterios de aceptación*

- [x] El repositorio no contiene secretos
- [x] CORS permite el origen del Angular en desarrollo

*Etiquetas:* backend, seguridad

#### Documentar y probar la API desde el navegador ([#33](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/33))

**Como** persona que mantiene el sistema, **quiero** tener OpenAPI/Swagger y un README con la preparación de la base **para** que cualquiera pueda arrancar y probar la API.

*Criterios de aceptación*

- [x] Swagger disponible en el perfil dev
- [x] El README explica la base, las variables y cómo arrancar

*Etiquetas:* backend, documentación

#### Datos de ejemplo y recorrido completo ([#34](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/34))

**Como** estudiante que presenta el proyecto, **quiero** cargar en dev los datos de ejemplo de la demo y comprobar un recorrido completo de punta a punta **para** mostrar la API funcionando.

*Criterios de aceptación*

- [x] Roles Administrador y Recepción, tres servicios, clientes y citas con claves de ejemplo
- [x] Hay una prueba de recorrido completo con peticiones HTTP reales

*Etiquetas:* backend, pruebas

### E08 · Inicio de sesión y permisos en el servidor ([#35](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/35))

El servidor es la autoridad: login real con contraseñas con hash y permisos comprobados en cada petición del panel.

#### Iniciar sesión con token ([#36](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/36))

**Como** usuario del panel, **quiero** iniciar sesión con usuario y contraseña y recibir un token de acceso **para** entrar de forma segura al panel.

*Criterios de aceptación*

- [x] La contraseña se guarda solo como hash
- [x] El token JWT dura 60 minutos y viene con mis datos, rol y permisos
- [x] El usuario no distingue mayúsculas ni espacios laterales

*Etiquetas:* backend, seguridad

#### Permisos comprobados en cada acción ([#37](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/37))

**Como** administrador del negocio, **quiero** que cada operación del panel exija su permiso y que los cambios de un rol apliquen de inmediato **para** que nadie haga algo editando la petición.

*Criterios de aceptación*

- [x] Sin permiso responde 403 y sin token válido 401
- [x] Los permisos se leen de la base en cada petición

*Etiquetas:* backend, seguridad

#### Administrador inicial por variables de entorno ([#38](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/38))

**Como** persona que mantiene el sistema, **quiero** crear el administrador inicial al arrancar desde variables de entorno **para** no dejar claves en el repositorio.

*Criterios de aceptación*

- [x] Si no hay usuarios, se crea el administrador con las variables definidas

*Etiquetas:* backend, seguridad

### E09 · Servicios y horarios libres en la API ([#39](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/39))

La API gestiona servicios con sus ventanas y calcula los horarios libres con la zona horaria del negocio.

#### Gestionar servicios y ventanas ([#40](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/40))

**Como** administrador del negocio, **quiero** crear, editar y activar o desactivar servicios con sus ventanas desde la API **para** administrar mi oferta con las mismas reglas de la demo.

*Criterios de aceptación*

- [x] Día de la semana ISO 1 a 7, inicio menor que fin, sin cruzar la medianoche
- [x] Un servicio con ventanas cruzadas se rechaza con un mensaje claro

*Etiquetas:* backend

#### Consultar horarios libres ([#41](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/41))

**Como** cliente, **quiero** consultar los horarios libres de un servicio en un día **para** reservar un horario realmente disponible.

*Criterios de aceptación*

- [x] Los horarios salen cada 30 minutos dentro de las ventanas
- [x] «Ahora», «hoy» y «ya pasó» se calculan con la zona horaria del negocio, por defecto America/Bogota
- [x] Máximo 30 días hacia adelante

*Etiquetas:* backend

### E10 · Reservas y citas sin doble agendamiento ([#42](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/42))

Las reservas son atómicas: aunque dos personas reserven a la vez, solo una gana el horario. Las citas siguen un ciclo de estados claro.

#### Reservar sin doble agendamiento ([#43](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/43))

**Como** cliente, **quiero** reservar un horario y que el servidor garantice que nadie más lo ocupe a la vez **para** no llegar a un horario ya tomado.

*Criterios de aceptación*

- [x] Dos reservas simultáneas del mismo horario: gana una y la otra recibe HORARIO_NO_DISPONIBLE
- [x] La reserva bloquea la agenda del servicio dentro de una transacción

*Etiquetas:* backend, concurrencia

#### Cancelar, mover y atender citas con estados válidos ([#44](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/44))

**Como** personal de recepción, **quiero** cancelar, mover y marcar atendida una cita con transiciones de estado impuestas por el servidor **para** que una cita no quede en un estado inconsistente.

*Criterios de aceptación*

- [x] Solo una cita Confirmada puede cancelarse, moverse o atenderse
- [x] Cancelar una ya cancelada no da error y no cambia nada
- [x] Atendida y Cancelada son estados finales

*Etiquetas:* backend

#### Listar citas por rango y filtros ([#45](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/45))

**Como** personal de recepción, **quiero** listar las citas por rango de fechas y filtros **para** ver la agenda real desde el panel.

*Criterios de aceptación*

- [x] El listado exige el permiso ver_citas

*Etiquetas:* backend

### E11 · Enlaces de invitación y zona del cliente ([#46](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/46))

El negocio genera enlaces únicos con los que sus clientes agendan sin cuenta; el servidor controla vencimiento, usos y desactivación.

#### Generar un enlace de invitación ([#47](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/47))

**Como** administrador del negocio, **quiero** generar un enlace con duración y máximo de usos opcional **para** invitar a mis clientes a agendar.

*Criterios de aceptación*

- [x] El código es aleatorio e impredecible, de al menos 128 bits
- [x] La duración va de 1 hora a 30 días y por defecto es de 24 horas

*Etiquetas:* backend, seguridad

#### Ver el estado de los enlaces y desactivarlos ([#48](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/48))

**Como** administrador del negocio, **quiero** ver la lista de enlaces con estado, usos y vencimiento y desactivar cualquiera **para** controlar quién puede agendar.

*Criterios de aceptación*

- [x] Los estados son Activo, Vencido, Agotado y Desactivado
- [x] Se muestra quién creó cada enlace

*Etiquetas:* backend

#### Agendar con un enlace activo ([#49](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/49))

**Como** cliente, **quiero** ver los servicios, los horarios y reservar usando un enlace de invitación **para** agendar sin crear cuenta.

*Criterios de aceptación*

- [x] Servicios, horarios y reserva exigen un enlace Activo
- [x] Un enlace vencido, agotado o desactivado responde con su error ENLACE_*
- [x] Un código desconocido responde como uno inexistente

*Etiquetas:* backend, seguridad

#### Un uso del enlace por cita, aun con concurrencia ([#50](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/50))

**Como** administrador del negocio, **quiero** que el conteo de usos y la reserva ocurran en la misma transacción **para** que un enlace de 1 uso nunca acepte dos citas.

*Criterios de aceptación*

- [x] Con máximo 1 uso y dos peticiones simultáneas solo se crea una cita

*Etiquetas:* backend, concurrencia

### E12 · Clientes, usuarios y roles con protecciones ([#51](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/51))

El servidor administra clientes, usuarios y roles e impone las protecciones que la demo solo aplicaba en pantalla.

#### Gestionar clientes por correo ([#52](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/52))

**Como** personal de recepción, **quiero** listar y editar clientes y que se reutilicen por correo **para** no duplicar clientes.

*Criterios de aceptación*

- [x] El correo identifica al cliente sin distinguir mayúsculas
- [x] Al reservar con un correo existente se actualizan nombre y teléfono

*Etiquetas:* backend

#### Gestionar usuarios y roles con protecciones ([#53](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/53))

**Como** administrador del negocio, **quiero** crear, editar y eliminar usuarios y roles con protecciones comprobadas en el servidor **para** no perder el control del sistema.

*Criterios de aceptación*

- [x] Un administrador no puede quitarse gestionar_roles
- [x] Un rol con usuarios no se elimina
- [x] Nadie se elimina a sí mismo y no se degrada al último con gestionar_roles

*Etiquetas:* backend, seguridad

#### Evitar la escalada de privilegios al asignar roles ([#54](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/54))

**Como** administrador del negocio, **quiero** que quien gestiona usuarios sin gestionar roles solo pueda asignar roles con permisos que ya tiene **para** que nadie se dé más poder del que tiene.

*Criterios de aceptación*

- [x] Asignar un rol más poderoso responde SIN_PERMISO

*Etiquetas:* backend, seguridad

### E13 · Datos del tablero en la API ([#55](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/55))

La API calcula los indicadores del panel con las mismas definiciones de la demo.

#### Obtener los datos del tablero ([#56](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/56))

**Como** administrador del negocio, **quiero** consultar los indicadores, alertas, citas por día, ocupación y próximas citas del panel **para** ver cifras reales calculadas por el servidor.

*Criterios de aceptación*

- [x] Ocupación, variaciones, ingresos y alertas siguen las definiciones acordadas
- [x] Las cifras «ahora» se calculan con el reloj del servidor

*Etiquetas:* backend

---

## Fase 2b · Angular conectado a la API

El Angular deja el modo demo y usa la API: sesión real, zona pública por enlace de invitación, pantalla de Enlaces y tablero con datos del servidor.

- **Tag:** [`v2.1-conexion-angular`](https://github.com/LuisMPMDev/Date-Manager-BackEnd/tree/v2.1-conexion-angular) · **Hito:** [Fase 2b · Angular conectado a la API](https://github.com/LuisMPMDev/Date-Manager-BackEnd/milestone/3?closed=1)

### E14 · Sesión y acceso del panel contra la API ([#57](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/57))

El panel inicia sesión de verdad, conserva y restaura la sesión y maneja con claridad los tokens vencidos y la falta de permisos.

#### Iniciar sesión contra la API ([#58](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/58))

**Como** usuario del panel, **quiero** iniciar sesión con mis credenciales reales **para** entrar al panel con los permisos que el servidor me da.

*Criterios de aceptación*

- [x] El token se guarda en sessionStorage y la contraseña nunca se guarda
- [x] El menú se filtra con los permisos que devuelve la API

*Etiquetas:* frontend, seguridad

#### Restaurar la sesión al recargar ([#59](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/59))

**Como** usuario del panel, **quiero** que la sesión se restaure al recargar la página **para** no tener que iniciar sesión de nuevo en la misma pestaña.

*Criterios de aceptación*

- [x] Al recargar se consulta GET /api/auth/yo para confirmar usuario, rol y permisos vigentes

*Etiquetas:* frontend

#### Token vencido sin pantallas en blanco ([#60](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/60))

**Como** usuario del panel, **quiero** volver a /login con un aviso cuando mi token venza **para** no quedarme en una pantalla en blanco ni en un ciclo de redirecciones.

*Criterios de aceptación*

- [x] El 401 del panel limpia la sesión y redirige con aviso sin ciclos
- [x] Un 403 muestra «sin acceso» sin cerrar la sesión

*Etiquetas:* frontend, seguridad

### E15 · Zona del cliente por enlace de invitación ([#61](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/61))

El cliente agenda solo con un enlace en la ruta /agendar/<código>, con mensajes claros cuando el enlace no sirve.

#### Agendar entrando por el enlace ([#62](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/62))

**Como** cliente, **quiero** abrir /agendar/<código> y seguir el flujo de reserva **para** agendar sin cuenta con el enlace que me dio el negocio.

*Criterios de aceptación*

- [x] Veo los servicios activos y sigo servicio, fecha, horario y datos
- [x] La confirmación muestra lo que confirmó el servidor
- [x] La ruta /reservar desaparece

*Etiquetas:* frontend

#### Mensajes claros si el enlace no sirve ([#63](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/63))

**Como** cliente, **quiero** ver un mensaje claro si el enlace está vencido, agotado, desactivado o no existe **para** saber que debo pedir uno nuevo al negocio.

*Criterios de aceptación*

- [x] Cada estado muestra su mensaje sin formulario
- [x] Un código inexistente muestra «enlace no encontrado»

*Etiquetas:* frontend

#### Evitar reservas duplicadas por doble clic ([#64](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/64))

**Como** cliente, **quiero** que dos clics rápidos en «Reservar» no creen dos citas ni muestren un error confuso **para** reservar con confianza.

*Criterios de aceptación*

- [x] Se ignoran respuestas tardías y el segundo clic no duplica la cita
- [x] Si el horario se ocupó veo el aviso y la lista se recarga

*Etiquetas:* frontend, concurrencia

#### La sesión vencida no expulsa al cliente ([#65](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/65))

**Como** cliente, **quiero** que un token vencido no me saque de la zona pública **para** no perder mi reserva por una sesión del negocio.

*Criterios de aceptación*

- [x] La zona pública nunca envía el token del panel

*Etiquetas:* frontend, seguridad

### E16 · Pantalla de Enlaces ([#66](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/66))

El administrador crea, comparte y desactiva los enlaces de invitación desde el panel.

#### Crear un enlace y copiar su ruta ([#67](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/67))

**Como** administrador del negocio, **quiero** crear un enlace con duración y máximo de usos y copiar su ruta completa **para** compartirlo con mis clientes.

*Criterios de aceptación*

- [x] Duración por defecto de 24 horas, con máximo de usos opcional
- [x] Se muestra la ruta …/#/agendar/<código> con botón de copiar
- [x] Una entrada inválida no se convierte en «sin límite»

*Etiquetas:* frontend

#### Ver y desactivar enlaces ([#68](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/68))

**Como** administrador del negocio, **quiero** ver la lista de enlaces con estado, usos, vencimiento y creador y desactivarlos **para** controlar los enlaces activos.

*Criterios de aceptación*

- [x] Los más nuevos aparecen primero
- [x] Solo ve la pantalla quien tiene gestionar_enlaces

*Etiquetas:* frontend, seguridad

### E17 · Panel conectado a la API y modo demo retirado ([#69](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/69))

Todas las pantallas del negocio leen y escriben contra la API y el código de la demo deja de existir en producción.

#### Pantallas del panel contra la API ([#70](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/70))

**Como** administrador del negocio, **quiero** que servicios, citas, clientes, usuarios y roles lean y escriban en la API **para** que lo que hago quede guardado de verdad.

*Criterios de aceptación*

- [x] Existe HttpAgendaRepo como única implementación de producción
- [x] Los errores de la API se muestran en español y los de campo junto al campo
- [x] Los ids son UUID

*Etiquetas:* frontend

#### Tablero con datos del servidor ([#71](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/71))

**Como** administrador del negocio, **quiero** que el tablero consuma las cifras de la API **para** ver números reales y consistentes.

*Criterios de aceptación*

- [x] El Angular deja de calcular cifras y solo da formato y gráficas
- [x] El tablero conserva los datos si falla una recarga

*Etiquetas:* frontend

#### Permisos auxiliares sin romper la pantalla ([#72](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/72))

**Como** usuario del panel, **quiero** que una pantalla siga funcionando si no tengo permiso para un dato secundario **para** usar lo que sí puedo ver.

*Criterios de aceptación*

- [x] El 403 de una llamada secundaria se muestra como aviso dentro de la sección

*Etiquetas:* frontend, seguridad

#### Retirar el modo demo del código de producción ([#73](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/73))

**Como** persona que mantiene el sistema, **quiero** eliminar DemoAgendaRepo, la semilla del navegador y el botón de reiniciar **para** que nada haga creer que algo se guardó sin API.

*Criterios de aceptación*

- [x] La demo queda guardada en la rama demo-sin-servidor
- [x] Un doble en memoria vive solo en src/app/testing

*Etiquetas:* frontend, pruebas

---

## Fase 3 · Validaciones en la base de datos

La base de SQL Server rechaza por sí misma los datos inválidos: restricciones, disparadores y migraciones seguras con auditoría previa.

- **Tag:** [`v3.0-validaciones-bd`](https://github.com/LuisMPMDev/Date-Manager-BackEnd/tree/v3.0-validaciones-bd) · **Hito:** [Fase 3 · Validaciones en la base de datos](https://github.com/LuisMPMDev/Date-Manager-BackEnd/milestone/4?closed=1)

### E18 · Citas sin solaparse en la base ([#74](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/74))

La base impide que dos citas no canceladas del mismo servicio y día se solapen, incluso con transacciones simultáneas.

#### Rechazar citas solapadas con un disparador ([#75](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/75))

**Como** persona que mantiene el sistema, **quiero** un disparador que rechace citas solapadas del mismo servicio y día **para** que la base sea la última defensa contra el doble agendamiento.

*Criterios de aceptación*

- [x] Que una cita termine cuando empieza otra sí vale
- [x] Se verifica en INSERT, UPDATE y varias filas a la vez
- [x] Error 50010 con mensaje sin valores de la base

*Etiquetas:* base-de-datos

#### A prueba de carreras entre transacciones ([#76](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/76))

**Como** persona que mantiene el sistema, **quiero** que dos transacciones con citas solapadas no puedan ganar las dos **para** no depender de que la aplicación sea la única que escribe.

*Criterios de aceptación*

- [x] Hay una prueba con dos conexiones concurrentes
- [x] El índice filtrado ix_cita_servicio_ocupada evita bloqueos de rango de más

*Etiquetas:* base-de-datos, concurrencia

#### Sin bloqueos de más al atender o cancelar ([#77](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/77))

**Como** persona que mantiene el sistema, **quiero** que cambios sin movimiento de horario no tomen bloqueos de rango **para** evitar interbloqueos con las reservas.

*Criterios de aceptación*

- [x] La V10 sale temprano si ninguna fila cambia de horario, estado o servicio
- [x] Una prueba de dos conexiones lo comprueba

*Etiquetas:* base-de-datos, concurrencia

### E19 · Datos coherentes y bien formados ([#78](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/78))

Las restricciones de la base rechazan datos vacíos, mal formados o incoherentes que la aplicación nunca guardaría.

#### Coherencia de las citas ([#79](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/79))

**Como** persona que mantiene el sistema, **quiero** que cancelada_en exista si y solo si la cita está Cancelada y no sea anterior a creada_en **para** evitar citas con datos contradictorios.

*Criterios de aceptación*

- [x] Restricciones ck_cita_cancelada_coherente y ck_cita_cancelada_posterior

*Etiquetas:* base-de-datos

#### Textos obligatorios no vacíos ([#80](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/80))

**Como** persona que mantiene el sistema, **quiero** que los textos obligatorios no puedan estar vacíos ni ser solo espacios **para** que la base respalde las validaciones de la aplicación.

*Criterios de aceptación*

- [x] Nombres, apellidos y teléfono de cliente, nombre de usuario, servicio y rol
- [x] No se exige a lo que la aplicación permite vacío: usuario.telefono y rol.descripcion

*Etiquetas:* base-de-datos

#### Correos, usuarios y claves con forma válida ([#81](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/81))

**Como** persona que mantiene el sistema, **quiero** que correos y nombres de usuario no tengan espacios ni mayúsculas ASCII y que la clave sea un hash BCrypt **para** proteger la invariante de que nunca se guarda la contraseña.

*Criterios de aceptación*

- [x] La comprobación de minúsculas es binaria, así que tildes y eñes pasan
- [x] ck_usuario_clave_hash_bcrypt exige la forma de BCrypt de 60 caracteres

*Etiquetas:* base-de-datos, seguridad

#### Límites de servicios y enlaces ([#82](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/82))

**Como** persona que mantiene el sistema, **quiero** que la duración de un servicio no pase de 1440 minutos y un enlace no se desactive antes de crearse **para** respaldar las reglas que ya impone la aplicación.

*Criterios de aceptación*

- [x] ck_servicio_duracion_max y ck_enlace_invitacion_desactivado

*Etiquetas:* base-de-datos

### E20 · Ventanas de disponibilidad sin cruce ([#83](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/83))

La base impide que dos ventanas de un servicio se crucen dentro del mismo día, sin romper el guardado de servicios de la aplicación.

#### Rechazar ventanas cruzadas con un disparador ([#84](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/84))

**Como** persona que mantiene el sistema, **quiero** un disparador que impida ventanas cruzadas del mismo servicio y día **para** que la base respalde la regla de la aplicación.

*Criterios de aceptación*

- [x] Ventanas pegadas extremo con extremo sí valen
- [x] Error 50020 con mensaje sin valores de la base

*Etiquetas:* base-de-datos

#### Guardar servicios sin cruces transitorios ([#85](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/85))

**Como** administrador del negocio, **quiero** que guardar un servicio con ventanas válidas siga funcionando **para** no ver errores en guardados legítimos.

*Criterios de aceptación*

- [x] ServiciosServicio reemplaza las ventanas borrando, vaciando e insertando
- [x] Un guardado con cruces reales sigue rechazándose con el mensaje en español

*Etiquetas:* backend, base-de-datos

### E21 · Migración segura, errores y documentación ([#86](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/86))

Las migraciones nuevas no pierden datos, abortan limpias si hay datos inválidos, y los errores de la base llegan como 409 a la API.

#### Migración con auditoría previa ([#87](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/87))

**Como** persona que mantiene el sistema, **quiero** que las migraciones V7 a V9 revisen los datos existentes y aborten con un mensaje legible si alguna regla sería violada **para** nunca corregir datos de usuario en silencio.

*Criterios de aceptación*

- [x] Aborta con THROW 50001 indicando tabla, regla y un puñado de ids
- [x] Cada migración es transaccional y deja la base en la versión anterior

*Etiquetas:* base-de-datos

#### Probar la migración con datos reales ([#88](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/88))

**Como** persona que mantiene el sistema, **quiero** pruebas que migren datos válidos e inválidos **para** confiar en que migrar no rompe una base con datos.

*Criterios de aceptación*

- [x] MigracionDatosTest: datos válidos intactos y reglas activas, y abortos limpios en V7, V8 y V9
- [x] Toda limpieza de pruebas exige una base cuyo nombre termine en Test

*Etiquetas:* base-de-datos, pruebas

#### Errores de la base como 409 ([#89](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/89))

**Como** cliente, **quiero** que un rechazo de la base llegue como un conflicto claro y no como un error 500 **para** entender que debo reintentar o elegir otro horario.

*Criterios de aceptación*

- [x] Los errores de disparador 50000 a 50099 se traducen a 409 CONFLICTO
- [x] CitasServicio.guardar responde HORARIO_NO_DISPONIBLE
- [x] Ningún mensaje expone valores de la base

*Etiquetas:* backend

#### Documentar las reglas de la base ([#90](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/90))

**Como** persona que mantiene el sistema, **quiero** una sección del README con cada regla, qué respalda y cómo se prueba **para** poder mantener y operar las reglas.

*Criterios de aceptación*

- [x] Lista restricciones, disparadores e índices, los números de error y las notas operativas
- [x] Avisa que la migración aborta si hay datos que violan las reglas

*Etiquetas:* base-de-datos, documentación

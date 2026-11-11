# DateManager

Plataforma genérica de gestión y reserva de citas para negocios que atienden por servicios con duración y horario (barberías, peluquerías, veterinarias, consultorios). El cliente, **sin crear cuenta**, entra con un enlace de invitación, ve los servicios, elige un horario libre y queda con la cita confirmada al instante. El negocio gestiona servicios, horarios, citas, clientes, usuarios, roles y enlaces desde un panel de administración.

Proyecto de curso. Las entidades y el problema vienen de `EVIDENCIA PRODUCTO (SEM 6).pdf`.

## Estructura

| Carpeta | Qué contiene | Cómo arrancarlo |
|---|---|---|
| [`src/Date.Manager.Back`](src/Date.Manager.Back) | API REST en Java 21 y Spring Boot con SQL Server, Flyway, JWT y permisos comprobados en el servidor | [README del backend](src/Date.Manager.Back/README.md) |
| [`src/Date.Manager.Front`](src/Date.Manager.Front) | Cliente web en Angular 21 conectado a la API: página pública de reserva y panel de administración | [README del frontend](src/Date.Manager.Front/README.md) |

Para ejecutarlo completo: prepara SQL Server y arranca la API con el perfil `dev` (datos de ejemplo), y luego `npm start` en el frontend. Los pasos exactos están en los dos README.

## Fases del proyecto

| Fase | Resultado | Tag |
|---|---|---|
| 1 · Demo Angular | Demo funcional que corre solo en el navegador, con datos de ejemplo | [`v1.0-demo-angular`](https://github.com/LuisMPMDev/Date-Manager-BackEnd/tree/v1.0-demo-angular) |
| 2 · API en Java | API con login real, permisos en el servidor, reservas sin doble agendamiento y enlaces de invitación | [`v2.0-api-java`](https://github.com/LuisMPMDev/Date-Manager-BackEnd/tree/v2.0-api-java) |
| 2b · Angular conectado a la API | El Angular usa la API: sesión real, zona pública por enlace y pantalla de Enlaces | [`v2.1-conexion-angular`](https://github.com/LuisMPMDev/Date-Manager-BackEnd/tree/v2.1-conexion-angular) |
| 3 · Validaciones en la base de datos | La base rechaza por sí misma los datos inválidos (restricciones, disparadores, migraciones seguras) | [`v3.0-validaciones-bd`](https://github.com/LuisMPMDev/Date-Manager-BackEnd/tree/v3.0-validaciones-bd) |

La demo original, sin servidor, se conserva en la rama local `demo-sin-servidor` (no está en este repositorio remoto).

## Historias de usuario

Hay 4 hitos, 21 épicas y 69 historias, todas entregadas. El detalle de cada una (criterios de aceptación y etiquetas) está en [`historias.md`](historias.md); cada épica y cada historia es una *issue* de GitHub dentro de su hito.


### Fase 1 · Demo Angular

Demo funcional en Angular que corre solo en el navegador, sin backend, con datos de ejemplo: página pública de reserva con «regla de tiempo» y panel de administración. Tag: [`v1.0-demo-angular`](https://github.com/LuisMPMDev/Date-Manager-BackEnd/tree/v1.0-demo-angular) · [Hito en GitHub](https://github.com/LuisMPMDev/Date-Manager-BackEnd/milestone/1?closed=1)

**E01 · Reserva pública sin cuenta** ([#1](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/1))

- [#2](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/2) Ver los servicios disponibles
- [#3](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/3) Elegir un horario libre en la regla de tiempo
- [#4](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/4) Dejar mis datos y confirmar la cita
- [#5](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/5) Aviso si el horario se ocupó mientras reservaba
- [#6](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/6) Usar la reserva cómodamente desde el celular

**E02 · Agenda y citas del panel** ([#7](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/7))

- [#8](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/8) Ver la agenda por columnas de servicio
- [#9](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/9) Listar y filtrar las citas
- [#10](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/10) Cancelar una cita y liberar el horario
- [#11](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/11) Mover una cita a otra hora libre
- [#12](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/12) Marcar una cita como atendida

**E03 · Servicios y horarios de disponibilidad** ([#13](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/13))

- [#14](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/14) Crear y editar servicios
- [#15](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/15) Activar o desactivar un servicio
- [#16](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/16) Editar las ventanas de disponibilidad
- [#17](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/17) Alerta de servicio sin horario

**E04 · Panel de inicio y clientes** ([#18](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/18))

- [#19](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/19) Ver indicadores y alertas del panel
- [#20](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/20) Ver gráficas y próximas citas
- [#21](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/21) Buscar y editar clientes

**E05 · Usuarios, roles y permisos** ([#22](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/22))

- [#23](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/23) Crear roles y elegir sus permisos
- [#24](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/24) Menú y rutas según el rol
- [#25](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/25) Gestionar usuarios y protecciones

**E06 · Base técnica y calidad de la demo** ([#26](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/26))

- [#27](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/27) Calcular horarios libres con lógica pura probada
- [#28](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/28) Datos de ejemplo y almacén en el navegador
- [#29](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/29) Instalar y mostrar la demo

### Fase 2 · API en Java

API REST en Java 21 y Spring Boot con SQL Server: login real con JWT, permisos comprobados en el servidor, reservas sin doble agendamiento y enlaces de invitación. Tag: [`v2.0-api-java`](https://github.com/LuisMPMDev/Date-Manager-BackEnd/tree/v2.0-api-java) · [Hito en GitHub](https://github.com/LuisMPMDev/Date-Manager-BackEnd/milestone/2?closed=1)

**E07 · Plataforma de la API** ([#30](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/30))

- [#31](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/31) Proyecto Spring Boot con SQL Server y migraciones
- [#32](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/32) Configuración por perfiles y sin secretos en el repositorio
- [#33](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/33) Documentar y probar la API desde el navegador
- [#34](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/34) Datos de ejemplo y recorrido completo

**E08 · Inicio de sesión y permisos en el servidor** ([#35](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/35))

- [#36](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/36) Iniciar sesión con token
- [#37](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/37) Permisos comprobados en cada acción
- [#38](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/38) Administrador inicial por variables de entorno

**E09 · Servicios y horarios libres en la API** ([#39](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/39))

- [#40](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/40) Gestionar servicios y ventanas
- [#41](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/41) Consultar horarios libres

**E10 · Reservas y citas sin doble agendamiento** ([#42](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/42))

- [#43](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/43) Reservar sin doble agendamiento
- [#44](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/44) Cancelar, mover y atender citas con estados válidos
- [#45](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/45) Listar citas por rango y filtros

**E11 · Enlaces de invitación y zona del cliente** ([#46](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/46))

- [#47](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/47) Generar un enlace de invitación
- [#48](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/48) Ver el estado de los enlaces y desactivarlos
- [#49](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/49) Agendar con un enlace activo
- [#50](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/50) Un uso del enlace por cita, aun con concurrencia

**E12 · Clientes, usuarios y roles con protecciones** ([#51](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/51))

- [#52](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/52) Gestionar clientes por correo
- [#53](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/53) Gestionar usuarios y roles con protecciones
- [#54](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/54) Evitar la escalada de privilegios al asignar roles

**E13 · Datos del tablero en la API** ([#55](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/55))

- [#56](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/56) Obtener los datos del tablero

### Fase 2b · Angular conectado a la API

El Angular deja el modo demo y usa la API: sesión real, zona pública por enlace de invitación, pantalla de Enlaces y tablero con datos del servidor. Tag: [`v2.1-conexion-angular`](https://github.com/LuisMPMDev/Date-Manager-BackEnd/tree/v2.1-conexion-angular) · [Hito en GitHub](https://github.com/LuisMPMDev/Date-Manager-BackEnd/milestone/3?closed=1)

**E14 · Sesión y acceso del panel contra la API** ([#57](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/57))

- [#58](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/58) Iniciar sesión contra la API
- [#59](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/59) Restaurar la sesión al recargar
- [#60](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/60) Token vencido sin pantallas en blanco

**E15 · Zona del cliente por enlace de invitación** ([#61](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/61))

- [#62](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/62) Agendar entrando por el enlace
- [#63](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/63) Mensajes claros si el enlace no sirve
- [#64](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/64) Evitar reservas duplicadas por doble clic
- [#65](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/65) La sesión vencida no expulsa al cliente

**E16 · Pantalla de Enlaces** ([#66](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/66))

- [#67](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/67) Crear un enlace y copiar su ruta
- [#68](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/68) Ver y desactivar enlaces

**E17 · Panel conectado a la API y modo demo retirado** ([#69](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/69))

- [#70](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/70) Pantallas del panel contra la API
- [#71](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/71) Tablero con datos del servidor
- [#72](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/72) Permisos auxiliares sin romper la pantalla
- [#73](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/73) Retirar el modo demo del código de producción

### Fase 3 · Validaciones en la base de datos

La base de SQL Server rechaza por sí misma los datos inválidos: restricciones, disparadores y migraciones seguras con auditoría previa. Tag: [`v3.0-validaciones-bd`](https://github.com/LuisMPMDev/Date-Manager-BackEnd/tree/v3.0-validaciones-bd) · [Hito en GitHub](https://github.com/LuisMPMDev/Date-Manager-BackEnd/milestone/4?closed=1)

**E18 · Citas sin solaparse en la base** ([#74](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/74))

- [#75](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/75) Rechazar citas solapadas con un disparador
- [#76](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/76) A prueba de carreras entre transacciones
- [#77](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/77) Sin bloqueos de más al atender o cancelar

**E19 · Datos coherentes y bien formados** ([#78](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/78))

- [#79](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/79) Coherencia de las citas
- [#80](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/80) Textos obligatorios no vacíos
- [#81](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/81) Correos, usuarios y claves con forma válida
- [#82](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/82) Límites de servicios y enlaces

**E20 · Ventanas de disponibilidad sin cruce** ([#83](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/83))

- [#84](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/84) Rechazar ventanas cruzadas con un disparador
- [#85](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/85) Guardar servicios sin cruces transitorios

**E21 · Migración segura, errores y documentación** ([#86](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/86))

- [#87](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/87) Migración con auditoría previa
- [#88](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/88) Probar la migración con datos reales
- [#89](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/89) Errores de la base como 409
- [#90](https://github.com/LuisMPMDev/Date-Manager-BackEnd/issues/90) Documentar las reglas de la base

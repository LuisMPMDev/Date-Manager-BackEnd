# DateManager Front (demo Angular)

Demo en el navegador, sin backend, de una plataforma de reserva de citas: una página pública donde el cliente reserva sin cuenta y un panel de administración para gestionar servicios, citas, clientes, usuarios y roles.

## Requisitos

- Node 20.19 o superior (con npm).

## Instalación y comandos

```
npm install
npm start          # servidor de desarrollo en http://localhost:4200
npm test           # pruebas unitarias (Vitest); para una sola corrida: npx ng test --watch=false
npm run build      # compilación a dist/
```

El `.npmrc` del proyecto activa `legacy-peer-deps=true`, así que `npm install` no necesita opciones extra.

Las rutas usan hash (`#/...`) y `<base href="./">`, por lo que el build de `dist/` se puede abrir desde cualquier servidor estático.

## Cuentas de ejemplo

| Usuario  | Clave     | Rol           | Puede                                    |
| -------- | --------- | ------------- | ---------------------------------------- |
| `marta`  | `demo123` | Administrador | Todo                                     |
| `andres` | `demo123` | Recepción     | Citas y clientes                         |

En `#/login` escribe el usuario y la clave y pulsa «Iniciar sesión». La pantalla también tiene tarjetas para entrar con un clic. Son cuentas de mentira: no hay autenticación real.

## Enlaces directos

Con el servidor en `http://localhost:4200`:

- `#/reservar`: página pública (no requiere cuenta).
- `#/login`: inicio de sesión del negocio.
- `#/panel`, `#/agenda`, `#/citas`, `#/servicios`, `#/clientes`, `#/usuarios`: panel; piden iniciar sesión y se adaptan a los permisos del rol.

## Qué probar

| Para ver…                | Haz esto                                                                                                                                              |
| ------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| Reservar una cita        | En `#/reservar` elige servicio, día y hora, completa tus datos y pulsa «Solicitar cita». Queda Confirmada. Entra como `marta` y búscala en Citas y Agenda. |
| Cancelar y mover         | En Citas, «Cancelar cita» libera el horario (vuelve a salir en `#/reservar`). «Mover cita» solo ofrece horas libres del mismo servicio.               |
| Roles y permisos         | En «Usuarios y roles» crea un rol (por ejemplo solo «Ver citas»), crea un usuario con ese rol y entra con él: el menú y las rutas muestran solo lo permitido. |
| Reglas del sistema       | Intenta quitar «Gestionar roles y permisos» al rol Administrador: no lo permite. Un rol con usuarios asignados no se puede eliminar.                   |
| Servicios y horarios     | Desactiva un servicio y desaparece de la página pública. Quítale todas las ventanas a un servicio activo y aparece la alerta «Servicio sin horario» en el Panel. |
| Reiniciar la demo        | Con sesión iniciada, «Reiniciar demo» (barra lateral) restaura los datos de ejemplo.                                                                   |

## Dónde se guardan los datos

Todo vive en el navegador (`localStorage`, clave `datemanager.demo.v1`): no hay servidor ni base de datos. Los cambios se conservan al recargar, pero son de ese navegador: no se comparten entre navegadores ni entre dispositivos. Las pestañas del mismo navegador se mantienen al día entre sí (una reserva hecha en `#/reservar` aparece en el panel abierto en otra pestaña). «Reiniciar demo» vuelve a los datos de ejemplo.

Los datos de ejemplo se calculan respecto al día en que se crean: si nadie los modificó, se renuevan solos al abrir la demo otro día. Si ya editaste datos en días anteriores, pulsa «Reiniciar demo» antes de presentar para que la agenda vuelva a quedar alrededor de hoy.

## Fase 2

El acceso a datos pasa por `AgendaRepo` (`src/app/core/agenda-repo.ts`), una clase abstracta que hoy implementa `DemoAgendaRepo` sobre `localStorage`. Ahí es donde se conectará la API en Java: basta con otra implementación que hable con el backend y registrarla en `app.config.ts`. Las reglas de horarios y permisos (`core/horarios.ts`, `core/permisos.ts`) son lógica pura y no dependen del almacenamiento.

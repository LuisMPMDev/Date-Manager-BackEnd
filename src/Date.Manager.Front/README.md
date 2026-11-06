# DateManager Front (demo Angular)

Demo en el navegador (sin backend) de agendamiento de citas: página pública y panel de administración. Los datos se guardan en `localStorage`.

## Comandos

- `npm install` — instala dependencias (el `.npmrc` usa `legacy-peer-deps`).
- `npx ng serve` — servidor de desarrollo en http://localhost:4200.
- `npx ng test --watch=false` — pruebas unitarias.
- `npx ng build` — compilación a `dist/`.

Las rutas usan hash (`#/...`) y `<base href="./">` para poder abrir el build desde cualquier carpeta.

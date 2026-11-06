import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Permiso } from './models';
import { primeraRuta } from './permisos';
import { SesionService } from './sesion';

/** Exige sesión; sin ella manda a /login. Espera a que se restaure la sesión de la pestaña. */
export const conSesion: CanActivateFn = async () => {
  const sesion = inject(SesionService);
  const router = inject(Router);
  await sesion.listo;
  return sesion.usuario() ? true : router.parseUrl('/login');
};

/** Exige alguno de los permisos de route.data['permisos']; si no, manda a la primera ruta del rol. */
export const conPermiso: CanActivateFn = async route => {
  const sesion = inject(SesionService);
  const router = inject(Router);
  await sesion.listo;
  const permisos = (route.data['permisos'] ?? []) as Permiso[];
  if (permisos.some(p => sesion.puede(p))) return true;
  return router.parseUrl(primeraRuta(sesion.rol()));
};

/** Para /login: con sesión activa no tiene sentido mostrarlo. */
export const sinSesion: CanActivateFn = async () => {
  const sesion = inject(SesionService);
  const router = inject(Router);
  await sesion.listo;
  return sesion.usuario() ? router.parseUrl(primeraRuta(sesion.rol())) : true;
};

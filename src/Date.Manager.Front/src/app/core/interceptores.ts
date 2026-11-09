import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ApiError } from './api-error';
import { API_URL, API_URL_POR_DEFECTO } from './config';
import { SesionExpirada } from './sesion-expirada';
import { TokenStore } from './token-store';

/**
 * El Bearer va solo a /panel/** y a GET /auth/yo de la API configurada;
 * nunca a /publico/**, al login ni a URLs ajenas.
 */
export function llevaToken(url: string, metodo: string, apiUrl: string = API_URL_POR_DEFECTO): boolean {
  const base = apiUrl.replace(/\/+$/, '');
  if (!url.startsWith(base + '/')) return false;
  const ruta = url.slice(base.length).split(/[?#]/)[0];
  if (ruta.startsWith('/panel/')) return true;
  return ruta === '/auth/yo' && metodo.toUpperCase() === 'GET';
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(TokenStore).leer();
  if (!token || !llevaToken(req.url, req.method, inject(API_URL))) return next(req);
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};

/** Traduce todo error HTTP a ApiError y avisa si venció la sesión (401 en una ruta con Bearer). */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const expirada = inject(SesionExpirada);
  return next(req).pipe(
    catchError((e: unknown) => {
      if (!(e instanceof HttpErrorResponse)) return throwError(() => e);
      if (e.status === 401 && req.headers.has('Authorization')) expirada.emitir();
      return throwError(() => ApiError.desde(e));
    }),
  );
};

import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from './config';
import { Rol, Usuario } from './models';
import { TokenStore } from './token-store';

/** Lo que el negocio necesita saber de quien tiene la sesión: quién es y qué puede hacer ahora. */
export interface SesionActual { usuario: Usuario; rol: Rol }

/** Acceso a /api/auth: iniciar sesión y consultar la sesión vigente. */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_URL);
  private readonly tokens = inject(TokenStore);

  /** POST /auth/login (sin Bearer). Guarda el token; la clave no se guarda en ningún lado. */
  async login(usuario: string, clave: string): Promise<SesionActual> {
    const r = await firstValueFrom(
      this.http.post<SesionActual & { token: string }>(`${this.base}/auth/login`, { usuario, clave }),
    );
    this.tokens.guardar(r.token);
    return { usuario: r.usuario, rol: r.rol };
  }

  /** GET /auth/yo: usuario, rol y permisos vigentes. No devuelve token: el guardado se conserva. */
  async yo(): Promise<SesionActual> {
    const r = await firstValueFrom(this.http.get<SesionActual>(`${this.base}/auth/yo`));
    return { usuario: r.usuario, rol: r.rol };
  }
}

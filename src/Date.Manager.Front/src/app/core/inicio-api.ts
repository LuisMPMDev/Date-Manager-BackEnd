import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from './config';
import { Inicio } from './inicio';

/** Acceso a GET /api/panel/inicio: todas las cifras del tablero ya calculadas por el servidor. */
@Injectable({ providedIn: 'root' })
export class InicioApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_URL);

  /** Sin `fecha` el servidor decide cuál es «hoy» con la zona horaria del negocio. */
  inicio(fecha?: string): Promise<Inicio> {
    const params = fecha ? new HttpParams().set('fecha', fecha) : undefined;
    return firstValueFrom(this.http.get<Inicio>(`${this.base}/panel/inicio`, { params }));
  }
}

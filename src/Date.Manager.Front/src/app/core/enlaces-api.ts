import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from './config';

export type EstadoEnlace = 'Activo' | 'Vencido' | 'Agotado' | 'Desactivado';

/** Enlace de invitación tal como lo devuelve la API; `maxUsos` se omite cuando no hay límite. */
export interface Enlace {
  id: string;
  codigo: string;
  /** Ruta lógica `/agendar/<código>`: el sitio base lo pone quien la muestra. */
  ruta: string;
  estado: EstadoEnlace;
  usos: number;
  maxUsos?: number;
  /** Instantes en ISO UTC. */
  venceEn: string;
  creadoEn: string;
  creadoPor: string;
  creadoPorNombre: string;
}

/** Cuerpo de la creación: lo que falte lo decide el servidor (1440 minutos, sin límite de usos). */
export interface PeticionEnlace {
  duracionMinutos?: number;
  maxUsos?: number;
}

/** Acceso a /api/panel/enlaces (permiso gestionar_enlaces). */
@Injectable({ providedIn: 'root' })
export class EnlacesApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_URL);

  /** Más nuevos primero: el orden lo pone la API. */
  listar(): Promise<Enlace[]> {
    return firstValueFrom(this.http.get<Enlace[]>(`${this.base}/panel/enlaces`));
  }

  crear(peticion: PeticionEnlace): Promise<Enlace> {
    return firstValueFrom(this.http.post<Enlace>(`${this.base}/panel/enlaces`, peticion));
  }

  /** Idempotente en la API: desactivar uno ya desactivado responde igual. */
  desactivar(id: string): Promise<Enlace> {
    return firstValueFrom(this.http.post<Enlace>(`${this.base}/panel/enlaces/${id}/desactivar`, {}));
  }
}

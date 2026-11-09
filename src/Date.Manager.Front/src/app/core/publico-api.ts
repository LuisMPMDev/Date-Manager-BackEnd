import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { DatosCliente } from './agenda-repo';
import { API_URL } from './config';
import { EstadoEnlace } from './enlaces-api';
import { Servicio } from './models';

/** Lo único que la zona del cliente sabe de un enlace; `venceEn` es un instante ISO UTC. */
export interface EstadoInvitacion {
  estado: EstadoEnlace;
  venceEn: string;
}

/** La cita recién reservada, tal como la devuelve la API: sin id de cita ni datos del cliente. */
export interface CitaPublica {
  servicioId: string;
  servicio: string;
  fecha: string;
  inicio: string;
  fin: string;
  estado: string;
}

export interface ReservaPublica {
  servicioId: string;
  fecha: string;
  inicio: string;
  cliente: DatosCliente;
}

/**
 * Acceso a /api/publico/invitaciones/{codigo}: la zona del cliente, sin sesión. El interceptor de autenticación
 * no añade Authorization a estas rutas, haya o no una sesión del negocio abierta en la pestaña.
 */
@Injectable({ providedIn: 'root' })
export class PublicoApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_URL);

  private ruta(codigo: string): string {
    return `${this.base}/publico/invitaciones/${encodeURIComponent(codigo)}`;
  }

  estado(codigo: string): Promise<EstadoInvitacion> {
    return firstValueFrom(this.http.get<EstadoInvitacion>(this.ruta(codigo)));
  }

  /** Solo los servicios activos: el servidor ya filtra. */
  servicios(codigo: string): Promise<Servicio[]> {
    return firstValueFrom(this.http.get<Servicio[]>(`${this.ruta(codigo)}/servicios`));
  }

  /** Horas `HH:mm` libres de un servicio en una fecha `YYYY-MM-DD`. */
  horarios(codigo: string, servicioId: string, fecha: string): Promise<string[]> {
    const consulta = `fecha=${encodeURIComponent(fecha)}`;
    return firstValueFrom(
      this.http.get<string[]>(`${this.ruta(codigo)}/servicios/${encodeURIComponent(servicioId)}/horarios?${consulta}`),
    );
  }

  reservar(codigo: string, peticion: ReservaPublica): Promise<CitaPublica> {
    return firstValueFrom(this.http.post<CitaPublica>(`${this.ruta(codigo)}/citas`, peticion));
  }
}

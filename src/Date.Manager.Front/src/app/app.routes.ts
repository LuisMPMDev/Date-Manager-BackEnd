import { Routes } from '@angular/router';
import { conPermiso, conSesion, sinSesion } from './core/guards';
import { SEGMENTO_PUBLICO } from './core/permisos';
import { PanelShell } from './layout/panel-shell';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  // Zona del cliente: pública, sin guardias; entra solo con el código de un enlace de invitación.
  {
    path: `${SEGMENTO_PUBLICO}/:codigo`,
    loadComponent: () => import('./features/agendar/agendar').then(m => m.AgendarComponent),
  },
  {
    path: 'login',
    canActivate: [sinSesion],
    loadComponent: () => import('./features/login/login').then(m => m.LoginComponent),
  },
  {
    path: '',
    component: PanelShell,
    canActivate: [conSesion],
    children: [
      {
        path: 'panel',
        canActivate: [conPermiso],
        data: { permisos: ['ver_panel'] },
        loadComponent: () => import('./features/panel/panel').then(m => m.PanelComponent),
      },
      {
        path: 'agenda',
        canActivate: [conPermiso],
        data: { permisos: ['ver_citas'] },
        loadComponent: () => import('./features/agenda/agenda').then(m => m.AgendaComponent),
      },
      {
        path: 'citas',
        canActivate: [conPermiso],
        data: { permisos: ['ver_citas'] },
        loadComponent: () => import('./features/citas/citas').then(m => m.CitasComponent),
      },
      {
        path: 'servicios',
        canActivate: [conPermiso],
        data: { permisos: ['gestionar_servicios'] },
        loadComponent: () => import('./features/servicios/servicios').then(m => m.ServiciosComponent),
      },
      {
        path: 'clientes',
        canActivate: [conPermiso],
        data: { permisos: ['gestionar_clientes'] },
        loadComponent: () => import('./features/clientes/clientes').then(m => m.ClientesComponent),
      },
      {
        path: 'usuarios',
        canActivate: [conPermiso],
        data: { permisos: ['gestionar_usuarios', 'gestionar_roles'] },
        loadComponent: () => import('./features/usuarios/usuarios').then(m => m.UsuariosComponent),
      },
      {
        path: 'enlaces',
        canActivate: [conPermiso],
        data: { permisos: ['gestionar_enlaces'] },
        loadComponent: () => import('./features/enlaces/enlaces').then(m => m.EnlacesComponent),
      },
      {
        path: 'sin-acceso',
        loadComponent: () => import('./features/sin-acceso/sin-acceso').then(m => m.SinAccesoComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'login' },
];

/** Instante ISO UTC en la hora local del navegador (o en `zona`, para las pruebas), con el nombre de la zona. */
export function fechaHora(iso: string, zona?: string): string {
  return new Date(iso).toLocaleString('es-CO', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    timeZoneName: 'short',
    timeZone: zona,
  });
}

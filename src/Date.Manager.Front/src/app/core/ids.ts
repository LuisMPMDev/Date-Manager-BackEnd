/** Id nuevo para lo que se crea en pantalla: la API exige UUID en los `PUT /…/{id}`. */
export function nuevoId(): string {
  return crypto.randomUUID();
}

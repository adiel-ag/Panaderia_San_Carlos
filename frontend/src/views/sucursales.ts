import type { SolicitudSucursal } from '../api/sucursales'

export function validarSucursal(form: SolicitudSucursal): Partial<Record<keyof SolicitudSucursal, string>> {
  const errores: Partial<Record<keyof SolicitudSucursal, string>> = {}
  if (!form.nombre.trim() || form.nombre.trim().length > 100) errores.nombre = 'El nombre debe tener entre 1 y 100 caracteres.'
  if (form.direccion.trim().length > 200) errores.direccion = 'La dirección admite máximo 200 caracteres.'
  if (form.telefono.trim().length > 20 || !/^[0-9 +()\-]*$/.test(form.telefono)) errores.telefono = 'El teléfono admite dígitos, espacios, +, - y paréntesis; máximo 20 caracteres.'
  return errores
}
export function normalizarSucursal(form: SolicitudSucursal): SolicitudSucursal {
  return { nombre: form.nombre.trim(), direccion: form.direccion.trim(), telefono: form.telefono.trim() }
}

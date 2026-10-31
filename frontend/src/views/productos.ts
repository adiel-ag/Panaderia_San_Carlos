import type { LocationQuery, LocationQueryRaw } from 'vue-router'
import type { Producto, SolicitudProducto } from '../api/productos'

export type FiltroTipo = 'todos' | 'insumos' | 'finales'
export type FiltroEstado = 'ACTIVO' | 'INACTIVO' | 'TODOS'
export interface FiltrosProductos { tipo: FiltroTipo; estado: FiltroEstado; q: string }
export interface FormularioProducto { nombre: string; idTipoProducto: number | null; idUnidad: number | null; stockMinimo: number | null }
export type ErroresProducto = Partial<Record<keyof FormularioProducto, string>>

export function leerFiltrosProductos(query: LocationQuery): FiltrosProductos {
  return {
    tipo: query.tipo === 'insumos' || query.tipo === 'finales' ? query.tipo : 'todos',
    estado: query.estado === 'INACTIVO' || query.estado === 'TODOS' ? query.estado : 'ACTIVO',
    q: typeof query.q === 'string' ? query.q.slice(0, 100) : '',
  }
}
export function queryFiltrosProductos(filtros: FiltrosProductos): LocationQueryRaw {
  return { ...(filtros.tipo !== 'todos' ? { tipo: filtros.tipo } : {}),
    ...(filtros.estado !== 'ACTIVO' ? { estado: filtros.estado } : {}),
    ...(filtros.q.trim() ? { q: filtros.q.trim() } : {}) }
}
export function filtrarProductos(lista: Producto[], filtros: FiltrosProductos): Producto[] {
  const q = filtros.q.trim().toLocaleLowerCase('es')
  return lista.filter((item) => (filtros.tipo === 'todos' || item.nombreTipoProducto === (filtros.tipo === 'insumos' ? 'INSUMO' : 'PRODUCTO_FINAL'))
    && (filtros.estado === 'TODOS' || item.estado === filtros.estado)
    && item.nombre.toLocaleLowerCase('es').includes(q))
}
export function resumenProductos(lista: Producto[]): string {
  return `${lista.filter((item) => item.nombreTipoProducto === 'INSUMO').length} insumos · ${lista.filter((item) => item.nombreTipoProducto === 'PRODUCTO_FINAL').length} productos finales · ${lista.filter((item) => item.estado === 'INACTIVO').length} inactivos`
}
export function validarProducto(form: FormularioProducto): ErroresProducto {
  const errores: ErroresProducto = {}
  if (!form.nombre.trim() || form.nombre.trim().length > 100) errores.nombre = 'El nombre debe tener entre 1 y 100 caracteres.'
  if (!form.idTipoProducto || form.idTipoProducto <= 0) errores.idTipoProducto = 'Seleccione un tipo.'
  if (!form.idUnidad || form.idUnidad <= 0) errores.idUnidad = 'Seleccione una unidad.'
  if (form.stockMinimo === null || !Number.isFinite(form.stockMinimo) || form.stockMinimo < 0
    || form.stockMinimo > 999999999.999 || Math.round(form.stockMinimo * 1000) / 1000 !== form.stockMinimo) {
    errores.stockMinimo = 'Indique un mínimo entre 0 y 999999999.999 con hasta 3 decimales.'
  }
  return errores
}
export function normalizarProducto(form: FormularioProducto): SolicitudProducto {
  return { nombre: form.nombre.trim(), idTipoProducto: form.idTipoProducto!, idUnidad: form.idUnidad!, stockMinimo: form.stockMinimo! }
}

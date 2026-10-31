import { api } from './cliente'

export type EstadoCatalogo = 'ACTIVO' | 'INACTIVO'
export interface Producto {
  idProducto: number
  idTipoProducto: number
  idUnidad: number
  nombre: string
  nombreTipoProducto: 'INSUMO' | 'PRODUCTO_FINAL'
  nombreUnidadMedida: string
  abreviaturaUnidadMedida: string
  stockMinimo: number
  estado: EstadoCatalogo
  fechaCreacion: string
  enUso: boolean
}
export interface SolicitudProducto { nombre: string; idTipoProducto: number; idUnidad: number; stockMinimo: number }
export interface UnidadCatalogo { idUnidad: number; nombre: string; abreviatura: string }
export interface TipoProducto { idTipoProducto: number; nombre: string }

export function listarProductos(): Promise<Producto[]> { return api('productos', { avisar: false }) }
export function listarUnidades(): Promise<UnidadCatalogo[]> { return api('catalogo/unidades', { avisar: false }) }
export function listarTiposProducto(): Promise<TipoProducto[]> { return api('catalogo/tipos-producto', { avisar: false }) }
export function crearProducto(solicitud: SolicitudProducto): Promise<Producto> {
  return api('productos', { metodo: 'POST', cuerpo: solicitud, avisar: true })
}
export function actualizarProducto(id: number, solicitud: SolicitudProducto): Promise<Producto> {
  return api(`productos/${id}`, { metodo: 'PUT', cuerpo: solicitud, avisar: true })
}
export function cambiarEstadoProducto(id: number, estado: EstadoCatalogo): Promise<Producto> {
  return api(`productos/${id}/estado`, { metodo: 'PUT', cuerpo: { estado }, avisar: true })
}

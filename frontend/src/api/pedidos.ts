import { ApiError, api } from './cliente'
import type { Pagina } from './tipos'

export const ESTADOS = ['PENDIENTE', 'CONFIRMADO', 'EN_PRODUCCION', 'LISTO_DESPACHO', 'DESPACHADO', 'CANCELADO'] as const
export type Estado = typeof ESTADOS[number]

const etiquetas: Record<Estado, string> = {
  PENDIENTE: 'Pendiente', CONFIRMADO: 'Confirmado', EN_PRODUCCION: 'En producción',
  LISTO_DESPACHO: 'Listo para despacho', DESPACHADO: 'Despachado', CANCELADO: 'Cancelado',
}
export function etiquetaEstado(estado: Estado): string { return etiquetas[estado] }

export interface PedidoResumen {
  idPedido: number
  idSucursal: number
  nombreSucursal: string
  idUsuario: number
  nombreUsuario: string
  fechaPedido: string
  fechaEntrega: string
  estado: Estado
  observacion: string | null
  cantidadProductos: number
}
export interface PedidoDetalle {
  idPedidoDetalle: number
  idPedido: number
  idProducto: number
  nombreProducto: string
  cantidad: number
}
export interface Pedido {
  idPedido: number
  idSucursal: number
  idUsuario: number
  fechaEntrega: string
  estado: Estado
  observacion: string | null
  /** Producción a la que pertenece; null si no está en ninguna. */
  idProduccion?: number | null
  detalles: PedidoDetalle[]
}
export interface Despacho {
  idDespacho: number
  idPedido: number
  idSucursal: number
  idUsuario: number
  fechaDespacho: string
  estado: string
  observacion: string | null
}
export interface Sucursal { idSucursal: number; nombre: string }
export interface Producto {
  idProducto: number
  nombre: string
  nombreTipoProducto: string
  abreviaturaUnidadMedida: string
  nombreUnidadMedida: string
  estado: string
}
export interface SolicitudPedido {
  idSucursal: number
  fechaEntrega: string
  observacion: string | null
  detalles: { idProducto: number; cantidad: number }[]
}
export interface FiltrosApi {
  estado?: Estado
  idSucursal?: number
  entregaDesde?: string
  entregaHasta?: string
}

export function listarPedidos(filtros: FiltrosApi, pagina = 1, tamano = 20, avisar = true): Promise<Pagina<PedidoResumen>> {
  return api('pedidos', { consulta: { pagina, tamano, ...filtros }, avisar })
}
export function obtenerPedido(id: number): Promise<Pedido> { return api(`pedidos/${id}`) }
export async function obtenerDespacho(id: number): Promise<Despacho | null> {
  try { return await api(`pedidos/${id}/despacho`, { avisar: false }) }
  catch (error) { if (error instanceof ApiError && error.status === 404) return null; throw error }
}
export function listarSucursales(): Promise<Sucursal[]> { return api('sucursales') }
export function listarProductos(): Promise<Producto[]> { return api('productos') }
export function crearPedido(solicitud: SolicitudPedido): Promise<{ idPedido: number }> {
  return api('pedidos', { metodo: 'POST', cuerpo: solicitud, avisar: true })
}
export function cancelarPedido(id: number): Promise<Pedido> {
  return api(`pedidos/${id}/cancelar`, { metodo: 'POST', avisar: true })
}
export function editarPedido(id: number, solicitud: Omit<SolicitudPedido, 'idSucursal'>): Promise<Pedido> {
  return api(`pedidos/${id}`, { metodo: 'PUT', cuerpo: solicitud, avisar: true })
}
export function recibirPedido(id: number, observacion: string | null): Promise<Despacho> {
  return api(`pedidos/${id}/recepcion`, { metodo: 'POST', cuerpo: { observacion }, avisar: true })
}

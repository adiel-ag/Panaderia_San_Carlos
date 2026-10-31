import { api } from './cliente'
import type { PedidoResumen } from './pedidos'
import type { Pagina } from './tipos'

export const ESTADOS_PRODUCCION = ['PLANIFICADA', 'EN_PROCESO', 'FINALIZADA', 'CANCELADA'] as const
export type EstadoProduccion = typeof ESTADOS_PRODUCCION[number]

const etiquetas: Record<EstadoProduccion, string> = {
  PLANIFICADA: 'Planificada', EN_PROCESO: 'En proceso', FINALIZADA: 'Finalizada', CANCELADA: 'Cancelada',
}
export function etiquetaEstadoProduccion(estado: EstadoProduccion): string { return etiquetas[estado] }

export interface ProduccionResumen {
  idProduccion: number
  idUsuario: number
  nombreUsuario: string
  fechaProduccion: string
  estado: EstadoProduccion
  observacion: string | null
  cantidadPedidos: number
}
export interface ProduccionConPedidos extends ProduccionResumen {
  pedidos: PedidoResumen[]
}
export interface ProduccionDetalle {
  idProduccionDetalle: number
  idProduccion: number
  idProducto: number
  idReceta: number
  cantidadPlanificada: number
  cantidadProducida: number
}
export interface InsumoProduccion {
  idInsumo: number
  nombre: string
  unidad: string
  requerido: number
  disponible: number
  faltante: number
  sinInventario: boolean
}
export interface InsumosProduccion {
  idProduccion: number
  estado: EstadoProduccion
  insumos: InsumoProduccion[]
  productosSinReceta: { idProducto: number; nombre: string }[]
  suficiente: boolean
}

export function listarProducciones(estado: EstadoProduccion | null, pagina = 1, tamano = 20, avisar = true): Promise<Pagina<ProduccionResumen>> {
  return api('producciones', { consulta: { pagina, tamano, estado }, avisar })
}
export function obtenerProduccion(id: number): Promise<ProduccionConPedidos> { return api(`producciones/${id}`) }
export function obtenerDetalleProduccion(id: number): Promise<ProduccionDetalle[]> { return api(`producciones/${id}/detalle`) }
export function obtenerInsumos(id: number): Promise<InsumosProduccion> { return api(`producciones/${id}/insumos`, { avisar: false }) }
export function crearProduccion(observacion: string | null): Promise<{ idProduccion: number }> {
  return api('producciones', { metodo: 'POST', cuerpo: { observacion } })
}
export function asociarPedido(id: number, idPedido: number): Promise<{ idProduccion: number; mensaje: string }> {
  return api(`producciones/${id}/pedidos`, { metodo: 'POST', cuerpo: { idPedido } })
}
export function generarPlan(id: number): Promise<ProduccionDetalle[]> {
  return api(`producciones/${id}/generar-plan`, { metodo: 'POST' })
}
export function registrarProducido(id: number, idProducto: number, cantidad: number): Promise<unknown> {
  return api(`producciones/${id}/productos/${idProducto}/cantidad-producida`, { metodo: 'PUT', cuerpo: { cantidadProducida: cantidad } })
}
export function finalizarProduccion(id: number): Promise<unknown> {
  return api(`producciones/${id}/finalizar`, { metodo: 'POST' })
}
export function cancelarProduccion(id: number): Promise<ProduccionConPedidos> {
  return api(`producciones/${id}/cancelar`, { metodo: 'POST', avisar: true })
}

import { api } from './cliente'

export interface ProductoPedidoReporte { idProducto: number; producto: string; unidad: string; cantidad: number }
export interface PedidosSucursalReporte {
  idSucursal: number
  sucursal: string
  pedidos: number
  despachados: number
  pendientes: number
  productos: ProductoPedidoReporte[]
}
export interface ProductoProduccionReporte { idProducto: number; producto: string; unidad: string; planificado: number; producido: number }
export interface ProduccionDiaReporte { fecha: string; producciones: number; productos: ProductoProduccionReporte[] }
export interface ConsumoInsumoReporte { idInsumo: number; insumo: string; unidad: string; consumido: number }
export interface Alertas { pedidosAtrasados: number; productosSinReceta: number; insumosBajoMinimo: number }

// Los errores 400 se muestran en la pantalla, por eso no se avisa con toast.
export function reportePedidosPorSucursal(desde: string, hasta: string): Promise<PedidosSucursalReporte[]> {
  return api('reportes/pedidos-por-sucursal', { consulta: { desde, hasta }, avisar: false })
}
export function reporteProduccionPorDia(desde: string, hasta: string): Promise<ProduccionDiaReporte[]> {
  return api('reportes/produccion-por-dia', { consulta: { desde, hasta }, avisar: false })
}
export function reporteConsumoInsumos(desde: string, hasta: string): Promise<ConsumoInsumoReporte[]> {
  return api('reportes/consumo-insumos', { consulta: { desde, hasta }, avisar: false })
}
export function obtenerAlertas(): Promise<Alertas> { return api('alertas', { avisar: false }) }

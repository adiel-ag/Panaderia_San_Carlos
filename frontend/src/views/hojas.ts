import type { Estado, Pedido, Producto } from '../api/pedidos'
import type { EstadoProduccion, ProduccionDetalle } from '../api/producciones'
import type { Rol } from '../api/tipos'

export interface FilaHojaProduccion { idProducto: number; nombre: string; unidad: string; planificado: number }

const ROLES_HOJA_DESPACHO: readonly Rol[] = ['ADMINISTRADOR', 'DESPACHO', 'GERENTE']
const ROLES_HOJA_PRODUCCION: readonly Rol[] = ['ADMINISTRADOR', 'PRODUCCION', 'GERENTE']

export function puedeImprimirDespacho(rol: Rol | null, estado: Estado | undefined): boolean {
  return rol !== null && ROLES_HOJA_DESPACHO.includes(rol) && (estado === 'LISTO_DESPACHO' || estado === 'DESPACHADO')
}
export function puedeImprimirProduccion(rol: Rol | null, estado: EstadoProduccion | undefined, pedidos: number): boolean {
  return rol !== null && ROLES_HOJA_PRODUCCION.includes(rol) && (estado === 'EN_PROCESO' || (estado === 'PLANIFICADA' && pedidos > 0))
}

function unidad(productos: readonly Producto[], idProducto: number): string {
  return productos.find((producto) => producto.idProducto === idProducto)?.abreviaturaUnidadMedida ?? ''
}
/** Plan en proceso: lo planificado de cada producto del detalle. */
export function filasDesdePlan(detalle: readonly ProduccionDetalle[], productos: readonly Producto[]): FilaHojaProduccion[] {
  return detalle.map((linea) => ({
    idProducto: linea.idProducto,
    nombre: productos.find((producto) => producto.idProducto === linea.idProducto)?.nombre ?? `Producto #${linea.idProducto}`,
    unidad: unidad(productos, linea.idProducto),
    planificado: linea.cantidadPlanificada,
  })).sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))
}
/** Producción planificada: suma por producto lo pedido en sus pedidos. */
export function filasDesdePedidos(pedidos: readonly Pedido[], productos: readonly Producto[]): FilaHojaProduccion[] {
  const filas = new Map<number, FilaHojaProduccion>()
  for (const pedido of pedidos) {
    for (const detalle of pedido.detalles) {
      const fila = filas.get(detalle.idProducto)
      if (fila) fila.planificado = Math.round((fila.planificado + detalle.cantidad) * 1000) / 1000
      else filas.set(detalle.idProducto, { idProducto: detalle.idProducto, nombre: detalle.nombreProducto, unidad: unidad(productos, detalle.idProducto), planificado: detalle.cantidad })
    }
  }
  return [...filas.values()].sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))
}

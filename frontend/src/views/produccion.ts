import type { LocationQuery, LocationQueryRaw } from 'vue-router'
import type { PedidoResumen, Producto } from '../api/pedidos'
import { ESTADOS_PRODUCCION, type EstadoProduccion, type ProduccionDetalle } from '../api/producciones'

export interface FiltrosProduccion { estado: EstadoProduccion | null; pagina: number }
export interface FilaPlan {
  idProducto: number
  nombre: string
  unidad: string
  planificado: number
  producido: number
  faltante: number | null
  completo: boolean
}

function simple(valor: LocationQuery[string]): string | undefined {
  return typeof valor === 'string' ? valor : undefined
}
function enteroPositivo(valor: string | undefined): number | null {
  if (!valor || !/^[1-9]\d*$/.test(valor)) return null
  const numero = Number(valor)
  return Number.isSafeInteger(numero) ? numero : null
}
export function leerFiltrosProduccion(query: LocationQuery): FiltrosProduccion {
  const estado = simple(query.estado)
  return {
    estado: ESTADOS_PRODUCCION.find((opcion) => opcion === estado) ?? null,
    pagina: enteroPositivo(simple(query.pagina)) ?? 1,
  }
}
export function queryFiltrosProduccion(filtros: FiltrosProduccion): LocationQueryRaw {
  return {
    ...(filtros.estado ? { estado: filtros.estado } : {}),
    ...(filtros.pagina !== 1 ? { pagina: String(filtros.pagina) } : {}),
  }
}

export function textoResumenProduccion(enProceso: number, planificadas: number): string {
  if (enProceso === 0 && planificadas === 0) return 'Sin producciones activas'
  return `${enProceso} en proceso · ${planificadas} ${planificadas === 1 ? 'planificada' : 'planificadas'}`
}

export function validarCantidad(valor: number | null): string | null {
  if (valor === null || !Number.isFinite(valor) || valor <= 0 || valor > 999999999.999 ||
    Math.abs(valor * 1000 - Math.round(valor * 1000)) > 0.00001) {
    return 'Ingrese una cantidad mayor que 0, con hasta 3 decimales y máximo 999999999.999.'
  }
  return null
}

export function abreviaturaUnidad(abreviatura: string | undefined): string {
  return abreviatura === 'UND' ? 'u' : abreviatura === 'KG' ? 'kg' : abreviatura === 'L' ? 'L' : ''
}

// Compara en milésimas para no arrastrar errores de punto flotante.
function milesimas(valor: number): number { return Math.round(valor * 1000) }

export function filasPlan(detalle: ProduccionDetalle[], productos: Producto[]): FilaPlan[] {
  return detalle.map((linea) => {
    const producto = productos.find((item) => item.idProducto === linea.idProducto)
    const diferencia = milesimas(linea.cantidadPlanificada) - milesimas(linea.cantidadProducida)
    return {
      idProducto: linea.idProducto,
      nombre: producto?.nombre ?? `Producto #${linea.idProducto}`,
      unidad: abreviaturaUnidad(producto?.abreviaturaUnidadMedida),
      planificado: linea.cantidadPlanificada,
      producido: linea.cantidadProducida,
      faltante: diferencia > 0 ? diferencia / 1000 : null,
      completo: diferencia <= 0,
    }
  })
}

export function puedeFinalizar(filas: FilaPlan[]): boolean {
  return filas.length > 0 && filas.every((fila) => fila.completo)
}

export function pedidosDisponibles(confirmados: PedidoResumen[], asociados: PedidoResumen[]): PedidoResumen[] {
  const ids = new Set(asociados.map((pedido) => pedido.idPedido))
  return confirmados.filter((pedido) => pedido.estado === 'CONFIRMADO' && !ids.has(pedido.idPedido))
}

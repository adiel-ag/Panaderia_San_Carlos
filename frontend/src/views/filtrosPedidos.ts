import { ESTADOS, type Estado, type FiltrosApi } from '../api/pedidos'
import { finDeSemana, sumarDias } from '../api/fechas'
import type { LocationQuery, LocationQueryRaw } from 'vue-router'

export const ENTREGAS = ['desde-hoy', 'hoy', 'manana', 'esta-semana', 'todas'] as const
export type Entrega = typeof ENTREGAS[number]
export interface FiltrosPedidos { entrega: Entrega; estado: Estado | null; sucursal: number | null; pagina: number }

function simple(valor: LocationQuery[string]): string | undefined {
  return typeof valor === 'string' ? valor : undefined
}
function enteroPositivo(valor: string | undefined): number | null {
  if (!valor || !/^[1-9]\d*$/.test(valor)) return null
  const numero = Number(valor)
  return Number.isSafeInteger(numero) ? numero : null
}
export function leerFiltros(query: LocationQuery): FiltrosPedidos {
  const entrega = simple(query.entrega)
  const estado = simple(query.estado)
  return {
    entrega: ENTREGAS.find((opcion) => opcion === entrega) ?? 'desde-hoy',
    estado: ESTADOS.find((opcion) => opcion === estado) ?? null,
    sucursal: enteroPositivo(simple(query.sucursal)),
    pagina: enteroPositivo(simple(query.pagina)) ?? 1,
  }
}
export function queryFiltros(filtros: FiltrosPedidos): LocationQueryRaw {
  return {
    ...(filtros.entrega !== 'desde-hoy' ? { entrega: filtros.entrega } : {}),
    ...(filtros.estado ? { estado: filtros.estado } : {}),
    ...(filtros.sucursal ? { sucursal: String(filtros.sucursal) } : {}),
    ...(filtros.pagina !== 1 ? { pagina: String(filtros.pagina) } : {}),
  }
}
export function consultaPedidos(filtros: FiltrosPedidos, hoy: string): FiltrosApi {
  const manana = sumarDias(hoy, 1)
  return {
    estado: filtros.estado ?? undefined, idSucursal: filtros.sucursal ?? undefined,
    entregaDesde: filtros.entrega === 'todas' ? undefined : filtros.entrega === 'manana' ? manana : hoy,
    entregaHasta: filtros.entrega === 'hoy' ? hoy : filtros.entrega === 'manana' ? manana
      : filtros.entrega === 'esta-semana' ? finDeSemana(hoy) : undefined,
  }
}

/**
 * Resumen del día: los cancelados no se entregan y todo lo que no ha salido
 * (pendiente, confirmado, en producción o listo) cuenta como por despachar.
 */
export function textoResumenHoy(total: number, cancelados: number, despachados: number): string {
  const aEntregar = total - cancelados
  if (aEntregar <= 0) return 'Sin entregas para hoy'
  const porDespachar = aEntregar - despachados
  const pedidos = `${aEntregar} ${aEntregar === 1 ? 'pedido' : 'pedidos'} para entregar hoy`
  return porDespachar === 0 ? `${pedidos} · todos despachados` : `${pedidos} · ${porDespachar} por despachar`
}

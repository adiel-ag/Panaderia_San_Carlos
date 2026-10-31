import type { LocationQuery, LocationQueryRaw } from 'vue-router'
import { sumarDias } from '../api/fechas'
import type { FiltrosApi } from '../api/pedidos'

export const VISTAS = ['listos', 'despachados'] as const
export type Vista = typeof VISTAS[number]
export interface FiltrosDespacho { vista: Vista; sucursal: number | null; pagina: number }
export type MarcaEntrega = 'atrasado' | 'hoy' | 'manana'

function simple(valor: LocationQuery[string]): string | undefined {
  return typeof valor === 'string' ? valor : undefined
}
function enteroPositivo(valor: string | undefined): number | null {
  if (!valor || !/^[1-9]\d*$/.test(valor)) return null
  const numero = Number(valor)
  return Number.isSafeInteger(numero) ? numero : null
}
export function leerFiltrosDespacho(query: LocationQuery): FiltrosDespacho {
  const vista = simple(query.vista)
  return {
    vista: VISTAS.find((opcion) => opcion === vista) ?? 'listos',
    sucursal: enteroPositivo(simple(query.sucursal)),
    pagina: enteroPositivo(simple(query.pagina)) ?? 1,
  }
}
export function queryFiltrosDespacho(filtros: FiltrosDespacho): LocationQueryRaw {
  return {
    ...(filtros.vista !== 'listos' ? { vista: filtros.vista } : {}),
    ...(filtros.sucursal ? { sucursal: String(filtros.sucursal) } : {}),
    ...(filtros.pagina !== 1 ? { pagina: String(filtros.pagina) } : {}),
  }
}

// Despachados: la última semana por fecha de entrega.
export function consultaDespacho(filtros: FiltrosDespacho, hoy: string): FiltrosApi {
  const idSucursal = filtros.sucursal ?? undefined
  return filtros.vista === 'listos'
    ? { estado: 'LISTO_DESPACHO', idSucursal }
    : { estado: 'DESPACHADO', idSucursal, entregaDesde: sumarDias(hoy, -7) }
}

// Las fechas llegan como YYYY-MM-DD: se comparan como texto, sin zona horaria.
export function marcaEntrega(fechaEntrega: string, hoy: string): MarcaEntrega | null {
  const fecha = fechaEntrega.slice(0, 10)
  if (fecha < hoy) return 'atrasado'
  if (fecha === hoy) return 'hoy'
  return fecha === sumarDias(hoy, 1) ? 'manana' : null
}

export function textoResumenDespacho(listos: number, atrasados: number): string {
  if (listos <= 0) return 'No hay pedidos listos para despachar'
  const texto = `${listos} ${listos === 1 ? 'pedido listo' : 'pedidos listos'} para despachar`
  return atrasados > 0 ? `${texto} · ${atrasados} ${atrasados === 1 ? 'atrasado' : 'atrasados'}` : texto
}

export function validarObservacion(observacion: string): string | null {
  return observacion.trim().length > 500 ? 'La observación admite hasta 500 caracteres.' : null
}

import type { LocationQuery, LocationQueryRaw } from 'vue-router'
import { hoyLocal } from '../api/fechas'
import type { ConsumoInsumoReporte, PedidosSucursalReporte, ProduccionDiaReporte } from '../api/reportes'

export const REPORTES = ['pedidos-por-sucursal', 'produccion-por-dia', 'consumo-insumos'] as const
export type Reporte = typeof REPORTES[number]
export const ETIQUETAS_REPORTE: Record<Reporte, string> = {
  'pedidos-por-sucursal': 'Pedidos por sucursal',
  'produccion-por-dia': 'Producción por día',
  'consumo-insumos': 'Consumo de insumos',
}
export const MAXIMO_DIAS = 366
export interface FiltrosReporte { reporte: Reporte; desde: string; hasta: string }
export type Celda = string | number | null | undefined

function simple(valor: LocationQuery[string]): string | undefined {
  return typeof valor === 'string' ? valor : undefined
}
function fechaValida(valor: string | undefined): valor is string {
  if (!valor || !/^\d{4}-\d{2}-\d{2}$/.test(valor)) return false
  const [anio = 0, mes = 0, dia = 0] = valor.split('-').map(Number)
  const fecha = new Date(Date.UTC(anio, mes - 1, dia))
  return fecha.getUTCFullYear() === anio && fecha.getUTCMonth() === mes - 1 && fecha.getUTCDate() === dia
}
function diasEntre(desde: string, hasta: string): number {
  const [a1 = 0, m1 = 1, d1 = 1] = desde.split('-').map(Number)
  const [a2 = 0, m2 = 1, d2 = 1] = hasta.split('-').map(Number)
  return Math.round((Date.UTC(a2, m2 - 1, d2) - Date.UTC(a1, m1 - 1, d1)) / 86400000)
}

/** Del primer día del mes en curso hasta hoy, en fechas locales. */
export function rangoPorDefecto(hoy = hoyLocal()): { desde: string; hasta: string } {
  return { desde: `${hoy.slice(0, 8)}01`, hasta: hoy }
}

export function leerFiltrosReporte(query: LocationQuery, hoy = hoyLocal()): FiltrosReporte {
  const reporte = simple(query.reporte)
  const porDefecto = rangoPorDefecto(hoy)
  const desde = simple(query.desde)
  const hasta = simple(query.hasta)
  return {
    reporte: REPORTES.find((opcion) => opcion === reporte) ?? 'pedidos-por-sucursal',
    desde: fechaValida(desde) ? desde : porDefecto.desde,
    hasta: fechaValida(hasta) ? hasta : porDefecto.hasta,
  }
}
export function queryFiltrosReporte(filtros: FiltrosReporte): LocationQueryRaw {
  return { reporte: filtros.reporte, desde: filtros.desde, hasta: filtros.hasta }
}

/** Mismas reglas que el servidor; null si el rango es válido. */
export function validarRango(desde: string, hasta: string): string | null {
  if (!fechaValida(desde) || !fechaValida(hasta)) return 'Ingrese ambas fechas.'
  if (desde > hasta) return 'La fecha inicial no puede ser posterior a la final.'
  if (diasEntre(desde, hasta) + 1 > MAXIMO_DIAS) return `El rango no puede superar ${MAXIMO_DIAS} días.`
  return null
}

function celdaCsv(valor: Celda): string {
  const texto = valor === null || valor === undefined ? '' : String(valor)
  return /[";\r\n]/.test(texto) ? `"${texto.replaceAll('"', '""')}"` : texto
}
/** CSV con ';' (Excel en español) y BOM para que respete los acentos. */
export function construirCsv(filas: readonly (readonly Celda[])[]): string {
  return `﻿${filas.map((fila) => fila.map(celdaCsv).join(';')).join('\r\n')}\r\n`
}
export function nombreArchivoCsv(reporte: Reporte, desde: string, hasta: string): string {
  return `${reporte}_${desde}_${hasta}.csv`
}

export function filasPedidosPorSucursal(datos: readonly PedidosSucursalReporte[]): Celda[][] {
  const filas: Celda[][] = [['Sucursal', 'Pedidos', 'Despachados', 'Pendientes', 'Producto', 'Cantidad', 'Unidad']]
  for (const sucursal of datos) {
    if (!sucursal.productos.length) filas.push([sucursal.sucursal, sucursal.pedidos, sucursal.despachados, sucursal.pendientes, '', '', ''])
    for (const producto of sucursal.productos) {
      filas.push([sucursal.sucursal, sucursal.pedidos, sucursal.despachados, sucursal.pendientes, producto.producto, producto.cantidad, producto.unidad])
    }
  }
  return filas
}
export function filasProduccionPorDia(datos: readonly ProduccionDiaReporte[]): Celda[][] {
  const filas: Celda[][] = [['Fecha', 'Producciones', 'Producto', 'Planificado', 'Producido', 'Unidad']]
  for (const dia of datos) {
    if (!dia.productos.length) filas.push([dia.fecha, dia.producciones, '', '', '', ''])
    for (const producto of dia.productos) {
      filas.push([dia.fecha, dia.producciones, producto.producto, producto.planificado, producto.producido, producto.unidad])
    }
  }
  return filas
}
export function filasConsumoInsumos(datos: readonly ConsumoInsumoReporte[]): Celda[][] {
  return [['Insumo', 'Consumido', 'Unidad'], ...datos.map((item) => [item.insumo, item.consumido, item.unidad])]
}

export function totalesPedidos(datos: readonly PedidosSucursalReporte[]): { pedidos: number; despachados: number; pendientes: number } {
  return datos.reduce((total, item) => ({
    pedidos: total.pedidos + item.pedidos, despachados: total.despachados + item.despachados, pendientes: total.pendientes + item.pendientes,
  }), { pedidos: 0, despachados: 0, pendientes: 0 })
}
export function totalProducciones(datos: readonly ProduccionDiaReporte[]): number {
  return datos.reduce((total, dia) => total + dia.producciones, 0)
}
/** Ancho relativo de cada barra respecto al mayor consumo (0..100). */
export function anchoBarra(valor: number, maximo: number): number {
  return maximo > 0 ? Math.max(0, Math.min(100, (valor / maximo) * 100)) : 0
}

export function descargarCsv(nombre: string, contenido: string): void {
  const enlace = document.createElement('a')
  const url = URL.createObjectURL(new Blob([contenido], { type: 'text/csv;charset=utf-8' }))
  enlace.href = url
  enlace.download = nombre
  document.body.appendChild(enlace)
  enlace.click()
  enlace.remove()
  URL.revokeObjectURL(url)
}

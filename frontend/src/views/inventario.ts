import type { LocationQuery, LocationQueryRaw } from 'vue-router'
import {
  TIPOS_PRODUCTO, type ExistenciaResumen, type FiltrosInventarioApi, type TipoMovimiento, type TipoProducto,
} from '../api/inventario'
import { validarObservacion } from './despacho'
import { validarCantidad } from './produccion'

export interface FiltrosInventario { tipo: TipoProducto | null; nombre: string; bajoMinimo: boolean; pagina: number }
export type EstadoExistencia = 'sin-inventario' | 'bajo-minimo' | 'suficiente'
export interface ErroresMovimiento { cantidad: string | null; observacion: string | null }

export const LARGO_BUSQUEDA = 100

function simple(valor: LocationQuery[string]): string | undefined {
  return typeof valor === 'string' ? valor : undefined
}
function enteroPositivo(valor: string | undefined): number | null {
  if (!valor || !/^[1-9]\d*$/.test(valor)) return null
  const numero = Number(valor)
  return Number.isSafeInteger(numero) ? numero : null
}
export function normalizarBusqueda(texto: string): string {
  return texto.trim().slice(0, LARGO_BUSQUEDA).trim()
}
export function leerFiltrosInventario(query: LocationQuery): FiltrosInventario {
  const tipo = simple(query.tipo)
  return {
    tipo: TIPOS_PRODUCTO.find((opcion) => opcion === tipo) ?? null,
    nombre: normalizarBusqueda(simple(query.q) ?? ''),
    bajoMinimo: simple(query.bajo) === '1',
    pagina: enteroPositivo(simple(query.pagina)) ?? 1,
  }
}
export function queryFiltrosInventario(filtros: FiltrosInventario): LocationQueryRaw {
  return {
    ...(filtros.tipo ? { tipo: filtros.tipo } : {}),
    ...(filtros.nombre ? { q: filtros.nombre } : {}),
    ...(filtros.bajoMinimo ? { bajo: '1' } : {}),
    ...(filtros.pagina !== 1 ? { pagina: String(filtros.pagina) } : {}),
  }
}
// Cambiar un filtro siempre vuelve a la primera página.
export function cambiarFiltros(filtros: FiltrosInventario, cambio: Partial<Omit<FiltrosInventario, 'pagina'>>): FiltrosInventario {
  return { ...filtros, ...cambio, pagina: 1 }
}
export function consultaInventario(filtros: FiltrosInventario): FiltrosInventarioApi {
  return {
    tipo: filtros.tipo ?? undefined,
    nombre: filtros.nombre || undefined,
    bajoMinimo: filtros.bajoMinimo ? true : undefined,
  }
}
export function filtrosPorDefecto(filtros: FiltrosInventario): boolean {
  return filtros.tipo === null && filtros.nombre === '' && !filtros.bajoMinimo
}

export function etiquetaTipo(tipo: TipoProducto): string {
  return tipo === 'INSUMO' ? 'Insumo' : 'Producto final'
}

export function estadoExistencia(item: Pick<ExistenciaResumen, 'existencia' | 'bajoMinimo'>): EstadoExistencia {
  if (item.existencia === null) return 'sin-inventario'
  return item.bajoMinimo ? 'bajo-minimo' : 'suficiente'
}

export function textoResumenInventario(bajoMinimo: number): string {
  if (bajoMinimo <= 0) return 'Ningún producto bajo el mínimo'
  return `${bajoMinimo} ${bajoMinimo === 1 ? 'producto' : 'productos'} bajo el mínimo`
}

// Se calcula en milésimas para no arrastrar errores de punto flotante.
function milesimas(valor: number): number { return Math.round(valor * 1000) }

export function existenciaResultante(tipo: TipoMovimiento, actual: number | null, cantidad: number | null): number | null {
  if (cantidad === null || !Number.isFinite(cantidad)) return null
  if (tipo === 'entrada') return (milesimas(actual ?? 0) + milesimas(cantidad)) / 1000
  return actual === null ? null : (milesimas(actual) - milesimas(cantidad)) / 1000
}

const formatoCantidad = new Intl.NumberFormat('es-GT', { maximumFractionDigits: 3 })
export function formatearCantidad(valor: number, unidad = ''): string {
  return unidad ? `${formatoCantidad.format(valor)} ${unidad}` : formatoCantidad.format(valor)
}

export function validarMovimiento(
  tipo: TipoMovimiento, actual: number | null, cantidad: number | null, observacion: string, unidad = '',
): ErroresMovimiento {
  let errorCantidad = validarCantidad(cantidad)
  if (!errorCantidad && tipo === 'salida' && cantidad !== null) {
    if (actual === null) errorCantidad = 'El producto no tiene inventario registrado.'
    else if (milesimas(cantidad) > milesimas(actual)) {
      errorCantidad = `No hay existencias suficientes: hay ${formatearCantidad(actual, unidad)}.`
    }
  }
  return { cantidad: errorCantidad, observacion: validarObservacion(observacion) }
}

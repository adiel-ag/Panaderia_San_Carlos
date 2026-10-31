import { api } from './cliente'
import type { Pagina } from './tipos'

export const TIPOS_PRODUCTO = ['INSUMO', 'PRODUCTO_FINAL'] as const
export type TipoProducto = typeof TIPOS_PRODUCTO[number]
export type TipoMovimiento = 'entrada' | 'salida'

// existencia null: el producto aún no tiene fila de inventario (distinto de 0).
export interface ExistenciaResumen {
  idProducto: number
  nombre: string
  tipo: TipoProducto
  unidad: string
  existencia: number | null
  stockMinimo: number
  bajoMinimo: boolean | null
}
export interface FiltrosInventarioApi {
  tipo?: TipoProducto
  nombre?: string
  bajoMinimo?: boolean
}
export interface ResultadoMovimiento { idProducto: number; existencia: number }

export function listarExistencias(filtros: FiltrosInventarioApi, pagina = 1, tamano = 20, avisar = true): Promise<Pagina<ExistenciaResumen>> {
  return api('inventario/existencias', { consulta: { pagina, tamano, ...filtros }, avisar })
}
export function registrarMovimiento(tipo: TipoMovimiento, idProducto: number, cantidad: number, observacion: string | null): Promise<ResultadoMovimiento> {
  return api(`inventario/productos/${idProducto}/${tipo === 'entrada' ? 'entradas' : 'salidas'}`, {
    metodo: 'POST', cuerpo: { cantidad, observacion }, avisar: true,
  })
}

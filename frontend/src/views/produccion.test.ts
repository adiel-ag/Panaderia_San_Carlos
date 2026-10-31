import { describe, expect, it } from 'vitest'
import type { PedidoResumen, Producto } from '../api/pedidos'
import {
  filasPlan, leerFiltrosProduccion, pedidosDisponibles, puedeFinalizar, queryFiltrosProduccion, textoResumenProduccion, validarCantidad,
} from './produccion'

const productos: Producto[] = [
  { idProducto: 9, nombre: 'Pan francés', nombreTipoProducto: 'PRODUCTO_FINAL', abreviaturaUnidadMedida: 'UND', nombreUnidadMedida: 'Unidad', estado: 'ACTIVO' },
  { idProducto: 10, nombre: 'Pastel', nombreTipoProducto: 'PRODUCTO_FINAL', abreviaturaUnidadMedida: 'KG', nombreUnidadMedida: 'Kilogramo', estado: 'ACTIVO' },
]
function pedido(idPedido: number, estado: PedidoResumen['estado'] = 'CONFIRMADO'): PedidoResumen {
  return { idPedido, idSucursal: 3, nombreSucursal: 'Centro', idUsuario: 1, nombreUsuario: 'Ana', fechaPedido: '2026-10-31T08:00:00',
    fechaEntrega: '2026-11-01', estado, observacion: null, cantidadProductos: 2 }
}
function linea(idProducto: number, cantidadPlanificada: number, cantidadProducida: number) {
  return { idProduccionDetalle: idProducto, idProduccion: 5, idProducto, idReceta: 1, cantidadPlanificada, cantidadProducida }
}

describe('filtros de producción', () => {
  it('usa valores por defecto para query inválida', () => {
    expect(leerFiltrosProduccion({ estado: 'OTRO', pagina: '0' })).toEqual({ estado: null, pagina: 1 })
    expect(leerFiltrosProduccion({ estado: ['EN_PROCESO'], pagina: 'x' })).toEqual({ estado: null, pagina: 1 })
  })
  it('restaura filtros válidos y omite los valores por defecto', () => {
    const filtros = leerFiltrosProduccion({ estado: 'EN_PROCESO', pagina: '2' })
    expect(filtros).toEqual({ estado: 'EN_PROCESO', pagina: 2 })
    expect(queryFiltrosProduccion(filtros)).toEqual({ estado: 'EN_PROCESO', pagina: '2' })
    expect(queryFiltrosProduccion({ estado: null, pagina: 1 })).toEqual({})
  })
})

describe('textoResumenProduccion', () => {
  it('cuenta en proceso y planificadas con singular y plural', () => {
    expect(textoResumenProduccion(2, 3)).toBe('2 en proceso · 3 planificadas')
    expect(textoResumenProduccion(1, 1)).toBe('1 en proceso · 1 planificada')
    expect(textoResumenProduccion(0, 2)).toBe('0 en proceso · 2 planificadas')
  })
  it('sin producciones activas cuando ambas son 0', () => {
    expect(textoResumenProduccion(0, 0)).toBe('Sin producciones activas')
  })
})

describe('validarCantidad', () => {
  it.each([1, 0.001, 2.5, 999999999.999])('acepta %s', (valor) => { expect(validarCantidad(valor)).toBeNull() })
  it.each([null, 0, -1, 1.0005, 1000000000, Number.NaN])('rechaza %s', (valor) => { expect(validarCantidad(valor)).not.toBeNull() })
})

describe('filasPlan', () => {
  it('nombra productos, convierte unidades y calcula faltante', () => {
    const filas = filasPlan([linea(9, 10, 4), linea(10, 2.5, 2.5), linea(11, 1, 0)], productos)
    expect(filas).toEqual([
      { idProducto: 9, nombre: 'Pan francés', unidad: 'u', planificado: 10, producido: 4, faltante: 6, completo: false },
      { idProducto: 10, nombre: 'Pastel', unidad: 'kg', planificado: 2.5, producido: 2.5, faltante: null, completo: true },
      { idProducto: 11, nombre: 'Producto #11', unidad: '', planificado: 1, producido: 0, faltante: 1, completo: false },
    ])
  })
  it('evita errores de punto flotante y trata el excedente como completo', () => {
    expect(filasPlan([linea(9, 0.3, 0.1 + 0.2)], productos)[0]).toMatchObject({ faltante: null, completo: true })
    expect(filasPlan([linea(9, 1.2, 0.1)], productos)[0]!.faltante).toBe(1.1)
    expect(filasPlan([linea(9, 2, 3)], productos)[0]).toMatchObject({ faltante: null, completo: true })
  })
})

describe('puedeFinalizar', () => {
  it('exige filas y que todas estén completas', () => {
    expect(puedeFinalizar([])).toBe(false)
    expect(puedeFinalizar(filasPlan([linea(9, 1, 1), linea(10, 1, 0.5)], productos))).toBe(false)
    expect(puedeFinalizar(filasPlan([linea(9, 1, 1), linea(10, 1, 2)], productos))).toBe(true)
  })
})

describe('pedidosDisponibles', () => {
  it('quita los ya asociados y los que no están confirmados', () => {
    const disponibles = pedidosDisponibles([pedido(1), pedido(2), pedido(3, 'PENDIENTE')], [pedido(1)])
    expect(disponibles.map((item) => item.idPedido)).toEqual([2])
  })
})

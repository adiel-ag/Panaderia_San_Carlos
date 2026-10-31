import { describe, expect, it } from 'vitest'
import { ROLES } from '../api/tipos'
import { filasDesdePedidos, filasDesdePlan, puedeImprimirDespacho, puedeImprimirProduccion } from './hojas'

const productos = [
  { idProducto: 9, nombre: 'Pan francés', nombreTipoProducto: 'PRODUCTO_FINAL', abreviaturaUnidadMedida: 'UND', nombreUnidadMedida: 'Unidad', estado: 'ACTIVO' },
  { idProducto: 10, nombre: 'Baguette', nombreTipoProducto: 'PRODUCTO_FINAL', abreviaturaUnidadMedida: 'KG', nombreUnidadMedida: 'Kilogramo', estado: 'ACTIVO' },
]

describe('permisos de impresión', () => {
  it('la hoja de despacho es para Administración, Despacho y Gerencia en pedidos listos o despachados', () => {
    const permitidos = ROLES.filter((rol) => puedeImprimirDespacho(rol, 'LISTO_DESPACHO'))
    expect(permitidos).toEqual(['ADMINISTRADOR', 'GERENTE', 'DESPACHO'])
    expect(puedeImprimirDespacho('DESPACHO', 'DESPACHADO')).toBe(true)
    expect(puedeImprimirDespacho('DESPACHO', 'EN_PRODUCCION')).toBe(false)
    expect(puedeImprimirDespacho(null, 'DESPACHADO')).toBe(false)
  })

  it('la hoja de producción exige proceso o planificada con pedidos', () => {
    expect(ROLES.filter((rol) => puedeImprimirProduccion(rol, 'EN_PROCESO', 0))).toEqual(['ADMINISTRADOR', 'GERENTE', 'PRODUCCION'])
    expect(puedeImprimirProduccion('PRODUCCION', 'PLANIFICADA', 2)).toBe(true)
    expect(puedeImprimirProduccion('PRODUCCION', 'PLANIFICADA', 0)).toBe(false)
    expect(puedeImprimirProduccion('GERENTE', 'FINALIZADA', 2)).toBe(false)
  })
})

describe('filas de la hoja de producción', () => {
  it('suma por producto lo pedido en una producción planificada', () => {
    const pedido = (idPedido: number, cantidades: [number, string, number][]) => ({
      idPedido, idSucursal: 1, idUsuario: 1, fechaEntrega: '2026-11-01', estado: 'CONFIRMADO' as const, observacion: null,
      detalles: cantidades.map(([idProducto, nombreProducto, cantidad], i) => ({ idPedidoDetalle: idPedido * 10 + i, idPedido, idProducto, nombreProducto, cantidad })),
    })
    expect(filasDesdePedidos([pedido(1, [[9, 'Pan francés', 10], [10, 'Baguette', 0.1]]), pedido(2, [[9, 'Pan francés', 5], [10, 'Baguette', 0.2]])], productos)).toEqual([
      { idProducto: 10, nombre: 'Baguette', unidad: 'KG', planificado: 0.3 },
      { idProducto: 9, nombre: 'Pan francés', unidad: 'UND', planificado: 15 },
    ])
  })

  it('toma lo planificado del plan en proceso', () => {
    expect(filasDesdePlan([{ idProduccionDetalle: 1, idProduccion: 5, idProducto: 9, idReceta: 2, cantidadPlanificada: 12, cantidadProducida: 0 },
      { idProduccionDetalle: 2, idProduccion: 5, idProducto: 77, idReceta: 3, cantidadPlanificada: 1, cantidadProducida: 0 }], productos)).toEqual([
      { idProducto: 9, nombre: 'Pan francés', unidad: 'UND', planificado: 12 },
      { idProducto: 77, nombre: 'Producto #77', unidad: '', planificado: 1 },
    ])
  })
})

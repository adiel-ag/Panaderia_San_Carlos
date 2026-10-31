import { describe, expect, it } from 'vitest'
import {
  anchoBarra, construirCsv, filasConsumoInsumos, filasPedidosPorSucursal, filasProduccionPorDia, leerFiltrosReporte,
  nombreArchivoCsv, queryFiltrosReporte, rangoPorDefecto, totalProducciones, totalesPedidos, validarRango,
} from './reportes'

describe('rango de fechas de reportes', () => {
  it('por defecto va del primer día del mes a hoy', () => {
    expect(rangoPorDefecto('2026-10-31')).toEqual({ desde: '2026-10-01', hasta: '2026-10-31' })
    expect(rangoPorDefecto('2026-02-01')).toEqual({ desde: '2026-02-01', hasta: '2026-02-01' })
  })

  it('lee reporte y fechas de la URL y descarta valores inválidos', () => {
    expect(leerFiltrosReporte({}, '2026-10-15')).toEqual({ reporte: 'pedidos-por-sucursal', desde: '2026-10-01', hasta: '2026-10-15' })
    expect(leerFiltrosReporte({ reporte: 'consumo-insumos', desde: '2026-09-01', hasta: '2026-09-30' }, '2026-10-15'))
      .toEqual({ reporte: 'consumo-insumos', desde: '2026-09-01', hasta: '2026-09-30' })
    expect(leerFiltrosReporte({ reporte: 'otro', desde: '2026-02-30', hasta: 'ayer' }, '2026-10-15'))
      .toEqual({ reporte: 'pedidos-por-sucursal', desde: '2026-10-01', hasta: '2026-10-15' })
    expect(queryFiltrosReporte({ reporte: 'produccion-por-dia', desde: '2026-10-01', hasta: '2026-10-02' }))
      .toEqual({ reporte: 'produccion-por-dia', desde: '2026-10-01', hasta: '2026-10-02' })
  })

  it('valida orden, formato y máximo de 366 días inclusivos', () => {
    expect(validarRango('2026-10-01', '2026-10-01')).toBeNull()
    expect(validarRango('2026-01-01', '2027-01-01')).toBeNull()
    expect(validarRango('2026-01-01', '2027-01-02')).toContain('366')
    expect(validarRango('2026-10-31', '2026-10-01')).toContain('posterior')
    expect(validarRango('', '2026-10-01')).toBe('Ingrese ambas fechas.')
    expect(validarRango('2026-13-01', '2026-10-01')).toBe('Ingrese ambas fechas.')
  })
})

describe('CSV', () => {
  it('usa punto y coma, BOM UTF-8, CRLF y escapa comillas', () => {
    const csv = construirCsv([['Insumo', 'Consumido'], ['Harina "fina"; especial', 12.5], ['Azúcar', null]])
    expect(csv.charCodeAt(0)).toBe(0xFEFF)
    expect(csv.slice(1)).toBe('Insumo;Consumido\r\n"Harina ""fina""; especial";12.5\r\nAzúcar;\r\n')
  })

  it('nombra el archivo con el reporte y el rango', () => {
    expect(nombreArchivoCsv('pedidos-por-sucursal', '2026-10-01', '2026-10-31')).toBe('pedidos-por-sucursal_2026-10-01_2026-10-31.csv')
  })

  it('arma una fila por producto en cada reporte', () => {
    const pedidos = [
      { idSucursal: 1, sucursal: 'Centro', pedidos: 3, despachados: 1, pendientes: 2, productos: [
        { idProducto: 9, producto: 'Pan', unidad: 'UND', cantidad: 40 }, { idProducto: 10, producto: 'Pastel', unidad: 'KG', cantidad: 2.5 }] },
      { idSucursal: 2, sucursal: 'Norte', pedidos: 1, despachados: 1, pendientes: 0, productos: [] },
    ]
    expect(filasPedidosPorSucursal(pedidos)).toEqual([
      ['Sucursal', 'Pedidos', 'Despachados', 'Pendientes', 'Producto', 'Cantidad', 'Unidad'],
      ['Centro', 3, 1, 2, 'Pan', 40, 'UND'], ['Centro', 3, 1, 2, 'Pastel', 2.5, 'KG'], ['Norte', 1, 1, 0, '', '', ''],
    ])
    expect(totalesPedidos(pedidos)).toEqual({ pedidos: 4, despachados: 2, pendientes: 2 })
    const produccion = [{ fecha: '2026-10-05', producciones: 2, productos: [{ idProducto: 9, producto: 'Pan', unidad: 'UND', planificado: 50, producido: 50 }] }]
    expect(filasProduccionPorDia(produccion)[1]).toEqual(['2026-10-05', 2, 'Pan', 50, 50, 'UND'])
    expect(totalProducciones(produccion)).toBe(2)
    expect(filasConsumoInsumos([{ idInsumo: 2, insumo: 'Harina', unidad: 'KG', consumido: 12 }])).toEqual([['Insumo', 'Consumido', 'Unidad'], ['Harina', 12, 'KG']])
  })

  it('calcula el ancho de barra relativo al máximo', () => {
    expect(anchoBarra(5, 10)).toBe(50)
    expect(anchoBarra(10, 10)).toBe(100)
    expect(anchoBarra(3, 0)).toBe(0)
  })
})

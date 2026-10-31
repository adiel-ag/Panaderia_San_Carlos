import { describe, expect, it } from 'vitest'
import {
  cambiarFiltros, consultaInventario, estadoExistencia, etiquetaTipo, existenciaResultante, filtrosPorDefecto,
  leerFiltrosInventario, queryFiltrosInventario, textoResumenInventario, validarMovimiento,
} from './inventario'

const base = { tipo: null, nombre: '', bajoMinimo: false, pagina: 1 }

describe('filtros de inventario', () => {
  it('usa valores por defecto para query vacía o inválida', () => {
    expect(leerFiltrosInventario({})).toEqual(base)
    expect(leerFiltrosInventario({ tipo: 'OTRO', bajo: 'si', pagina: '0' })).toEqual(base)
    expect(leerFiltrosInventario({ tipo: ['INSUMO'], q: ['harina'], bajo: ['1'], pagina: '1.5' })).toEqual(base)
    expect(leerFiltrosInventario({ bajo: 'true', pagina: '-2' })).toEqual(base)
  })

  it('restaura filtros válidos y los vuelve a serializar', () => {
    const filtros = leerFiltrosInventario({ tipo: 'INSUMO', q: 'harina', bajo: '1', pagina: '2' })
    expect(filtros).toEqual({ tipo: 'INSUMO', nombre: 'harina', bajoMinimo: true, pagina: 2 })
    expect(queryFiltrosInventario(filtros)).toEqual({ tipo: 'INSUMO', q: 'harina', bajo: '1', pagina: '2' })
    expect(queryFiltrosInventario(base)).toEqual({})
  })

  it('recorta la búsqueda y la limita a 100 caracteres', () => {
    expect(leerFiltrosInventario({ q: '   pan dulce  ' }).nombre).toBe('pan dulce')
    expect(leerFiltrosInventario({ q: '   ' }).nombre).toBe('')
    expect(leerFiltrosInventario({ q: 'a'.repeat(150) }).nombre).toHaveLength(100)
  })

  it('vuelve a la página 1 al cambiar un filtro', () => {
    expect(cambiarFiltros({ ...base, pagina: 3 }, { tipo: 'PRODUCTO_FINAL' })).toEqual({ ...base, tipo: 'PRODUCTO_FINAL', pagina: 1 })
  })

  it('arma la consulta de la API sin parámetros vacíos', () => {
    expect(consultaInventario(base)).toEqual({ tipo: undefined, nombre: undefined, bajoMinimo: undefined })
    expect(consultaInventario({ tipo: 'INSUMO', nombre: '50%_x', bajoMinimo: true, pagina: 2 }))
      .toEqual({ tipo: 'INSUMO', nombre: '50%_x', bajoMinimo: true })
  })

  it('reconoce los filtros por defecto', () => {
    expect(filtrosPorDefecto(base)).toBe(true)
    expect(filtrosPorDefecto({ ...base, pagina: 4 })).toBe(true)
    expect(filtrosPorDefecto({ ...base, nombre: 'pan' })).toBe(false)
    expect(filtrosPorDefecto({ ...base, bajoMinimo: true })).toBe(false)
  })
})

describe('textos de inventario', () => {
  it('etiqueta los tipos', () => {
    expect(etiquetaTipo('INSUMO')).toBe('Insumo')
    expect(etiquetaTipo('PRODUCTO_FINAL')).toBe('Producto final')
  })

  it('resume los productos bajo el mínimo', () => {
    expect(textoResumenInventario(0)).toBe('Ningún producto bajo el mínimo')
    expect(textoResumenInventario(1)).toBe('1 producto bajo el mínimo')
    expect(textoResumenInventario(4)).toBe('4 productos bajo el mínimo')
  })

  it('distingue sin inventario, bajo el mínimo y suficiente', () => {
    expect(estadoExistencia({ existencia: null, bajoMinimo: null })).toBe('sin-inventario')
    expect(estadoExistencia({ existencia: 0, bajoMinimo: true })).toBe('bajo-minimo')
    expect(estadoExistencia({ existencia: 30, bajoMinimo: false })).toBe('suficiente')
  })
})

describe('movimientos de inventario', () => {
  it('calcula la existencia resultante en milésimas', () => {
    expect(existenciaResultante('entrada', 0.1, 0.2)).toBe(0.3)
    expect(existenciaResultante('salida', 0.3, 0.1)).toBe(0.2)
    expect(existenciaResultante('entrada', null, 5)).toBe(5)
    expect(existenciaResultante('salida', null, 5)).toBeNull()
    expect(existenciaResultante('salida', 2, 3)).toBe(-1)
    expect(existenciaResultante('entrada', 2, null)).toBeNull()
  })

  it('valida la cantidad', () => {
    const mensaje = 'Ingrese una cantidad mayor que 0, con hasta 3 decimales y máximo 999999999.999.'
    for (const cantidad of [null, 0, -1, 1.2345, 1000000000]) {
      expect(validarMovimiento('entrada', 10, cantidad, '').cantidad).toBe(mensaje)
    }
    expect(validarMovimiento('entrada', null, 2.125, '')).toEqual({ cantidad: null, observacion: null })
    expect(validarMovimiento('entrada', null, 999999999.999, '').cantidad).toBeNull()
  })

  it('exige inventario registrado y existencias suficientes para una salida', () => {
    expect(validarMovimiento('salida', null, 1, '').cantidad).toBe('El producto no tiene inventario registrado.')
    expect(validarMovimiento('salida', 2.5, 3, '', 'kg').cantidad).toBe('No hay existencias suficientes: hay 2.5 kg.')
    expect(validarMovimiento('salida', 0.3, 0.1 + 0.2, '').cantidad).toBeNull()
    expect(validarMovimiento('salida', 10, 10, '', 'u').cantidad).toBeNull()
  })

  it('limita la observación a 500 caracteres', () => {
    expect(validarMovimiento('entrada', 1, 1, 'a'.repeat(500)).observacion).toBeNull()
    expect(validarMovimiento('entrada', 1, 1, 'a'.repeat(501)).observacion).toBe('La observación admite hasta 500 caracteres.')
  })
})

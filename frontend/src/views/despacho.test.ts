import { afterEach, describe, expect, it, vi } from 'vitest'
import { hoyLocal } from '../api/fechas'
import { consultaDespacho, leerFiltrosDespacho, marcaEntrega, queryFiltrosDespacho, textoResumenDespacho, validarObservacion } from './despacho'

afterEach(() => { vi.useRealTimers() })

describe('filtros de despacho', () => {
  it('usa valores por defecto para query vacía o inválida', () => {
    expect(leerFiltrosDespacho({})).toEqual({ vista: 'listos', sucursal: null, pagina: 1 })
    expect(leerFiltrosDespacho({ vista: 'otra', sucursal: '-2', pagina: '0' })).toEqual({ vista: 'listos', sucursal: null, pagina: 1 })
    expect(leerFiltrosDespacho({ vista: ['despachados'], pagina: '1.5' })).toEqual({ vista: 'listos', sucursal: null, pagina: 1 })
  })

  it('restaura filtros válidos y los vuelve a serializar', () => {
    const filtros = leerFiltrosDespacho({ vista: 'despachados', sucursal: '2', pagina: '2' })
    expect(filtros).toEqual({ vista: 'despachados', sucursal: 2, pagina: 2 })
    expect(queryFiltrosDespacho(filtros)).toEqual({ vista: 'despachados', sucursal: '2', pagina: '2' })
    expect(queryFiltrosDespacho({ vista: 'listos', sucursal: null, pagina: 1 })).toEqual({})
  })

  it('consulta los listos para despachar con la sucursal elegida', () => {
    expect(consultaDespacho({ vista: 'listos', sucursal: 3, pagina: 1 }, '2026-10-31')).toEqual({ estado: 'LISTO_DESPACHO', idSucursal: 3 })
    expect(consultaDespacho({ vista: 'listos', sucursal: null, pagina: 1 }, '2026-10-31')).toEqual({ estado: 'LISTO_DESPACHO', idSucursal: undefined })
  })

  it('consulta los despachados de los últimos 7 días', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 10, 3, 9))
    expect(consultaDespacho({ vista: 'despachados', sucursal: null, pagina: 1 }, hoyLocal())).toEqual({
      estado: 'DESPACHADO', idSucursal: undefined, entregaDesde: '2026-10-27',
    })
  })
})

describe('marcaEntrega', () => {
  it.each([
    ['2026-10-30', 'atrasado'], ['2026-10-31', 'hoy'], ['2026-11-01', 'manana'], ['2026-11-02', null],
  ] as const)('marca %s como %s', (fecha, marca) => {
    expect(marcaEntrega(fecha, '2026-10-31')).toBe(marca)
  })

  it('usa la fecha local cerca de la medianoche', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 9, 31, 23, 45))
    expect(marcaEntrega('2026-10-31', hoyLocal())).toBe('hoy')
    expect(marcaEntrega('2026-10-30', hoyLocal())).toBe('atrasado')
    expect(marcaEntrega('2026-11-01', hoyLocal())).toBe('manana')
  })
})

describe('textoResumenDespacho', () => {
  it('usa plural y singular', () => {
    expect(textoResumenDespacho(5, 2)).toBe('5 pedidos listos para despachar · 2 atrasados')
    expect(textoResumenDespacho(1, 1)).toBe('1 pedido listo para despachar · 1 atrasado')
  })
  it('omite los atrasados cuando no hay', () => {
    expect(textoResumenDespacho(3, 0)).toBe('3 pedidos listos para despachar')
  })
  it('avisa cuando no hay pedidos listos', () => {
    expect(textoResumenDespacho(0, 0)).toBe('No hay pedidos listos para despachar')
  })
})

describe('validarObservacion', () => {
  it('acepta vacía y hasta 500 caracteres', () => {
    expect(validarObservacion('')).toBeNull()
    expect(validarObservacion('a'.repeat(500))).toBeNull()
  })
  it('rechaza más de 500 caracteres', () => {
    expect(validarObservacion('a'.repeat(501))).toBe('La observación admite hasta 500 caracteres.')
  })
})

import { describe, expect, it } from 'vitest'
import { consultaPedidos, leerFiltros, queryFiltros, textoResumenHoy } from './filtrosPedidos'

describe('filtros de pedidos', () => {
  it('usa valores por defecto para query inválida', () => {
    expect(leerFiltros({ entrega: 'otro', estado: 'OTRO', sucursal: '-2', pagina: '0' })).toEqual({
      entrega: 'desde-hoy', estado: null, sucursal: null, pagina: 1,
    })
  })

  it('restaura filtros válidos y los convierte a la consulta del servidor', () => {
    const filtros = leerFiltros({ entrega: 'esta-semana', estado: 'PENDIENTE', sucursal: '3', pagina: '2' })
    expect(queryFiltros(filtros)).toEqual({ entrega: 'esta-semana', estado: 'PENDIENTE', sucursal: '3', pagina: '2' })
    expect(consultaPedidos(filtros, '2026-10-31')).toEqual({
      estado: 'PENDIENTE', idSucursal: 3, entregaDesde: '2026-10-31', entregaHasta: '2026-11-01',
    })
  })

  it.each([
    ['desde-hoy', '2026-10-31', undefined], ['hoy', '2026-10-31', '2026-10-31'],
    ['manana', '2026-11-01', '2026-11-01'], ['esta-semana', '2026-10-31', '2026-11-01'],
    ['todas', undefined, undefined],
  ] as const)('aplica entrega %s', (entrega, desde, hasta) => {
    expect(consultaPedidos({ entrega, estado: null, sucursal: null, pagina: 1 }, '2026-10-31')).toEqual({
      estado: undefined, idSucursal: undefined, entregaDesde: desde, entregaHasta: hasta,
    })
  })
})

describe('textoResumenHoy', () => {
  it('excluye los cancelados y cuenta todo lo que no ha salido como por despachar', () => {
    // 6 del día: 1 cancelado, 2 despachados, el resto pendiente/confirmado/en producción/listo.
    expect(textoResumenHoy(6, 1, 2)).toBe('5 pedidos para entregar hoy · 3 por despachar')
  })
  it('usa singular y reconoce cuando todo salió', () => {
    expect(textoResumenHoy(1, 0, 0)).toBe('1 pedido para entregar hoy · 1 por despachar')
    expect(textoResumenHoy(3, 0, 3)).toBe('3 pedidos para entregar hoy · todos despachados')
  })
  it('sin entregas cuando no hay pedidos o todos están cancelados', () => {
    expect(textoResumenHoy(0, 0, 0)).toBe('Sin entregas para hoy')
    expect(textoResumenHoy(2, 2, 0)).toBe('Sin entregas para hoy')
  })
})

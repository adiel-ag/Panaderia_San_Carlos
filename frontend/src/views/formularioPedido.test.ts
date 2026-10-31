import { describe, expect, it } from 'vitest'
import { construirSolicitud, hayCambios, productosPedibles, validarPedido, type EstadoFormularioPedido } from './formularioPedido'
import type { Producto } from '../api/pedidos'

const producto = (idProducto: number, nombre: string, nombreTipoProducto = 'PRODUCTO_FINAL', estado = 'ACTIVO'): Producto => ({
  idProducto, nombre, nombreTipoProducto, estado, abreviaturaUnidadMedida: 'UND', nombreUnidadMedida: 'Unidad',
})
const base = (): EstadoFormularioPedido => ({
  idSucursal: 3, fechaEntrega: '2026-11-01', observacion: '',
  lineas: [{ clave: 1, idProducto: 9, cantidad: 1 }],
})

describe('formularioPedido', () => {
  it('filtra productos finales activos y los ordena por nombre en español', () => {
    expect(productosPedibles([
      producto(1, 'Zeta'), producto(2, 'Árbol'), producto(3, 'Insumo', 'INSUMO'), producto(4, 'Inactivo', 'PRODUCTO_FINAL', 'INACTIVO'), producto(5, 'Abasto'),
    ]).map((p) => p.idProducto)).toEqual([5, 2, 1])
  })

  it('exige sucursal y fecha futura o de hoy', () => {
    const estado: EstadoFormularioPedido = { ...base(), idSucursal: null, fechaEntrega: '' }
    expect(validarPedido(estado, '2026-10-31')).toMatchObject({ sucursal: expect.any(String), fechaEntrega: expect.any(String) })
    estado.fechaEntrega = '2026-10-30'
    expect(validarPedido(estado, '2026-10-31').fechaEntrega).toMatch(/pasado/)
    estado.idSucursal = 3
    estado.fechaEntrega = '2026-10-31'
    expect(validarPedido(estado, '2026-10-31')).toEqual({ lineas: {} })
  })

  it('exige al menos una línea completa', () => {
    expect(validarPedido({ ...base(), lineas: [] }, '2026-10-31').productos).toBeTruthy()
    expect(validarPedido({ ...base(), lineas: [{ clave: 1, idProducto: null, cantidad: null }] }, '2026-10-31').lineas[1]).toMatchObject({ producto: expect.any(String), cantidad: expect.any(String) })
  })

  it.each([0, -1, 0.0001, 1.1234, 1000000000, Number.NaN])('rechaza cantidad inválida %s', (cantidad) => {
    expect(validarPedido({ ...base(), lineas: [{ clave: 1, idProducto: 9, cantidad }] }, '2026-10-31').lineas[1]?.cantidad).toBeTruthy()
  })

  it('acepta hasta tres decimales y el máximo permitido', () => {
    expect(validarPedido({ ...base(), lineas: [{ clave: 1, idProducto: 9, cantidad: 999999999.999 }] }, '2026-10-31')).toEqual({ lineas: {} })
  })

  it('detecta productos repetidos', () => {
    const estado = base()
    estado.lineas.push({ clave: 2, idProducto: 9, cantidad: 2 })
    expect(validarPedido(estado, '2026-10-31').lineas[2]?.producto).toMatch(/repetido/)
  })

  it('limita observación a 500 caracteres y recorta el payload', () => {
    const estado = { ...base(), observacion: 'x'.repeat(501) }
    expect(validarPedido(estado, '2026-10-31').observacion).toBeTruthy()
    estado.observacion = '  Para mañana  '
    expect(construirSolicitud(estado)).toEqual({ idSucursal: 3, fechaEntrega: '2026-11-01', observacion: 'Para mañana', detalles: [{ idProducto: 9, cantidad: 1 }] })
    estado.observacion = '  '
    expect(construirSolicitud(estado).observacion).toBeNull()
  })

  it('detecta cambios de campos y líneas sin marcar el estado inicial', () => {
    expect(hayCambios({ ...base(), idSucursal: null, observacion: '', lineas: [{ clave: 1, idProducto: null, cantidad: null }] })).toBe(false)
    expect(hayCambios(base())).toBe(true)
    expect(hayCambios({ ...base(), idSucursal: null, lineas: [{ clave: 1, idProducto: null, cantidad: null }, { clave: 2, idProducto: null, cantidad: null }] })).toBe(true)
    const inicial = { idSucursal: 3, fechaEntrega: '2026-11-01' }
    expect(hayCambios({ ...base(), lineas: [{ clave: 1, idProducto: null, cantidad: null }] }, inicial)).toBe(false)
    expect(hayCambios({ ...base(), fechaEntrega: '2026-11-02', lineas: [{ clave: 1, idProducto: null, cantidad: null }] }, inicial)).toBe(true)
    expect(hayCambios({ ...base(), observacion: ' ', lineas: [{ clave: 1, idProducto: null, cantidad: null }] }, inicial)).toBe(true)
  })

  it('rechaza una fecha de calendario inválida', () => {
    expect(validarPedido({ ...base(), fechaEntrega: '2026-02-30' }, '2026-01-01').fechaEntrega).toBeTruthy()
  })
})

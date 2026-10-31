import { describe, expect, it } from 'vitest'
import type { Producto } from '../api/productos'
import { filtrarProductos, leerFiltrosProductos, normalizarProducto, queryFiltrosProductos,
  resumenProductos, validarProducto } from './productos'
import { normalizarSucursal, validarSucursal } from './sucursales'

const producto = (nombre: string, tipo: Producto['nombreTipoProducto'], estado: Producto['estado']): Producto => ({
  idProducto: 1, idTipoProducto: 1, idUnidad: 1, nombre, nombreTipoProducto: tipo,
  nombreUnidadMedida: 'Unidad', abreviaturaUnidadMedida: 'UND', stockMinimo: 0,
  estado, fechaCreacion: '', enUso: false,
})
const lista = [producto('Harina', 'INSUMO', 'ACTIVO'), producto('Pan', 'PRODUCTO_FINAL', 'ACTIVO'),
  producto('Pan viejo', 'PRODUCTO_FINAL', 'INACTIVO')]

describe('catálogo de productos', () => {
  it('filtra por tipo, estado y nombre desde la query', () => {
    const filtros = leerFiltrosProductos({ tipo: 'finales', estado: 'TODOS', q: 'pan' })
    expect(filtrarProductos(lista, filtros).map((item) => item.nombre)).toEqual(['Pan', 'Pan viejo'])
    expect(queryFiltrosProductos(filtros)).toEqual({ tipo: 'finales', estado: 'TODOS', q: 'pan' })
    expect(filtrarProductos(lista, leerFiltrosProductos({})).map((item) => item.nombre)).toEqual(['Harina', 'Pan'])
    expect(resumenProductos(lista)).toBe('1 insumos · 2 productos finales · 1 inactivos')
  })
  it('valida nombre, referencias y stock mínimo', () => {
    expect(validarProducto({ nombre: ' ', idTipoProducto: null, idUnidad: null, stockMinimo: -1 }))
      .toEqual({ nombre: expect.any(String), idTipoProducto: expect.any(String), idUnidad: expect.any(String), stockMinimo: expect.any(String) })
    expect(validarProducto({ nombre: 'Pan', idTipoProducto: 1, idUnidad: 1, stockMinimo: 0.0001 }).stockMinimo).toBeTruthy()
    expect(validarProducto({ nombre: 'Pan', idTipoProducto: 1, idUnidad: 1, stockMinimo: 999999999.999 })).toEqual({})
    expect(normalizarProducto({ nombre: ' Pan ', idTipoProducto: 1, idUnidad: 2, stockMinimo: 0 })).toEqual({ nombre: 'Pan', idTipoProducto: 1, idUnidad: 2, stockMinimo: 0 })
  })
})

describe('catálogo de sucursales', () => {
  it('valida nombre, dirección y teléfono', () => {
    expect(validarSucursal({ nombre: ' ', direccion: 'x'.repeat(201), telefono: 'abc' }))
      .toEqual({ nombre: expect.any(String), direccion: expect.any(String), telefono: expect.any(String) })
    expect(validarSucursal({ nombre: 'Centro', direccion: '', telefono: '+502 (12) 34-56' })).toEqual({})
    expect(normalizarSucursal({ nombre: ' Centro ', direccion: ' Calle ', telefono: ' 123 ' }))
      .toEqual({ nombre: 'Centro', direccion: 'Calle', telefono: '123' })
  })
})

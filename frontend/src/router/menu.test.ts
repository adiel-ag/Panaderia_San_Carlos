import { describe, expect, it } from 'vitest'
import { ROLES, type Rol } from '../api/tipos'
import { menu, opcionesMenu, seccionesMenu } from './menu'

const visibles: Record<Rol, string[]> = {
  ADMINISTRADOR: ['inicio', 'pedidos', 'produccion', 'recetas', 'inventario', 'productos', 'despacho', 'reportes', 'usuarios', 'sucursales'],
  GERENTE: ['inicio', 'pedidos', 'produccion', 'recetas', 'inventario', 'productos', 'reportes'],
  SUCURSAL: ['inicio', 'pedidos'],
  PRODUCCION: ['inicio', 'pedidos', 'produccion', 'recetas', 'inventario', 'productos'],
  BODEGA: ['inicio', 'inventario', 'productos'],
  DESPACHO: ['inicio', 'pedidos', 'inventario', 'despacho'],
}

describe('opcionesMenu', () => {
  it('coloca los catálogos junto a sus módulos y conserva sus permisos', () => {
    const productos = menu.findIndex((opcion) => opcion.nombre === 'productos')
    const sucursales = menu.findIndex((opcion) => opcion.nombre === 'sucursales')
    expect(menu[productos - 1]?.nombre).toBe('inventario')
    expect(menu[sucursales - 1]?.nombre).toBe('usuarios')
    expect(menu[productos]?.roles).toEqual(['ADMINISTRADOR', 'GERENTE', 'PRODUCCION', 'BODEGA'])
    expect(menu[sucursales]?.roles).toEqual(['ADMINISTRADOR'])
  })
  it.each(ROLES)('muestra únicamente los módulos de %s', (rol) => {
    expect(opcionesMenu(rol).map((opcion) => opcion.nombre)).toEqual(visibles[rol])
  })

  it('coloca Recetas justo después de Producción con sus roles', () => {
    const indice = menu.findIndex((opcion) => opcion.nombre === 'recetas')
    expect(menu[indice - 1]?.nombre).toBe('produccion')
    expect(menu[indice]).toEqual({
      nombre: 'recetas', etiqueta: 'Recetas', icono: 'pi pi-book', descripcion: 'Define el rendimiento y los ingredientes de cada producto.',
      roles: ['ADMINISTRADOR', 'GERENTE', 'PRODUCCION'], grupo: 'catalogos',
    })
  })

  it('coloca Reportes antes de Usuarios, solo para Administración y Gerencia', () => {
    const indice = menu.findIndex((opcion) => opcion.nombre === 'reportes')
    expect(menu[indice + 1]?.nombre).toBe('usuarios')
    expect(menu[indice]).toEqual({
      nombre: 'reportes', etiqueta: 'Reportes', icono: 'pi pi-chart-bar', descripcion: 'Consulta pedidos, producción y consumo por fechas.',
      roles: ['ADMINISTRADOR', 'GERENTE'], grupo: 'administracion',
    })
  })

  it('no muestra opciones sin rol', () => {
    expect(opcionesMenu(null)).toEqual([])
  })
})

describe('seccionesMenu', () => {
  it('agrupa los módulos en Operación, Catálogos y Administración', () => {
    expect(seccionesMenu('ADMINISTRADOR').map((seccion) => [seccion.etiqueta, seccion.opciones.map((opcion) => opcion.nombre)])).toEqual([
      ['Operación', ['inicio', 'pedidos', 'produccion', 'inventario', 'despacho']],
      ['Catálogos', ['recetas', 'productos']],
      ['Administración', ['reportes', 'usuarios', 'sucursales']],
    ])
  })

  it('omite los grupos sin módulos visibles para el rol', () => {
    expect(seccionesMenu('SUCURSAL').map((seccion) => seccion.grupo)).toEqual(['operacion'])
    expect(seccionesMenu('BODEGA').map((seccion) => seccion.grupo)).toEqual(['operacion', 'catalogos'])
    expect(seccionesMenu('GERENTE').map((seccion) => seccion.grupo)).toEqual(['operacion', 'catalogos', 'administracion'])
    expect(seccionesMenu(null)).toEqual([])
  })

  it.each(ROLES)('conserva todos los módulos visibles de %s', (rol) => {
    const agrupados = seccionesMenu(rol).flatMap((seccion) => seccion.opciones.map((opcion) => opcion.nombre))
    expect([...agrupados].sort()).toEqual([...visibles[rol]].sort())
  })
})

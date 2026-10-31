import { describe, expect, it } from 'vitest'
import { ROLES, type Rol } from '../api/tipos'
import { menu, opcionesMenu } from './menu'

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
      roles: ['ADMINISTRADOR', 'GERENTE', 'PRODUCCION'],
    })
  })

  it('coloca Reportes antes de Usuarios, solo para Administración y Gerencia', () => {
    const indice = menu.findIndex((opcion) => opcion.nombre === 'reportes')
    expect(menu[indice + 1]?.nombre).toBe('usuarios')
    expect(menu[indice]).toEqual({
      nombre: 'reportes', etiqueta: 'Reportes', icono: 'pi pi-chart-bar', descripcion: 'Consulta pedidos, producción y consumo por fechas.',
      roles: ['ADMINISTRADOR', 'GERENTE'],
    })
  })

  it('no muestra opciones sin rol', () => {
    expect(opcionesMenu(null)).toEqual([])
  })
})

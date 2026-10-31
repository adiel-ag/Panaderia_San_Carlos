import { describe, expect, it } from 'vitest'
import { ROLES, type Rol } from '../api/tipos'
import { menu, opcionesMenu } from './menu'

const visibles: Record<Rol, string[]> = {
  ADMINISTRADOR: ['inicio', 'pedidos', 'produccion', 'recetas', 'inventario', 'despacho', 'usuarios'],
  GERENTE: ['inicio', 'pedidos', 'produccion', 'recetas', 'inventario'],
  SUCURSAL: ['inicio', 'pedidos'],
  PRODUCCION: ['inicio', 'pedidos', 'produccion', 'recetas', 'inventario'],
  BODEGA: ['inicio', 'inventario'],
  DESPACHO: ['inicio', 'pedidos', 'inventario', 'despacho'],
}

describe('opcionesMenu', () => {
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

  it('no muestra opciones sin rol', () => {
    expect(opcionesMenu(null)).toEqual([])
  })
})

import { describe, expect, it } from 'vitest'
import { ROLES, type Rol } from '../api/tipos'
import { opcionesMenu } from './menu'

const visibles: Record<Rol, string[]> = {
  ADMINISTRADOR: ['inicio', 'pedidos', 'produccion', 'inventario', 'despacho', 'usuarios'],
  GERENTE: ['inicio', 'pedidos', 'produccion', 'inventario'],
  SUCURSAL: ['inicio', 'pedidos'],
  PRODUCCION: ['inicio', 'pedidos', 'produccion', 'inventario'],
  BODEGA: ['inicio', 'inventario'],
  DESPACHO: ['inicio', 'pedidos', 'inventario', 'despacho'],
}

describe('opcionesMenu', () => {
  it.each(ROLES)('muestra únicamente los módulos de %s', (rol) => {
    expect(opcionesMenu(rol).map((opcion) => opcion.nombre)).toEqual(visibles[rol])
  })

  it('no muestra opciones sin rol', () => {
    expect(opcionesMenu(null)).toEqual([])
  })
})

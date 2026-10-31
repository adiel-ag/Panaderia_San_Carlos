import { ROLES, type Rol } from '../api/tipos'

export type OpcionMenu = {
  nombre: 'inicio' | 'pedidos' | 'produccion' | 'inventario' | 'despacho' | 'usuarios'
  etiqueta: string
  icono: string
  roles: readonly Rol[]
}

export const menu: readonly OpcionMenu[] = [
  { nombre: 'inicio', etiqueta: 'Inicio', icono: 'pi pi-home', roles: ROLES },
  { nombre: 'pedidos', etiqueta: 'Pedidos', icono: 'pi pi-shopping-bag', roles: ['ADMINISTRADOR', 'GERENTE', 'SUCURSAL', 'PRODUCCION', 'DESPACHO'] },
  { nombre: 'produccion', etiqueta: 'Producción', icono: 'pi pi-cog', roles: ['ADMINISTRADOR', 'GERENTE', 'PRODUCCION'] },
  { nombre: 'inventario', etiqueta: 'Inventario', icono: 'pi pi-box', roles: ['ADMINISTRADOR', 'GERENTE', 'PRODUCCION', 'BODEGA', 'DESPACHO'] },
  { nombre: 'despacho', etiqueta: 'Despacho', icono: 'pi pi-truck', roles: ['ADMINISTRADOR', 'DESPACHO'] },
  { nombre: 'usuarios', etiqueta: 'Usuarios', icono: 'pi pi-users', roles: ['ADMINISTRADOR'] },
]

export function opcionesMenu(rol: Rol | null): OpcionMenu[] {
  return rol === null ? [] : menu.filter((opcion) => opcion.roles.includes(rol))
}

import { ROLES, type Rol } from '../api/tipos'

export type OpcionMenu = {
  nombre: 'inicio' | 'pedidos' | 'produccion' | 'recetas' | 'inventario' | 'productos' | 'despacho' | 'usuarios' | 'sucursales'
  etiqueta: string
  icono: string
  descripcion: string
  roles: readonly Rol[]
}

export const menu: readonly OpcionMenu[] = [
  { nombre: 'inicio', etiqueta: 'Inicio', icono: 'pi pi-home', descripcion: 'Resumen y accesos a tus módulos.', roles: ROLES },
  { nombre: 'pedidos', etiqueta: 'Pedidos', icono: 'pi pi-shopping-bag', descripcion: 'Registra y sigue los pedidos de cada sucursal.', roles: ['ADMINISTRADOR', 'GERENTE', 'SUCURSAL', 'PRODUCCION', 'DESPACHO'] },
  { nombre: 'produccion', etiqueta: 'Producción', icono: 'pi pi-cog', descripcion: 'Planifica las horneadas y registra lo producido.', roles: ['ADMINISTRADOR', 'GERENTE', 'PRODUCCION'] },
  { nombre: 'recetas', etiqueta: 'Recetas', icono: 'pi pi-book', descripcion: 'Define el rendimiento y los ingredientes de cada producto.', roles: ['ADMINISTRADOR', 'GERENTE', 'PRODUCCION'] },
  { nombre: 'inventario', etiqueta: 'Inventario', icono: 'pi pi-box', descripcion: 'Consulta existencias y registra entradas y salidas.', roles: ['ADMINISTRADOR', 'GERENTE', 'PRODUCCION', 'BODEGA', 'DESPACHO'] },
  { nombre: 'productos', etiqueta: 'Productos', icono: 'pi pi-tags', descripcion: 'Da de alta y ajusta los productos e insumos.', roles: ['ADMINISTRADOR', 'GERENTE', 'PRODUCCION', 'BODEGA'] },
  { nombre: 'despacho', etiqueta: 'Despacho', icono: 'pi pi-truck', descripcion: 'Despacha los pedidos listos a cada sucursal.', roles: ['ADMINISTRADOR', 'DESPACHO'] },
  { nombre: 'usuarios', etiqueta: 'Usuarios', icono: 'pi pi-users', descripcion: 'Administra cuentas, roles y contraseñas.', roles: ['ADMINISTRADOR'] },
  { nombre: 'sucursales', etiqueta: 'Sucursales', icono: 'pi pi-building', descripcion: 'Administra las tiendas que hacen pedidos.', roles: ['ADMINISTRADOR'] },
]

export function opcionesMenu(rol: Rol | null): OpcionMenu[] {
  return rol === null ? [] : menu.filter((opcion) => opcion.roles.includes(rol))
}

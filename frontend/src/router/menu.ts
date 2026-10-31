import { ROLES, type Rol } from '../api/tipos'

export type GrupoMenu = 'operacion' | 'catalogos' | 'administracion'

export const GRUPOS_MENU: readonly { grupo: GrupoMenu; etiqueta: string }[] = [
  { grupo: 'operacion', etiqueta: 'Operación' },
  { grupo: 'catalogos', etiqueta: 'Catálogos' },
  { grupo: 'administracion', etiqueta: 'Administración' },
]

export type OpcionMenu = {
  nombre: 'inicio' | 'pedidos' | 'produccion' | 'recetas' | 'inventario' | 'productos' | 'despacho' | 'reportes' | 'usuarios' | 'sucursales'
  etiqueta: string
  icono: string
  descripcion: string
  roles: readonly Rol[]
  grupo: GrupoMenu
}

export const menu: readonly OpcionMenu[] = [
  { nombre: 'inicio', etiqueta: 'Inicio', icono: 'pi pi-home', descripcion: 'Resumen y accesos a tus módulos.', roles: ROLES, grupo: 'operacion' },
  { nombre: 'pedidos', etiqueta: 'Pedidos', icono: 'pi pi-shopping-bag', descripcion: 'Registra y sigue los pedidos de cada sucursal.', roles: ['ADMINISTRADOR', 'GERENTE', 'SUCURSAL', 'PRODUCCION', 'DESPACHO'], grupo: 'operacion' },
  { nombre: 'produccion', etiqueta: 'Producción', icono: 'pi pi-cog', descripcion: 'Planifica las horneadas y registra lo producido.', roles: ['ADMINISTRADOR', 'GERENTE', 'PRODUCCION'], grupo: 'operacion' },
  { nombre: 'recetas', etiqueta: 'Recetas', icono: 'pi pi-book', descripcion: 'Define el rendimiento y los ingredientes de cada producto.', roles: ['ADMINISTRADOR', 'GERENTE', 'PRODUCCION'], grupo: 'catalogos' },
  { nombre: 'inventario', etiqueta: 'Inventario', icono: 'pi pi-box', descripcion: 'Consulta existencias y registra entradas y salidas.', roles: ['ADMINISTRADOR', 'GERENTE', 'PRODUCCION', 'BODEGA', 'DESPACHO'], grupo: 'operacion' },
  { nombre: 'productos', etiqueta: 'Productos', icono: 'pi pi-tags', descripcion: 'Da de alta y ajusta los productos e insumos.', roles: ['ADMINISTRADOR', 'GERENTE', 'PRODUCCION', 'BODEGA'], grupo: 'catalogos' },
  { nombre: 'despacho', etiqueta: 'Despacho', icono: 'pi pi-truck', descripcion: 'Despacha los pedidos listos a cada sucursal.', roles: ['ADMINISTRADOR', 'DESPACHO'], grupo: 'operacion' },
  { nombre: 'reportes', etiqueta: 'Reportes', icono: 'pi pi-chart-bar', descripcion: 'Consulta pedidos, producción y consumo por fechas.', roles: ['ADMINISTRADOR', 'GERENTE'], grupo: 'administracion' },
  { nombre: 'usuarios', etiqueta: 'Usuarios', icono: 'pi pi-users', descripcion: 'Administra cuentas, roles y contraseñas.', roles: ['ADMINISTRADOR'], grupo: 'administracion' },
  { nombre: 'sucursales', etiqueta: 'Sucursales', icono: 'pi pi-building', descripcion: 'Administra las tiendas que hacen pedidos.', roles: ['ADMINISTRADOR'], grupo: 'administracion' },
]

export function opcionesMenu(rol: Rol | null): OpcionMenu[] {
  return rol === null ? [] : menu.filter((opcion) => opcion.roles.includes(rol))
}

export type SeccionMenu = { grupo: GrupoMenu; etiqueta: string; opciones: OpcionMenu[] }

/** Agrupa las opciones visibles del rol; los grupos sin opciones no aparecen. */
export function seccionesMenu(rol: Rol | null): SeccionMenu[] {
  const visibles = opcionesMenu(rol)
  return GRUPOS_MENU
    .map(({ grupo, etiqueta }) => ({ grupo, etiqueta, opciones: visibles.filter((opcion) => opcion.grupo === grupo) }))
    .filter((seccion) => seccion.opciones.length > 0)
}

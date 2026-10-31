import { describe, expect, it } from 'vitest'
import router from './index'
import { menu } from './menu'
import DespachoView from '../views/DespachoView.vue'
import InventarioView from '../views/InventarioView.vue'
import UsuariosView from '../views/UsuariosView.vue'
import RecetasView from '../views/RecetasView.vue'
import RecetaView from '../views/RecetaView.vue'
import NuevaRecetaView from '../views/NuevaRecetaView.vue'

// Las pantallas de módulo se cargan al abrirlas: se resuelve la importación diferida.
async function cargarVista(componente: unknown): Promise<unknown> {
  return typeof componente === 'function' ? ((await (componente as () => Promise<{ default: unknown }>)()).default) : componente
}

describe('rutas', () => {
  it('incluye la pagina de inicio', () => {
    expect(router.getRoutes().some((route) => route.path === '/')).toBe(true)
  })

  it('incluye la ruta de acceso', () => {
    expect(router.getRoutes().some((route) => route.path === '/login' && route.name === 'login')).toBe(true)
  })

  it('registra el layout, las rutas auxiliares y los módulos con sus permisos', () => {
    const rutas = router.getRoutes()
    for (const opcion of menu) {
      const ruta = rutas.find((item) => item.name === opcion.nombre)
      expect(ruta?.meta.roles).toEqual(opcion.roles)
      expect(ruta?.meta.titulo).toBe(opcion.etiqueta)
    }
    expect(rutas.find((ruta) => ruta.name === 'mi-clave')?.path).toBe('/mi-clave')
    expect(rutas.find((ruta) => ruta.name === 'sin-permiso')?.path).toBe('/sin-permiso')
    expect(rutas.find((ruta) => ruta.name === 'login')?.meta.publica).toBe(true)
    expect(rutas.some((ruta) => ruta.path === '/:pathMatch(.*)*')).toBe(true)
  })

  it('protege el detalle con los mismos roles de Pedidos', () => {
    const rutas = router.getRoutes()
    expect(rutas.find((ruta) => ruta.name === 'pedido')?.meta.roles).toEqual(menu.find((opcion) => opcion.nombre === 'pedidos')?.roles)
    expect(rutas.find((ruta) => ruta.name === 'pedido')?.path).toBe('/pedidos/:id(\\d+)')
  })

  it('protege el detalle de producción con los mismos roles de Producción', () => {
    const ruta = router.getRoutes().find((item) => item.name === 'produccion-detalle')
    expect(ruta?.path).toBe('/produccion/:id(\\d+)')
    expect(ruta?.meta.roles).toEqual(['ADMINISTRADOR', 'GERENTE', 'PRODUCCION'])
    expect(ruta?.meta.titulo).toBe('Producción')
    expect(router.resolve('/produccion/12').name).toBe('produccion-detalle')
  })

  it('muestra Despacho con su pantalla para administrador y despacho', async () => {
    const ruta = router.getRoutes().find((item) => item.name === 'despacho')
    expect(ruta?.path).toBe('/despacho')
    expect(await cargarVista(ruta?.components?.default)).toBe(DespachoView)
    expect(ruta?.meta.roles).toEqual(['ADMINISTRADOR', 'DESPACHO'])
    expect(ruta?.meta.titulo).toBe('Despacho')
  })

  it('muestra Inventario con su pantalla para los roles que lo consultan', async () => {
    const ruta = router.getRoutes().find((item) => item.name === 'inventario')
    expect(ruta?.path).toBe('/inventario')
    expect(await cargarVista(ruta?.components?.default)).toBe(InventarioView)
    expect(ruta?.meta.roles).toEqual(['ADMINISTRADOR', 'GERENTE', 'PRODUCCION', 'BODEGA', 'DESPACHO'])
    expect(ruta?.meta.titulo).toBe('Inventario')
  })

  it('muestra Usuarios con su pantalla solo para administrador', async () => {
    const ruta = router.getRoutes().find((item) => item.name === 'usuarios')
    expect(ruta?.path).toBe('/usuarios')
    expect(await cargarVista(ruta?.components?.default)).toBe(UsuariosView)
    expect(ruta?.meta.roles).toEqual(['ADMINISTRADOR'])
    expect(ruta?.meta.titulo).toBe('Usuarios')
  })

  it('muestra Recetas, su detalle y la nueva versión con sus permisos', async () => {
    const rutas = router.getRoutes()
    const lista = rutas.find((item) => item.name === 'recetas')
    expect(lista?.path).toBe('/recetas')
    expect(await cargarVista(lista?.components?.default)).toBe(RecetasView)
    expect(lista?.meta.roles).toEqual(['ADMINISTRADOR', 'GERENTE', 'PRODUCCION'])
    const detalle = rutas.find((item) => item.name === 'receta')
    expect(detalle?.path).toBe('/recetas/:idProducto(\\d+)')
    expect(await cargarVista(detalle?.components?.default)).toBe(RecetaView)
    expect(detalle?.meta.roles).toEqual(['ADMINISTRADOR', 'GERENTE', 'PRODUCCION'])
    const nueva = rutas.find((item) => item.name === 'receta-nueva')
    expect(nueva?.path).toBe('/recetas/:idProducto(\\d+)/nueva')
    expect(await cargarVista(nueva?.components?.default)).toBe(NuevaRecetaView)
    expect(nueva?.meta.roles).toEqual(['ADMINISTRADOR', 'PRODUCCION'])
    expect(router.resolve('/recetas/9').name).toBe('receta')
    expect(router.resolve('/recetas/9/nueva').name).toBe('receta-nueva')
  })

  it('registra nuevo pedido solo para administrador y sucursal', () => {
    const ruta = router.resolve('/pedidos/nuevo')
    expect(ruta.name).toBe('pedido-nuevo')
    expect(ruta.meta.roles).toEqual(['ADMINISTRADOR', 'SUCURSAL'])
    expect(ruta.meta.titulo).toBe('Nuevo pedido')
  })
})

import { describe, expect, it } from 'vitest'
import router from './index'
import { menu } from './menu'
import DespachoView from '../views/DespachoView.vue'
import InventarioView from '../views/InventarioView.vue'
import UsuariosView from '../views/UsuariosView.vue'

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

  it('muestra Despacho con su pantalla para administrador y despacho', () => {
    const ruta = router.getRoutes().find((item) => item.name === 'despacho')
    expect(ruta?.path).toBe('/despacho')
    expect(ruta?.components?.default).toBe(DespachoView)
    expect(ruta?.meta.roles).toEqual(['ADMINISTRADOR', 'DESPACHO'])
    expect(ruta?.meta.titulo).toBe('Despacho')
  })

  it('muestra Inventario con su pantalla para los roles que lo consultan', () => {
    const ruta = router.getRoutes().find((item) => item.name === 'inventario')
    expect(ruta?.path).toBe('/inventario')
    expect(ruta?.components?.default).toBe(InventarioView)
    expect(ruta?.meta.roles).toEqual(['ADMINISTRADOR', 'GERENTE', 'PRODUCCION', 'BODEGA', 'DESPACHO'])
    expect(ruta?.meta.titulo).toBe('Inventario')
  })

  it('muestra Usuarios con su pantalla solo para administrador', () => {
    const ruta = router.getRoutes().find((item) => item.name === 'usuarios')
    expect(ruta?.path).toBe('/usuarios')
    expect(ruta?.components?.default).toBe(UsuariosView)
    expect(ruta?.meta.roles).toEqual(['ADMINISTRADOR'])
    expect(ruta?.meta.titulo).toBe('Usuarios')
  })

  it('registra nuevo pedido solo para administrador y sucursal', () => {
    const ruta = router.resolve('/pedidos/nuevo')
    expect(ruta.name).toBe('pedido-nuevo')
    expect(ruta.meta.roles).toEqual(['ADMINISTRADOR', 'SUCURSAL'])
    expect(ruta.meta.titulo).toBe('Nuevo pedido')
  })
})

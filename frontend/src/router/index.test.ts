import { describe, expect, it } from 'vitest'
import router from './index'
import { menu } from './menu'

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
})

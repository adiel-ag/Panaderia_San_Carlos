import { describe, expect, it } from 'vitest'
import router from './index'

describe('rutas', () => {
  it('incluye la pagina de inicio', () => {
    expect(router.getRoutes().some((route) => route.path === '/')).toBe(true)
  })
})

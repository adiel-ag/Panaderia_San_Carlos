import { describe, expect, it, vi } from 'vitest'
import type { RouteLocationNormalized } from 'vue-router'
import { ROLES, type Rol } from '../api/tipos'
import { decidirNavegacion, rutaSegura } from './guardia'

const rolesPorRuta: Record<string, Rol[]> = {
  inicio: [...ROLES],
  pedidos: ['ADMINISTRADOR', 'GERENTE', 'SUCURSAL', 'PRODUCCION', 'DESPACHO'],
  produccion: ['ADMINISTRADOR', 'GERENTE', 'PRODUCCION'],
  inventario: ['ADMINISTRADOR', 'GERENTE', 'PRODUCCION', 'BODEGA', 'DESPACHO'],
  despacho: ['ADMINISTRADOR', 'DESPACHO'],
  usuarios: ['ADMINISTRADOR'],
}

function destino(nombre: string, opciones: { publica?: boolean; roles?: Rol[]; redirect?: unknown } = {}) {
  return {
    name: nombre,
    fullPath: nombre === 'login' ? '/login' : `/${nombre}`,
    query: opciones.redirect === undefined ? {} : { redirect: opciones.redirect },
    meta: { publica: opciones.publica, roles: opciones.roles },
  } as RouteLocationNormalized
}

function sesion(rol: Rol | null = null, verificada = true) {
  return {
    verificada,
    autenticado: rol !== null,
    cargar: vi.fn(async () => {}),
    tieneRol: vi.fn((...roles: Rol[]) => rol !== null && roles.includes(rol)),
  }
}

describe('rutaSegura', () => {
  it.each(['/pedidos', '/inventario?pagina=2', '/#vista', '/'])('acepta %s', (ruta) => {
    expect(rutaSegura(ruta)).toBe(ruta)
  })

  it.each(['//evil.com', 'https://x', '/\\evil.com', '/login', '/login?x=1', 4, null, ['pedidos']])('rechaza %s', (ruta) => {
    expect(rutaSegura(ruta)).toBe('/')
  })
})

describe('decidirNavegacion', () => {
  it('carga la sesión una vez cuando aún no está verificada', async () => {
    const estado = sesion('GERENTE', false)
    expect(await decidirNavegacion(destino('inicio'), estado)).toBe(true)
    expect(estado.cargar).toHaveBeenCalledOnce()
    estado.verificada = true
    expect(await decidirNavegacion(destino('inicio'), estado)).toBe(true)
    expect(estado.cargar).toHaveBeenCalledOnce()
  })

  it('deja entrar al login público si falla la carga', async () => {
    const estado = sesion(null, false)
    estado.cargar.mockRejectedValue(new Error('red'))
    expect(await decidirNavegacion(destino('login', { publica: true }), estado)).toBe(true)
  })

  it('manda al login con aviso y retorno si falla la carga', async () => {
    const estado = sesion(null, false)
    estado.cargar.mockRejectedValue(new Error('red'))
    expect(await decidirNavegacion(destino('pedidos'), estado)).toEqual({
      name: 'login', query: { redirect: '/pedidos', aviso: 'sin-conexion' },
    })
  })

  it('manda al login con retorno si no está autenticado', async () => {
    expect(await decidirNavegacion(destino('pedidos'), sesion())).toEqual({
      name: 'login', query: { redirect: '/pedidos' },
    })
  })

  it.each(['/pedidos', '//evil.com'])('redirige desde login autenticado según seguridad de %s', async (redirect) => {
    expect(await decidirNavegacion(destino('login', { publica: true, redirect }), sesion('GERENTE')))
      .toBe(redirect === '/pedidos' ? '/pedidos' : '/')
  })

  it('permite el login público sin sesión', async () => {
    expect(await decidirNavegacion(destino('login', { publica: true }), sesion())).toBe(true)
  })

  it.each(ROLES.flatMap((rol) => Object.entries(rolesPorRuta).map(([nombre, roles]) => ({ rol, nombre, roles }))))(
    '$rol accede a $nombre según la matriz', async ({ rol, nombre, roles }) => {
      expect(await decidirNavegacion(destino(nombre, { roles }), sesion(rol)))
        .toEqual(roles.includes(rol) ? true : { name: 'sin-permiso' })
    },
  )

  it('sin-permiso no produce un ciclo de redirección', async () => {
    expect(await decidirNavegacion(destino('sin-permiso'), sesion('BODEGA'))).toBe(true)
  })
})

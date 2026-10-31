import type { RouteLocationNormalized, RouteLocationRaw } from 'vue-router'
import type { Rol } from '../api/tipos'

type SesionNavegacion = {
  verificada: boolean
  autenticado: boolean
  cargar: () => Promise<void>
  tieneRol: (...roles: Rol[]) => boolean
}

export function rutaSegura(valor: unknown): string {
  if (typeof valor !== 'string' || !valor.startsWith('/') || valor.startsWith('//') || valor.includes('\\')) return '/'
  const primerSegmento = valor.slice(1).split(/[/?#]/, 1)[0]
  if (primerSegmento.includes(':') || valor.startsWith('/login')) return '/'
  return valor
}

export async function decidirNavegacion(
  to: RouteLocationNormalized,
  sesion: SesionNavegacion,
): Promise<true | RouteLocationRaw> {
  if (!sesion.verificada) {
    try {
      await sesion.cargar()
    } catch {
      if (!to.meta.publica) {
        return { name: 'login', query: { redirect: to.fullPath, aviso: 'sin-conexion' } }
      }
    }
  }

  if (to.meta.publica) {
    return sesion.autenticado ? rutaSegura(to.query.redirect) : true
  }
  if (!sesion.autenticado) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.roles && !sesion.tieneRol(...to.meta.roles)) return { name: 'sin-permiso' }
  return true
}

import type { Rol } from '../api/tipos'

declare module 'vue-router' {
  interface RouteMeta {
    publica?: boolean
    roles?: readonly Rol[]
    titulo?: string
  }
}

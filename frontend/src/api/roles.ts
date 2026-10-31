import type { Rol } from './tipos'

const etiquetas: Record<Rol, string> = {
  ADMINISTRADOR: 'Administrador',
  GERENTE: 'Gerente',
  SUCURSAL: 'Sucursal',
  PRODUCCION: 'Producción',
  BODEGA: 'Bodega',
  DESPACHO: 'Despacho',
}

export function etiquetaRol(rol: Rol | null): string {
  return rol === null ? '' : etiquetas[rol]
}

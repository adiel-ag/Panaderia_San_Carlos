export const ROLES = [
  'ADMINISTRADOR',
  'GERENTE',
  'SUCURSAL',
  'PRODUCCION',
  'BODEGA',
  'DESPACHO',
] as const

export type Rol = (typeof ROLES)[number]

export interface Usuario {
  idUsuario: number
  username: string
  nombre: string
  rol: Rol
  idSucursal: number | null
}

export interface Pagina<T> {
  items: T[]
  total: number
  pagina: number
  tamano: number
}

export interface ApiErrorBody {
  status: number
  error: string
  message: string
}

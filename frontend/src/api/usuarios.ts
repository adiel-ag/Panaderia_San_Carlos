import { api } from './cliente'
import type { Pagina, Rol } from './tipos'

export const ESTADOS_USUARIO = ['ACTIVO', 'INACTIVO'] as const
export type EstadoUsuario = typeof ESTADOS_USUARIO[number]

// Usuario tal como lo ve la administración (distinto del usuario de la sesión).
export interface UsuarioAdmin {
  idUsuario: number
  nombre: string
  username: string
  rol: Rol
  idSucursal: number | null
  nombreSucursal: string | null
  estado: EstadoUsuario
  fechaCreacion: string
  ultimoAcceso: string | null
  bloqueado: boolean
  tieneClave: boolean
}
export interface RolCatalogo { idRol: number; nombre: string; descripcion: string | null }
export interface FiltrosUsuariosApi {
  rol?: Rol
  estado?: EstadoUsuario
  idSucursal?: number
}
export interface SolicitudCrearUsuario {
  nombre: string
  username: string
  rol: Rol
  idSucursal: number | null
  password: string
}
export interface SolicitudEditarUsuario {
  nombre: string
  rol: Rol
  idSucursal: number | null
}

export function listarUsuarios(filtros: FiltrosUsuariosApi, pagina = 1, tamano = 20, avisar = true): Promise<Pagina<UsuarioAdmin>> {
  return api('usuarios', { consulta: { pagina, tamano, ...filtros }, avisar })
}
export function crearUsuario(solicitud: SolicitudCrearUsuario): Promise<UsuarioAdmin> {
  return api('usuarios', { metodo: 'POST', cuerpo: solicitud, avisar: true })
}
export function actualizarUsuario(idUsuario: number, solicitud: SolicitudEditarUsuario): Promise<UsuarioAdmin> {
  return api(`usuarios/${idUsuario}`, { metodo: 'PUT', cuerpo: solicitud, avisar: true })
}
export function cambiarEstadoUsuario(idUsuario: number, estado: EstadoUsuario): Promise<UsuarioAdmin> {
  return api(`usuarios/${idUsuario}/estado`, { metodo: 'PUT', cuerpo: { estado }, avisar: true })
}
export function desbloquearUsuario(idUsuario: number): Promise<UsuarioAdmin> {
  return api(`usuarios/${idUsuario}/desbloquear`, { metodo: 'POST', avisar: true })
}
export function asignarClave(idUsuario: number, password: string): Promise<{ mensaje: string }> {
  return api(`usuarios/${idUsuario}/clave`, { metodo: 'PUT', cuerpo: { password }, avisar: true })
}
export function listarRolesCatalogo(): Promise<RolCatalogo[]> {
  return api('roles', { avisar: false })
}

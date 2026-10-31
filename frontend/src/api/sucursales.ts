import { api } from './cliente'
import type { EstadoCatalogo } from './productos'

export interface SucursalAdmin {
  idSucursal: number
  nombre: string
  direccion: string | null
  telefono: string | null
  estado: EstadoCatalogo
  usuariosActivos: number
  pedidosAbiertos: number
}
export interface SolicitudSucursal { nombre: string; direccion: string; telefono: string }

export function listarTodasSucursales(): Promise<SucursalAdmin[]> { return api('sucursales/todas', { avisar: false }) }
export function crearSucursal(solicitud: SolicitudSucursal): Promise<SucursalAdmin> {
  return api('sucursales', { metodo: 'POST', cuerpo: solicitud, avisar: true })
}
export function actualizarSucursal(id: number, solicitud: SolicitudSucursal): Promise<SucursalAdmin> {
  return api(`sucursales/${id}`, { metodo: 'PUT', cuerpo: solicitud, avisar: true })
}
export function cambiarEstadoSucursal(id: number, estado: EstadoCatalogo): Promise<SucursalAdmin> {
  return api(`sucursales/${id}/estado`, { metodo: 'PUT', cuerpo: { estado }, avisar: true })
}

import type { LocationQuery, LocationQueryRaw } from 'vue-router'
import { formatearFechaHora } from '../api/fechas'
import { ROLES, type Rol } from '../api/tipos'
import type { EstadoUsuario, FiltrosUsuariosApi, SolicitudCrearUsuario, SolicitudEditarUsuario, UsuarioAdmin } from '../api/usuarios'

export type FiltroEstado = EstadoUsuario | 'TODOS'
// q no viaja al servidor: la API no busca por nombre, así que filtra solo la página cargada.
export interface FiltrosUsuarios { rol: Rol | null; estado: FiltroEstado; sucursal: number | null; q: string; pagina: number }
export type ModoUsuario = 'crear' | 'editar'
export interface FormularioUsuario {
  nombre: string
  username: string
  rol: Rol | null
  idSucursal: number | null
  password: string
  confirmacion: string
}
export interface ErroresUsuario {
  nombre?: string
  username?: string
  rol?: string
  sucursal?: string
  password?: string
  confirmacion?: string
}

export const LARGO_BUSQUEDA = 100
export const AYUDA_USERNAME = 'Use minúsculas, números, punto o guion (3 a 50).'
const FORMATO_USERNAME = /^[a-z0-9.-]{3,50}$/
const ESTADOS_FILTRO: readonly FiltroEstado[] = ['ACTIVO', 'INACTIVO', 'TODOS']

function simple(valor: LocationQuery[string]): string | undefined {
  return typeof valor === 'string' ? valor : undefined
}
function enteroPositivo(valor: string | undefined): number | null {
  if (!valor || !/^[1-9]\d*$/.test(valor)) return null
  const numero = Number(valor)
  return Number.isSafeInteger(numero) ? numero : null
}
export function normalizarBusqueda(texto: string): string {
  return texto.trim().slice(0, LARGO_BUSQUEDA).trim()
}

export function leerFiltrosUsuarios(query: LocationQuery): FiltrosUsuarios {
  const rol = simple(query.rol)
  const estado = simple(query.estado)
  return {
    rol: ROLES.find((opcion) => opcion === rol) ?? null,
    estado: ESTADOS_FILTRO.find((opcion) => opcion === estado) ?? 'ACTIVO',
    sucursal: enteroPositivo(simple(query.sucursal)),
    q: normalizarBusqueda(simple(query.q) ?? ''),
    pagina: enteroPositivo(simple(query.pagina)) ?? 1,
  }
}
// Activos es el valor por defecto: no se escribe en la URL.
export function queryFiltrosUsuarios(filtros: FiltrosUsuarios): LocationQueryRaw {
  return {
    ...(filtros.rol ? { rol: filtros.rol } : {}),
    ...(filtros.estado !== 'ACTIVO' ? { estado: filtros.estado } : {}),
    ...(filtros.sucursal !== null ? { sucursal: String(filtros.sucursal) } : {}),
    ...(filtros.q ? { q: filtros.q } : {}),
    ...(filtros.pagina !== 1 ? { pagina: String(filtros.pagina) } : {}),
  }
}
// Los filtros del servidor vuelven a la página 1; la búsqueda filtra la página
// que ya está a la vista, así que la conserva.
export function cambiarFiltros(filtros: FiltrosUsuarios, cambio: Partial<Omit<FiltrosUsuarios, 'pagina'>>): FiltrosUsuarios {
  const soloBusqueda = Object.keys(cambio).every((clave) => clave === 'q')
  return { ...filtros, ...cambio, pagina: soloBusqueda ? filtros.pagina : 1 }
}
export function consultaUsuarios(filtros: FiltrosUsuarios): FiltrosUsuariosApi {
  return {
    rol: filtros.rol ?? undefined,
    estado: filtros.estado === 'TODOS' ? undefined : filtros.estado,
    idSucursal: filtros.sucursal ?? undefined,
  }
}
export function filtrosPorDefecto(filtros: FiltrosUsuarios): boolean {
  return filtros.rol === null && filtros.estado === 'ACTIVO' && filtros.sucursal === null && filtros.q === ''
}

// Sin distinguir mayúsculas ni tildes.
function plano(texto: string): string {
  return texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLocaleLowerCase('es')
}
export function filtrarUsuarios(usuarios: UsuarioAdmin[], q: string): UsuarioAdmin[] {
  const buscado = plano(q.trim())
  if (!buscado) return usuarios
  return usuarios.filter((usuario) => plano(usuario.nombre).includes(buscado) || plano(usuario.username).includes(buscado))
}

export function validarClave(password: string, confirmacion: string): Pick<ErroresUsuario, 'password' | 'confirmacion'> {
  const errores: Pick<ErroresUsuario, 'password' | 'confirmacion'> = {}
  if (!password) errores.password = 'Ingrese la contraseña.'
  else if (password.length < 8 || password.length > 128) errores.password = 'La contraseña debe tener entre 8 y 128 caracteres.'
  if (!confirmacion) errores.confirmacion = 'Confirme la contraseña.'
  else if (confirmacion !== password) errores.confirmacion = 'Las contraseñas no coinciden.'
  return errores
}

export function validarUsuario(form: FormularioUsuario, modo: ModoUsuario): ErroresUsuario {
  const errores: ErroresUsuario = {}
  const nombre = form.nombre.trim()
  if (!nombre) errores.nombre = 'Ingrese el nombre.'
  else if (nombre.length > 100) errores.nombre = 'El nombre no puede superar 100 caracteres.'
  // El usuario no se puede cambiar al editar.
  if (modo === 'crear') {
    const username = form.username.trim().toLowerCase()
    if (!username) errores.username = 'Ingrese el usuario.'
    else if (!FORMATO_USERNAME.test(username)) errores.username = `Usuario no válido. ${AYUDA_USERNAME}`
  }
  if (form.rol === null) errores.rol = 'Elija un rol.'
  else if (form.rol === 'SUCURSAL' && form.idSucursal === null) errores.sucursal = 'Elija la sucursal del usuario.'
  if (modo === 'crear') Object.assign(errores, validarClave(form.password, form.confirmacion))
  return errores
}

// Solo el rol SUCURSAL lleva sucursal; los demás deben ir sin ella.
export function normalizarUsuario(form: FormularioUsuario, modo?: 'crear'): SolicitudCrearUsuario
export function normalizarUsuario(form: FormularioUsuario, modo: 'editar'): SolicitudEditarUsuario
export function normalizarUsuario(form: FormularioUsuario, modo: ModoUsuario = 'crear'): SolicitudCrearUsuario | SolicitudEditarUsuario {
  const rol = form.rol!
  const base = { nombre: form.nombre.trim(), rol, idSucursal: rol === 'SUCURSAL' ? form.idSucursal : null }
  if (modo === 'editar') return base
  return { nombre: base.nombre, username: form.username.trim().toLowerCase(), rol, idSucursal: base.idSucursal, password: form.password }
}

export function textoResumenUsuarios(activos: number, bloqueados: number): string {
  const texto = `${activos} ${activos === 1 ? 'usuario activo' : 'usuarios activos'}`
  if (bloqueados <= 0) return texto
  return `${texto} · ${bloqueados} ${bloqueados === 1 ? 'bloqueado' : 'bloqueados'}`
}

export function etiquetaAcceso(ultimoAcceso: string | null): string {
  return ultimoAcceso ? formatearFechaHora(ultimoAcceso) : 'Nunca'
}

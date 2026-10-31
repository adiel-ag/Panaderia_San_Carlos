import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError, api } from '../api/cliente'
import type { Rol, Usuario } from '../api/tipos'

const CLAVE_CIERRE_PENDIENTE = 'panaderia.cierrePendiente'

export const useSesionStore = defineStore('sesion', () => {
  const usuario = ref<Usuario | null>(null)
  const verificada = ref(false)
  const autenticado = computed(() => usuario.value !== null)
  const rol = computed(() => usuario.value?.rol ?? null)
  let cargaEnCurso: Promise<void> | null = null
  let cierreEnCurso: Promise<void> | null = null
  let cierrePendienteEnMemoria = false
  let almacenamientoDisponible = true
  // Cambia cada vez que la sesion se inicia o se borra: una respuesta tardia
  // de cargar() no debe deshacer ese cambio.
  let version = 0

  // El cierre pendiente se guarda en localStorage para que sobreviva a una recarga;
  // si el navegador lo bloquea, queda solo en memoria.
  function leerCierrePendiente(): boolean {
    if (!almacenamientoDisponible) return cierrePendienteEnMemoria
    try {
      return localStorage.getItem(CLAVE_CIERRE_PENDIENTE) === '1'
    } catch {
      return cierrePendienteEnMemoria
    }
  }

  function guardarCierrePendiente(pendiente: boolean): void {
    cierrePendienteEnMemoria = pendiente
    try {
      if (pendiente) localStorage.setItem(CLAVE_CIERRE_PENDIENTE, '1')
      else localStorage.removeItem(CLAVE_CIERRE_PENDIENTE)
      almacenamientoDisponible = true
    } catch {
      // No se pudo escribir: hasta la proxima escritura exitosa manda la memoria.
      almacenamientoDisponible = false
    }
  }

  function cambiarUsuario(nuevo: Usuario | null): void {
    version++
    cargaEnCurso = null
    usuario.value = nuevo
  }

  function tieneRol(...roles: Rol[]): boolean {
    return usuario.value !== null && roles.includes(usuario.value.rol)
  }

  function cargar(): Promise<void> {
    if (cargaEnCurso) return cargaEnCurso
    const versionInicial = version
    const vigente = () => version === versionInicial
    // Durante un cierre de sesion la cookie aun puede ser valida: se espera a que
    // termine. Si fallo, se reintenta el cierre en vez de recuperar la sesion.
    const consultar = (): Promise<Usuario | null> => leerCierrePendiente()
      ? enviarCierre().then(() => null)
      : api<Usuario>('auth/me', { manejar401: false, avisar: false })
    const peticion = cierreEnCurso
      ? cierreEnCurso.catch(() => {}).then(consultar)
      : consultar()
    const carga: Promise<void> = peticion
      .then((datos) => { if (vigente()) usuario.value = datos })
      .catch((error: unknown) => {
        if (vigente()) usuario.value = null
        if (error instanceof ApiError && error.status === 401) return
        throw error
      })
      .finally(() => {
        verificada.value = true
        if (cargaEnCurso === carga) cargaEnCurso = null
      })
    cargaEnCurso = carga
    return carga
  }

  async function iniciarSesion(username: string, password: string): Promise<Usuario> {
    const datos = await api<Usuario>('auth/login', {
      metodo: 'POST', cuerpo: { username, password }, manejar401: false, avisar: false,
    })
    // El servidor descarta la sesion anterior al iniciar una nueva.
    guardarCierrePendiente(false)
    cambiarUsuario(datos)
    return datos
  }

  /** Si el servidor no confirma el cierre, queda pendiente hasta lograrlo o iniciar otra sesion. */
  function enviarCierre(): Promise<void> {
    const versionInicial = version
    return api<void>('auth/logout', { metodo: 'POST', avisar: false })
      .then(() => guardarCierrePendiente(false))
      .catch((error: unknown) => {
        if (version === versionInicial) guardarCierrePendiente(true)
        throw error
      })
  }

  /**
   * Borra la sesion local aunque falle la peticion; en ese caso rechaza para que
   * la pantalla avise que la sesion pudo quedar abierta en el servidor.
   */
  function cerrarSesion(): Promise<void> {
    cambiarUsuario(null)
    const cierre: Promise<void> = enviarCierre()
      .finally(() => { if (cierreEnCurso === cierre) cierreEnCurso = null })
    cierreEnCurso = cierre
    return cierre
  }

  function limpiar(): void {
    cambiarUsuario(null)
  }

  return { usuario, verificada, autenticado, rol, tieneRol, cargar, iniciarSesion, cerrarSesion, limpiar }
})

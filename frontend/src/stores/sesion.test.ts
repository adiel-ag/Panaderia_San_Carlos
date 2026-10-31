import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { configurarCliente, ApiError } from '../api/cliente'
import type { Usuario } from '../api/tipos'
import { useSesionStore } from './sesion'

const usuario: Usuario = {
  idUsuario: 7, username: 'ana', nombre: 'Ana', rol: 'GERENTE', idSucursal: 2,
}
const fetchMock = vi.fn()
const alNoAutenticado = vi.fn()
const alAviso = vi.fn()

function respuesta(status: number, cuerpo: unknown): Response {
  return new Response(JSON.stringify(cuerpo), { status })
}

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset()
  alNoAutenticado.mockReset()
  alAviso.mockReset()
  configurarCliente({ alNoAutenticado, alAviso })
})

afterEach(() => vi.unstubAllGlobals())

describe('sesión', () => {
  it('cargar obtiene el usuario y marca la verificación', async () => {
    fetchMock.mockResolvedValue(respuesta(200, usuario))
    const sesion = useSesionStore()
    expect(sesion.verificada).toBe(false)

    await sesion.cargar()

    expect(fetchMock.mock.calls[0][0]).toBe('api/auth/me')
    expect(sesion.usuario).toEqual(usuario)
    expect(sesion.verificada).toBe(true)
    expect(sesion.autenticado).toBe(true)
    expect(sesion.rol).toBe('GERENTE')
  })

  it('cargar trata 401 como sesión ausente', async () => {
    fetchMock.mockResolvedValue(respuesta(401, { status: 401, error: 'NO_AUTH', message: 'Sin sesión' }))
    const sesion = useSesionStore()

    await expect(sesion.cargar()).resolves.toBeUndefined()

    expect(sesion.usuario).toBeNull()
    expect(sesion.verificada).toBe(true)
    expect(alNoAutenticado).not.toHaveBeenCalled()
    expect(alAviso).not.toHaveBeenCalled()
  })

  it('cargar propaga 503 y deja la sesión verificada y vacía', async () => {
    fetchMock.mockResolvedValue(respuesta(503, { status: 503, error: 'DB', message: 'No disponible' }))
    const sesion = useSesionStore()

    await expect(sesion.cargar()).rejects.toMatchObject({ status: 503 })

    expect(sesion.usuario).toBeNull()
    expect(sesion.verificada).toBe(true)
    expect(alAviso).not.toHaveBeenCalled()
  })

  it('comparte la petición mientras cargar está en curso', async () => {
    let resolver!: (value: Response) => void
    fetchMock.mockReturnValue(new Promise<Response>((resolve) => { resolver = resolve }))
    const sesion = useSesionStore()

    const primera = sesion.cargar()
    const segunda = sesion.cargar()
    expect(fetchMock).toHaveBeenCalledOnce()
    resolver(respuesta(200, usuario))
    await Promise.all([primera, segunda])
    expect(sesion.usuario).toEqual(usuario)
  })

  it('una respuesta tardía de cargar no restaura una sesión cerrada', async () => {
    let resolver!: (value: Response) => void
    fetchMock.mockReturnValueOnce(new Promise<Response>((resolve) => { resolver = resolve }))
      .mockResolvedValueOnce(respuesta(200, { mensaje: 'Sesión cerrada' }))
    const sesion = useSesionStore()

    const carga = sesion.cargar()
    await sesion.cerrarSesion()
    resolver(respuesta(200, usuario))
    await carga

    expect(sesion.usuario).toBeNull()
    expect(sesion.verificada).toBe(true)
  })

  it('un 401 tardío de cargar no borra una sesión recién iniciada', async () => {
    let resolver!: (value: Response) => void
    fetchMock.mockReturnValueOnce(new Promise<Response>((resolve) => { resolver = resolve }))
      .mockResolvedValueOnce(respuesta(200, usuario))
    const sesion = useSesionStore()

    const carga = sesion.cargar()
    await sesion.iniciarSesion('ana', 'clave')
    resolver(respuesta(401, { status: 401, error: 'NO_AUTH', message: 'Sin sesión' }))
    await carga

    expect(sesion.usuario).toEqual(usuario)
  })

  it('cargar espera a que termine el cierre de sesión antes de consultar', async () => {
    let resolverCierre!: (value: Response) => void
    fetchMock.mockReturnValueOnce(new Promise<Response>((resolve) => { resolverCierre = resolve }))
      .mockResolvedValueOnce(respuesta(401, { status: 401, error: 'NO_AUTH', message: 'Sin sesión' }))
    const sesion = useSesionStore()

    const cierre = sesion.cerrarSesion()
    const carga = sesion.cargar()
    await Promise.resolve()
    expect(fetchMock).toHaveBeenCalledOnce()

    resolverCierre(respuesta(200, { mensaje: 'Sesión cerrada' }))
    await Promise.all([cierre, carga])
    expect(fetchMock.mock.calls[1][0]).toBe('api/auth/me')
    expect(sesion.usuario).toBeNull()
  })

  it('tras un cierre fallido cargar reintenta el cierre en vez de recuperar la sesión', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Sin red'))
      .mockRejectedValueOnce(new TypeError('Sin red'))
      .mockResolvedValueOnce(respuesta(200, { mensaje: 'Sesión cerrada' }))
      .mockResolvedValueOnce(respuesta(200, usuario))
    const sesion = useSesionStore()

    await expect(sesion.cerrarSesion()).rejects.toMatchObject({ status: 0 })
    await expect(sesion.cargar()).rejects.toMatchObject({ status: 0 })
    expect(sesion.usuario).toBeNull()
    await sesion.cargar()
    expect(sesion.usuario).toBeNull()
    await sesion.cargar()

    expect(fetchMock.mock.calls.map((llamada) => llamada[0])).toEqual(
      ['api/auth/logout', 'api/auth/logout', 'api/auth/logout', 'api/auth/me'])
    expect(sesion.usuario).toEqual(usuario)
  })

  it('un cierre fallido que termina después de iniciar sesión no deja el cierre pendiente', async () => {
    let rechazar!: (motivo: unknown) => void
    fetchMock.mockReturnValueOnce(new Promise<Response>((_resolve, reject) => { rechazar = reject }))
      .mockResolvedValueOnce(respuesta(200, usuario))
      .mockResolvedValueOnce(respuesta(200, usuario))
    const sesion = useSesionStore()

    const cierre = sesion.cerrarSesion()
    await sesion.iniciarSesion('ana', 'clave')
    rechazar(new TypeError('Sin red'))
    await expect(cierre).rejects.toMatchObject({ status: 0 })
    await sesion.cargar()

    expect(fetchMock.mock.calls[2][0]).toBe('api/auth/me')
    expect(sesion.usuario).toEqual(usuario)
  })

  it('el cierre pendiente sobrevive a una recarga de la página', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Sin red'))
      .mockResolvedValueOnce(respuesta(200, { mensaje: 'Sesión cerrada' }))
    await expect(useSesionStore().cerrarSesion()).rejects.toMatchObject({ status: 0 })

    // Una recarga crea una aplicación y un store nuevos.
    setActivePinia(createPinia())
    const recargada = useSesionStore()
    await recargada.cargar()

    expect(fetchMock.mock.calls[1][0]).toBe('api/auth/logout')
    expect(recargada.usuario).toBeNull()
    expect(localStorage.getItem('panaderia.cierrePendiente')).toBeNull()
  })

  it('sin localStorage el cierre pendiente queda en memoria', async () => {
    const getItem = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('bloqueado') })
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('bloqueado') })
    fetchMock.mockRejectedValueOnce(new TypeError('Sin red'))
      .mockResolvedValueOnce(respuesta(200, { mensaje: 'Sesión cerrada' }))
    const sesion = useSesionStore()

    await expect(sesion.cerrarSesion()).rejects.toMatchObject({ status: 0 })
    await sesion.cargar()

    expect(fetchMock.mock.calls[1][0]).toBe('api/auth/logout')
    getItem.mockRestore()
    setItem.mockRestore()
  })

  it('si localStorage lee pero no escribe, el cierre pendiente queda en memoria', async () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('cuota') })
    fetchMock.mockRejectedValueOnce(new TypeError('Sin red'))
      .mockResolvedValueOnce(respuesta(200, { mensaje: 'Sesión cerrada' }))
    const sesion = useSesionStore()

    await expect(sesion.cerrarSesion()).rejects.toMatchObject({ status: 0 })
    await sesion.cargar()

    expect(fetchMock.mock.calls[1][0]).toBe('api/auth/logout')
    setItem.mockRestore()
  })

  it('iniciar sesión cancela un cierre pendiente', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Sin red'))
      .mockResolvedValueOnce(respuesta(200, usuario))
      .mockResolvedValueOnce(respuesta(200, usuario))
    const sesion = useSesionStore()

    await expect(sesion.cerrarSesion()).rejects.toMatchObject({ status: 0 })
    await sesion.iniciarSesion('ana', 'clave')
    await sesion.cargar()

    expect(fetchMock.mock.calls[2][0]).toBe('api/auth/me')
    expect(sesion.usuario).toEqual(usuario)
  })

  it('cargar vuelve a consultar después de limpiar aunque la anterior siga en curso', async () => {
    fetchMock.mockReturnValueOnce(new Promise<Response>(() => {}))
      .mockResolvedValueOnce(respuesta(200, usuario))
    const sesion = useSesionStore()

    void sesion.cargar()
    sesion.limpiar()
    await sesion.cargar()

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(sesion.usuario).toEqual(usuario)
  })

  it('iniciarSesion guarda y devuelve el usuario', async () => {
    fetchMock.mockResolvedValue(respuesta(200, usuario))
    const sesion = useSesionStore()

    await expect(sesion.iniciarSesion('ana', 'clave')).resolves.toEqual(usuario)
    expect(fetchMock.mock.calls[0][0]).toBe('api/auth/login')
    expect(fetchMock.mock.calls[0][1].body).toBe('{"username":"ana","password":"clave"}')
    expect(sesion.usuario).toEqual(usuario)
  })

  it('iniciarSesion propaga 401 sin llamar al manejador global', async () => {
    fetchMock.mockResolvedValue(respuesta(401, { status: 401, error: 'LOGIN', message: 'Credenciales incorrectas' }))
    const sesion = useSesionStore()

    await expect(sesion.iniciarSesion('ana', 'mal')).rejects.toBeInstanceOf(ApiError)
    expect(sesion.usuario).toBeNull()
    expect(alNoAutenticado).not.toHaveBeenCalled()
    expect(alAviso).not.toHaveBeenCalled()
  })

  it('cerrarSesion limpia aunque falle la petición', async () => {
    fetchMock.mockResolvedValueOnce(respuesta(200, usuario))
      .mockRejectedValueOnce(new TypeError('Sin red'))
    const sesion = useSesionStore()
    await sesion.iniciarSesion('ana', 'clave')

    await expect(sesion.cerrarSesion()).rejects.toMatchObject({ status: 0 })

    expect(sesion.usuario).toBeNull()
    expect(fetchMock.mock.calls[1][0]).toBe('api/auth/logout')
    expect(alAviso).not.toHaveBeenCalled()
  })

  it('tieneRol compara uno o varios roles y limpiar borra el usuario', async () => {
    fetchMock.mockResolvedValue(respuesta(200, usuario))
    const sesion = useSesionStore()
    expect(sesion.tieneRol('GERENTE')).toBe(false)
    await sesion.iniciarSesion('ana', 'clave')

    expect(sesion.tieneRol('ADMINISTRADOR', 'GERENTE')).toBe(true)
    expect(sesion.tieneRol('BODEGA')).toBe(false)
    sesion.limpiar()
    expect(sesion.autenticado).toBe(false)
    expect(sesion.rol).toBeNull()
  })
})

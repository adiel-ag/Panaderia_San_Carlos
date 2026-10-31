import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api, configurarCliente } from './cliente'

const alNoAutenticado = vi.fn()
const alAviso = vi.fn()
const fetchMock = vi.fn()

function respuesta(status: number, cuerpo?: unknown, headers?: Record<string, string>): Response {
  return new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), { status, headers })
}

function error(status: number, message = 'Mensaje del servidor'): Response {
  return respuesta(status, { status, error: 'ERROR_API', message })
}

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset()
  alNoAutenticado.mockReset()
  alAviso.mockReset()
  configurarCliente({ alNoAutenticado, alAviso })
})

afterEach(() => vi.unstubAllGlobals())

describe('cliente API', () => {
  it('devuelve JSON con una URL relativa', async () => {
    const datos = { idUsuario: 1 }
    fetchMock.mockResolvedValue(respuesta(200, datos))

    await expect(api<typeof datos>('auth/me')).resolves.toEqual(datos)
    expect(fetchMock).toHaveBeenCalledWith('api/auth/me', {
      method: 'GET', credentials: 'same-origin', headers: { Accept: 'application/json' },
    })
  })

  it('construye y codifica la consulta omitiendo valores vacíos', async () => {
    fetchMock.mockResolvedValue(respuesta(200, {}))

    await api('productos', { consulta: { buscar: 'pan dulce & sal', pagina: 2, activo: false, vacio: '', nulo: null, ausente: undefined } })

    expect(fetchMock.mock.calls[0][0]).toBe('api/productos?buscar=pan+dulce+%26+sal&pagina=2&activo=false')
  })

  it('envía JSON con encabezados y credenciales', async () => {
    fetchMock.mockResolvedValue(respuesta(200, {}))

    await api('auth/login', { metodo: 'POST', cuerpo: { username: 'ana', password: 'clave' } })

    expect(fetchMock).toHaveBeenCalledWith('api/auth/login', {
      method: 'POST', credentials: 'same-origin',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: '{"username":"ana","password":"clave"}',
    })
  })

  it.each([
    ['204', respuesta(204)],
    ['longitud cero', respuesta(200, undefined, { 'content-length': '0' })],
  ])('devuelve undefined con cuerpo vacío: %s', async (_caso, respuestaVacia) => {
    fetchMock.mockResolvedValue(respuestaVacia)
    await expect(api('auth/logout')).resolves.toBeUndefined()
  })

  it('en 401 avisa al manejador sin mostrar toast', async () => {
    fetchMock.mockResolvedValue(error(401))
    await expect(api('auth/me')).rejects.toMatchObject({ status: 401, error: 'ERROR_API', message: 'Mensaje del servidor' })
    expect(alNoAutenticado).toHaveBeenCalledOnce()
    expect(alAviso).not.toHaveBeenCalled()
  })

  it('en 401 permite omitir el manejador', async () => {
    fetchMock.mockResolvedValue(error(401))
    await expect(api('auth/me', { manejar401: false })).rejects.toBeInstanceOf(ApiError)
    expect(alNoAutenticado).not.toHaveBeenCalled()
    expect(alAviso).not.toHaveBeenCalled()
  })

  it.each([
    [403, { severidad: 'warn', titulo: 'Sin permiso', detalle: 'No tiene permiso para esta acción.' }],
    [409, { severidad: 'warn', titulo: 'Conflicto', detalle: 'Mensaje del servidor' }],
    [503, { severidad: 'error', titulo: 'Servicio no disponible', detalle: 'Mensaje del servidor' }],
    [500, { severidad: 'error', titulo: 'Error del servidor', detalle: 'Ocurrió un error en el servidor.' }],
    [502, { severidad: 'error', titulo: 'Error del servidor', detalle: 'Ocurrió un error en el servidor.' }],
  ] as const)('muestra el aviso correspondiente para %i', async (status, aviso) => {
    fetchMock.mockResolvedValue(error(status))
    await expect(api('recurso')).rejects.toMatchObject({ status })
    expect(alAviso).toHaveBeenCalledExactlyOnceWith(aviso)
  })

  it.each([400, 404])('no muestra aviso para %i', async (status) => {
    fetchMock.mockResolvedValue(error(status))
    await expect(api('recurso')).rejects.toMatchObject({ status })
    expect(alAviso).not.toHaveBeenCalled()
  })

  it('avisar false suprime avisos', async () => {
    fetchMock.mockResolvedValue(error(503))
    await expect(api('recurso', { avisar: false })).rejects.toMatchObject({ status: 503 })
    expect(alAviso).not.toHaveBeenCalled()
  })

  it('convierte un fallo de red en ApiError de estado cero', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))
    await expect(api('recurso')).rejects.toMatchObject({ status: 0, message: 'No se pudo conectar con el servidor.' })
    expect(alAviso).toHaveBeenCalledExactlyOnceWith({
      severidad: 'error', titulo: 'Sin conexión', detalle: 'No se pudo conectar con el servidor.',
    })
  })

  it('usa un mensaje genérico si el error no es JSON válido', async () => {
    fetchMock.mockResolvedValue(new Response('<html>error</html>', { status: 500 }))
    await expect(api('recurso')).rejects.toMatchObject({
      status: 500, message: 'Ocurrió un error al procesar la respuesta del servidor.',
    })
  })
})

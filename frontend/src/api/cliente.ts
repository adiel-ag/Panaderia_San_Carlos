import type { ApiErrorBody } from './tipos'

type Aviso = {
  severidad: 'warn' | 'error'
  titulo: string
  detalle: string
}

type Manejadores = {
  alNoAutenticado: () => void
  alAviso: (aviso: Aviso) => void
}

type Opciones = {
  metodo?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  cuerpo?: unknown
  consulta?: Record<string, string | number | boolean | null | undefined>
  manejar401?: boolean
  avisar?: boolean
}

const mensajeRespuestaInvalida = 'Ocurrió un error al procesar la respuesta del servidor.'
let manejadores: Manejadores | undefined

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly error: string,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export function configurarCliente(nuevosManejadores: Manejadores): void {
  manejadores = nuevosManejadores
}

function leerError(datos: unknown, status: number): ApiErrorBody {
  if (
    typeof datos === 'object' && datos !== null &&
    'error' in datos && typeof datos.error === 'string' &&
    'message' in datos && typeof datos.message === 'string'
  ) {
    return { status, error: datos.error, message: datos.message }
  }
  return { status, error: 'RESPUESTA_INVALIDA', message: mensajeRespuestaInvalida }
}

function avisarError(error: ApiError): void {
  if (error.status === 403) {
    manejadores?.alAviso({ severidad: 'warn', titulo: 'Sin permiso', detalle: 'No tiene permiso para esta acción.' })
  } else if (error.status === 409) {
    manejadores?.alAviso({ severidad: 'warn', titulo: 'Conflicto', detalle: error.message })
  } else if (error.status === 503) {
    manejadores?.alAviso({ severidad: 'error', titulo: 'Servicio no disponible', detalle: error.message })
  } else if (error.status === 0) {
    manejadores?.alAviso({ severidad: 'error', titulo: 'Sin conexión', detalle: 'No se pudo conectar con el servidor.' })
  } else if (error.status >= 500) {
    manejadores?.alAviso({ severidad: 'error', titulo: 'Error del servidor', detalle: 'Ocurrió un error en el servidor.' })
  }
}

export async function api<T>(ruta: string, opciones: Opciones = {}): Promise<T> {
  const consulta = new URLSearchParams()
  for (const [clave, valor] of Object.entries(opciones.consulta ?? {})) {
    if (valor !== null && valor !== undefined && valor !== '') consulta.append(clave, String(valor))
  }
  const parametros = consulta.toString()
  const url = `api/${ruta}${parametros ? `?${parametros}` : ''}`
  const tieneCuerpo = opciones.cuerpo !== undefined
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (tieneCuerpo) headers['Content-Type'] = 'application/json'

  let respuesta: Response
  try {
    respuesta = await fetch(url, {
      method: opciones.metodo ?? 'GET',
      credentials: 'same-origin',
      headers,
      ...(tieneCuerpo ? { body: JSON.stringify(opciones.cuerpo) } : {}),
    })
  } catch {
    const error = new ApiError(0, 'SIN_CONEXION', 'No se pudo conectar con el servidor.')
    if (opciones.avisar !== false) avisarError(error)
    throw error
  }

  if (!respuesta.ok) {
    let datos: unknown
    try {
      datos = await respuesta.json()
    } catch {
      datos = undefined
    }
    const cuerpo = leerError(datos, respuesta.status)
    const error = new ApiError(cuerpo.status, cuerpo.error, cuerpo.message)
    if (error.status === 401) {
      if (opciones.manejar401 !== false) manejadores?.alNoAutenticado()
    } else if (opciones.avisar !== false) {
      avisarError(error)
    }
    throw error
  }

  if (respuesta.status === 204 || respuesta.headers.get('content-length') === '0') {
    return undefined as T
  }
  try {
    return await respuesta.json() as T
  } catch {
    throw new ApiError(respuesta.status, 'RESPUESTA_INVALIDA', mensajeRespuestaInvalida)
  }
}

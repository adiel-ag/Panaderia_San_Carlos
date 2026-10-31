import { beforeEach, describe, expect, it, vi } from 'vitest'

type Cambio = (event: { matches: boolean }) => void

async function cargarTema(preferencia: string | null, sistemaOscuro: boolean) {
  vi.resetModules()
  document.documentElement.classList.remove('tema-oscuro')
  document.documentElement.style.colorScheme = ''
  localStorage.clear()
  if (preferencia) localStorage.setItem('panaderia.tema', preferencia)
  let alCambiar: Cambio | undefined
  const media = {
    matches: sistemaOscuro,
    addEventListener: vi.fn((_tipo: string, listener: Cambio) => { alCambiar = listener }),
  }
  vi.stubGlobal('matchMedia', vi.fn(() => media))
  const tema = await import('./useTema')
  tema.iniciarTema()
  return { tema, media, cambiarSistema: (matches: boolean) => alCambiar?.({ matches }) }
}

describe('useTema', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('da prioridad a la preferencia guardada', async () => {
    const { tema } = await cargarTema('claro', true)
    expect(tema.useTema().oscuro.value).toBe(false)
    expect(document.documentElement.classList.contains('tema-oscuro')).toBe(false)
  })

  it('usa la preferencia del sistema sin una elección guardada', async () => {
    const { tema } = await cargarTema(null, true)
    expect(tema.useTema().oscuro.value).toBe(true)
    expect(document.documentElement.style.colorScheme).toBe('dark')
  })

  it('alterna, aplica la clase y guarda la elección', async () => {
    const { tema } = await cargarTema(null, false)
    tema.useTema().alternar()
    expect(tema.useTema().oscuro.value).toBe(true)
    expect(document.documentElement.classList.contains('tema-oscuro')).toBe(true)
    expect(localStorage.getItem('panaderia.tema')).toBe('oscuro')
  })

  it('funciona si localStorage falla', async () => {
    const lectura = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('bloqueado') })
    const escritura = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('bloqueado') })
    vi.resetModules()
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false, addEventListener: vi.fn() })))
    const tema = await import('./useTema')
    expect(() => { tema.iniciarTema(); tema.useTema().alternar() }).not.toThrow()
    expect(tema.useTema().oscuro.value).toBe(true)
    lectura.mockRestore()
    escritura.mockRestore()
  })

  it('usa modo claro si matchMedia no está disponible', async () => {
    vi.resetModules()
    localStorage.clear()
    vi.stubGlobal('matchMedia', vi.fn(() => { throw new Error('no disponible') }))
    const tema = await import('./useTema')
    expect(() => tema.iniciarTema()).not.toThrow()
    expect(tema.useTema().oscuro.value).toBe(false)
  })

  it('sigue los cambios del sistema solo sin elección guardada', async () => {
    const libre = await cargarTema(null, false)
    libre.cambiarSistema(true)
    expect(libre.tema.useTema().oscuro.value).toBe(true)
    const fijo = await cargarTema('claro', false)
    fijo.cambiarSistema(true)
    expect(fijo.tema.useTema().oscuro.value).toBe(false)
  })
})

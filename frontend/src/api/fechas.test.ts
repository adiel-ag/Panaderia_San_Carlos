import { afterEach, describe, expect, it, vi } from 'vitest'
import { finDeSemana, formatearFecha, formatearFechaHora, hoyLocal, sumarDias } from './fechas'

const zonaOriginal = process.env.TZ
afterEach(() => {
  vi.useRealTimers()
  if (zonaOriginal === undefined) delete process.env.TZ
  else process.env.TZ = zonaOriginal
})

describe('fechas locales', () => {
  it('usa el día local aunque UTC ya esté en el siguiente día', () => {
    process.env.TZ = 'America/Mexico_City'
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-01T05:30:00Z'))
    expect(hoyLocal()).toBe('2026-10-31')
  })

  it('calcula mañana y el domingo de la semana actual', () => {
    expect(sumarDias('2026-10-31', 1)).toBe('2026-11-01')
    expect(finDeSemana('2026-10-31')).toBe('2026-11-01')
    expect(finDeSemana('2026-11-01')).toBe('2026-11-01')
  })

  it('muestra una fecha sin desplazarla por zona horaria', () => {
    process.env.TZ = 'America/Mexico_City'
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 9, 31, 12))
    // El año solo aparece cuando no es el año en curso.
    expect(formatearFecha('2026-10-31')).toBe('sáb 31 oct')
    expect(formatearFecha('2025-12-31')).toBe('mié 31 dic 2025')
    vi.useRealTimers()
  })

  it('muestra la hora en formato de 24 horas', () => {
    expect(formatearFechaHora('2026-10-30T17:40:00')).toContain('17:40')
  })
})

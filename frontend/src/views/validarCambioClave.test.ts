import { describe, expect, it } from 'vitest'
import { validarCambioClave } from './validarCambioClave'

describe('validarCambioClave', () => {
  it('exige los tres campos', () => {
    expect(validarCambioClave('', '', '')).toEqual({
      actual: 'Ingrese la contraseña actual.',
      nueva: 'Ingrese la contraseña nueva.',
      confirmar: 'Confirme la contraseña nueva.',
    })
  })

  it.each(['1234567', 'x'.repeat(129)])('rechaza longitud inválida', (nueva) => {
    expect(validarCambioClave('anterior123', nueva, nueva).nueva)
      .toBe('La contraseña nueva debe tener entre 8 y 128 caracteres.')
  })

  it('exige una contraseña distinta', () => {
    expect(validarCambioClave('anterior123', 'anterior123', 'anterior123').nueva)
      .toBe('La contraseña nueva debe ser distinta de la actual.')
  })

  it('exige confirmación igual', () => {
    expect(validarCambioClave('anterior123', 'nueva12345', 'otra12345').confirmar)
      .toBe('Las contraseñas no coinciden.')
  })

  it('acepta los límites válidos', () => {
    expect(validarCambioClave('anterior123', 'x'.repeat(8), 'x'.repeat(8))).toEqual({})
    expect(validarCambioClave('anterior123', 'x'.repeat(128), 'x'.repeat(128))).toEqual({})
  })
})

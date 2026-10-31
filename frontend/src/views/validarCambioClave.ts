export type ErroresCambioClave = {
  actual?: string
  nueva?: string
  confirmar?: string
}

export function validarCambioClave(actual: string, nueva: string, confirmar: string): ErroresCambioClave {
  const errores: ErroresCambioClave = {}
  if (!actual) errores.actual = 'Ingrese la contraseña actual.'
  if (!nueva) errores.nueva = 'Ingrese la contraseña nueva.'
  else if (nueva.length < 8 || nueva.length > 128) errores.nueva = 'La contraseña nueva debe tener entre 8 y 128 caracteres.'
  else if (nueva === actual) errores.nueva = 'La contraseña nueva debe ser distinta de la actual.'
  if (!confirmar) errores.confirmar = 'Confirme la contraseña nueva.'
  else if (confirmar !== nueva) errores.confirmar = 'Las contraseñas no coinciden.'
  return errores
}

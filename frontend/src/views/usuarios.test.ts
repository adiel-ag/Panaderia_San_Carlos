import { describe, expect, it } from 'vitest'
import { formatearFechaHora } from '../api/fechas'
import type { UsuarioAdmin } from '../api/usuarios'
import {
  cambiarFiltros, consultaUsuarios, etiquetaAcceso, filtrarUsuarios, filtrosPorDefecto, leerFiltrosUsuarios, normalizarUsuario,
  queryFiltrosUsuarios, textoResumenUsuarios, validarClave, validarUsuario, type FormularioUsuario,
} from './usuarios'

const base = { rol: null, estado: 'ACTIVO', sucursal: null, q: '', pagina: 1 }
const valido: FormularioUsuario = { nombre: 'Ana López', username: 'ana.lopez', rol: 'GERENTE', idSucursal: null, password: 'secreta123', confirmacion: 'secreta123' }
function usuario(idUsuario: number, nombre: string, username: string): UsuarioAdmin {
  return { idUsuario, nombre, username, rol: 'GERENTE', idSucursal: null, nombreSucursal: null, estado: 'ACTIVO',
    fechaCreacion: '2026-10-01T08:00:00', ultimoAcceso: null, bloqueado: false, tieneClave: true }
}

describe('filtros de usuarios', () => {
  it('usa valores por defecto para query vacía o inválida', () => {
    expect(leerFiltrosUsuarios({})).toEqual(base)
    expect(leerFiltrosUsuarios({ rol: 'JEFE', estado: 'BORRADO', sucursal: 'x', pagina: '0' })).toEqual(base)
    expect(leerFiltrosUsuarios({ rol: ['GERENTE'], estado: ['INACTIVO'], sucursal: '-2', q: ['ana'], pagina: '1.5' })).toEqual(base)
    expect(leerFiltrosUsuarios({ rol: 'gerente', sucursal: '0' })).toEqual(base)
  })

  it('restaura filtros válidos y los vuelve a serializar', () => {
    const filtros = leerFiltrosUsuarios({ rol: 'GERENTE', estado: 'ACTIVO', sucursal: '2', q: ' ana ', pagina: '2' })
    expect(filtros).toEqual({ rol: 'GERENTE', estado: 'ACTIVO', sucursal: 2, q: 'ana', pagina: 2 })
    // Activos es el valor por defecto y no se escribe.
    expect(queryFiltrosUsuarios(filtros)).toEqual({ rol: 'GERENTE', sucursal: '2', q: 'ana', pagina: '2' })
    expect(queryFiltrosUsuarios({ ...filtros, estado: 'INACTIVO' })).toMatchObject({ estado: 'INACTIVO' })
    expect(leerFiltrosUsuarios({ estado: 'TODOS' }).estado).toBe('TODOS')
    expect(queryFiltrosUsuarios({ ...base, estado: 'TODOS' } as never)).toEqual({ estado: 'TODOS' })
    expect(queryFiltrosUsuarios(base as never)).toEqual({})
    expect(leerFiltrosUsuarios({ q: 'a'.repeat(150) }).q).toHaveLength(100)
  })

  it('vuelve a la página 1 al cambiar un filtro del servidor, no al buscar', () => {
    const filtros = { ...base, pagina: 3 } as never
    expect(cambiarFiltros(filtros, { rol: 'BODEGA' })).toMatchObject({ rol: 'BODEGA', pagina: 1 })
    expect(cambiarFiltros(filtros, { estado: 'TODOS' }).pagina).toBe(1)
    expect(cambiarFiltros(filtros, { sucursal: 4 }).pagina).toBe(1)
    expect(cambiarFiltros(filtros, { q: 'ana' })).toMatchObject({ q: 'ana', pagina: 3 })
  })

  it('arma la consulta sin la búsqueda local', () => {
    expect(consultaUsuarios(base as never)).toEqual({ rol: undefined, estado: 'ACTIVO', idSucursal: undefined })
    expect(consultaUsuarios({ rol: 'SUCURSAL', estado: 'TODOS', sucursal: 2, q: 'ana', pagina: 2 })).toEqual({ rol: 'SUCURSAL', estado: undefined, idSucursal: 2 })
    expect(filtrosPorDefecto(base as never)).toBe(true)
    expect(filtrosPorDefecto({ ...base, q: 'x' } as never)).toBe(false)
    expect(filtrosPorDefecto({ ...base, estado: 'INACTIVO' } as never)).toBe(false)
  })

  it('filtra la página por nombre o usuario sin mayúsculas ni tildes', () => {
    const lista = [usuario(1, 'Ana López', 'ana'), usuario(2, 'Luis Pérez', 'lperez'), usuario(3, 'Marta Ruiz', 'mruiz')]
    expect(filtrarUsuarios(lista, '')).toBe(lista)
    expect(filtrarUsuarios(lista, 'LOPEZ').map((item) => item.idUsuario)).toEqual([1])
    expect(filtrarUsuarios(lista, 'perez').map((item) => item.idUsuario)).toEqual([2])
    expect(filtrarUsuarios(lista, 'mru').map((item) => item.idUsuario)).toEqual([3])
    expect(filtrarUsuarios(lista, 'zz')).toEqual([])
  })
})

describe('validarUsuario', () => {
  it('acepta un formulario válido en ambos modos', () => {
    expect(validarUsuario(valido, 'crear')).toEqual({})
    expect(validarUsuario({ ...valido, rol: 'SUCURSAL', idSucursal: 2 }, 'crear')).toEqual({})
    expect(validarUsuario({ ...valido, password: '', confirmacion: '' }, 'editar')).toEqual({})
  })

  it('valida el nombre', () => {
    for (const modo of ['crear', 'editar'] as const) {
      expect(validarUsuario({ ...valido, nombre: '   ' }, modo).nombre).toBe('Ingrese el nombre.')
      expect(validarUsuario({ ...valido, nombre: 'a'.repeat(101) }, modo).nombre).toBe('El nombre no puede superar 100 caracteres.')
      expect(validarUsuario({ ...valido, nombre: ` ${'a'.repeat(100)} ` }, modo).nombre).toBeUndefined()
    }
  })

  it('valida el formato del usuario solo al crear', () => {
    expect(validarUsuario({ ...valido, username: '  ' }, 'crear').username).toBe('Ingrese el usuario.')
    for (const username of ['ab', 'ana lopez', 'ana_lopez', 'añá', 'a'.repeat(51)]) {
      expect(validarUsuario({ ...valido, username }, 'crear').username).toBe('Usuario no válido. Use minúsculas, números, punto o guion (3 a 50).')
    }
    for (const username of ['abc', 'ana.lopez-2', ' Ana.Lopez ', 'a'.repeat(50)]) {
      expect(validarUsuario({ ...valido, username }, 'crear').username).toBeUndefined()
    }
    expect(validarUsuario({ ...valido, username: 'x' }, 'editar').username).toBeUndefined()
  })

  it('exige el rol y la sucursal solo para el rol Sucursal', () => {
    for (const modo of ['crear', 'editar'] as const) {
      expect(validarUsuario({ ...valido, rol: null }, modo).rol).toBe('Elija un rol.')
      expect(validarUsuario({ ...valido, rol: 'SUCURSAL', idSucursal: null }, modo).sucursal).toBe('Elija la sucursal del usuario.')
      expect(validarUsuario({ ...valido, rol: 'BODEGA', idSucursal: null }, modo).sucursal).toBeUndefined()
    }
  })

  it('valida la contraseña y su confirmación solo al crear', () => {
    expect(validarUsuario({ ...valido, password: '', confirmacion: '' }, 'crear')).toEqual({ password: 'Ingrese la contraseña.', confirmacion: 'Confirme la contraseña.' })
    expect(validarUsuario({ ...valido, password: '1234567', confirmacion: '1234567' }, 'crear').password).toBe('La contraseña debe tener entre 8 y 128 caracteres.')
    expect(validarUsuario({ ...valido, password: 'a'.repeat(129), confirmacion: 'a'.repeat(129) }, 'crear').password).toBe('La contraseña debe tener entre 8 y 128 caracteres.')
    expect(validarUsuario({ ...valido, password: 'a'.repeat(128), confirmacion: 'a'.repeat(128) }, 'crear')).toEqual({})
    expect(validarUsuario({ ...valido, confirmacion: 'otra12345' }, 'crear').confirmacion).toBe('Las contraseñas no coinciden.')
    expect(validarUsuario({ ...valido, password: '1', confirmacion: '2' }, 'editar')).toEqual({})
    expect(validarClave('12345678', '12345678')).toEqual({})
  })
})

describe('normalizarUsuario', () => {
  it('recorta, pasa el usuario a minúsculas y quita la sucursal si el rol no la lleva', () => {
    expect(normalizarUsuario({ ...valido, nombre: '  Ana López ', username: ' Ana.Lopez ', idSucursal: 3 })).toEqual({
      nombre: 'Ana López', username: 'ana.lopez', rol: 'GERENTE', idSucursal: null, password: 'secreta123',
    })
    expect(normalizarUsuario({ ...valido, rol: 'SUCURSAL', idSucursal: 3, password: ' con espacios ' })).toMatchObject({ idSucursal: 3, password: ' con espacios ' })
  })

  it('en edición solo envía nombre, rol y sucursal', () => {
    expect(normalizarUsuario({ ...valido, nombre: ' Ana ', rol: 'SUCURSAL', idSucursal: 2 }, 'editar')).toEqual({ nombre: 'Ana', rol: 'SUCURSAL', idSucursal: 2 })
    expect(normalizarUsuario({ ...valido, rol: 'ADMINISTRADOR', idSucursal: 2 }, 'editar')).toEqual({ nombre: 'Ana López', rol: 'ADMINISTRADOR', idSucursal: null })
  })
})

describe('textos de usuarios', () => {
  it('resume activos y bloqueados en singular o plural', () => {
    expect(textoResumenUsuarios(12, 0)).toBe('12 usuarios activos')
    expect(textoResumenUsuarios(1, 0)).toBe('1 usuario activo')
    expect(textoResumenUsuarios(12, 1)).toBe('12 usuarios activos · 1 bloqueado')
    expect(textoResumenUsuarios(12, 3)).toBe('12 usuarios activos · 3 bloqueados')
    expect(textoResumenUsuarios(0, 0)).toBe('0 usuarios activos')
  })

  it('muestra el último acceso o Nunca', () => {
    expect(etiquetaAcceso(null)).toBe('Nunca')
    expect(etiquetaAcceso('2026-10-30T14:05:00')).toBe(formatearFechaHora('2026-10-30T14:05:00'))
  })
})

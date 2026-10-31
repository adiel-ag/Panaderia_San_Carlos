import { describe, expect, it } from 'vitest'
import type { RecetaResumen } from '../api/recetas'
import {
  construirSolicitud, consumoPorUnidad, estadoReceta, filtrarRecetas, formatearConsumo, formularioDesdeReceta, formularioVacio, hayCambios,
  hayErrores, leerFiltroRecetas, queryFiltroRecetas, siguienteVersion, textoResumenRecetas, ultimaVersion, validarReceta, type FormularioReceta,
} from './recetas'

function resumen(idProducto: number, recetasVigentes: number): RecetaResumen {
  const vigente = recetasVigentes === 1
  return {
    idProducto, nombreProducto: `Producto ${idProducto}`, unidad: 'UND', idReceta: vigente ? idProducto * 10 : null, version: vigente ? 2 : null,
    rendimiento: vigente ? 100 : null, cantidadIngredientes: vigente ? 3 : 0, fechaInicio: vigente ? '2026-10-01' : null, recetasVigentes,
  }
}
function formulario(rendimiento: number | null, lineas: Array<[number | null, number | null]>): FormularioReceta {
  return { rendimiento, lineas: lineas.map(([idInsumo, cantidad], indice) => ({ clave: indice + 1, idInsumo, cantidad })) }
}
const mensajeCantidad = 'Ingrese una cantidad mayor que 0, con hasta 3 decimales y máximo 999999999.999.'
const mensajeRendimiento = 'Ingrese un rendimiento mayor que 0, con hasta 3 decimales y máximo 999999999.999.'

describe('estadoReceta', () => {
  it('distingue vigente, sin receta y duplicada', () => {
    expect(estadoReceta(resumen(1, 1))).toBe('vigente')
    expect(estadoReceta(resumen(1, 0))).toBe('sin-receta')
    expect(estadoReceta(resumen(1, 2))).toBe('ambigua')
  })
})

describe('filtro de recetas', () => {
  it('lee y escribe el filtro de la query', () => {
    expect(leerFiltroRecetas({})).toBe('todos')
    expect(leerFiltroRecetas({ filtro: 'sin-receta' })).toBe('sin-receta')
    expect(leerFiltroRecetas({ filtro: 'otro' })).toBe('todos')
    expect(queryFiltroRecetas('todos')).toEqual({})
    expect(queryFiltroRecetas('con-receta')).toEqual({ filtro: 'con-receta' })
  })

  it('separa lo que se puede producir de lo que no', () => {
    const lista = [resumen(1, 1), resumen(2, 0), resumen(3, 2)]
    expect(filtrarRecetas(lista, 'todos')).toHaveLength(3)
    expect(filtrarRecetas(lista, 'con-receta').map((item) => item.idProducto)).toEqual([1])
    expect(filtrarRecetas(lista, 'sin-receta').map((item) => item.idProducto)).toEqual([2, 3])
  })
})

describe('textoResumenRecetas', () => {
  it('cuenta los listos y los que faltan', () => {
    const lista = [resumen(1, 1), resumen(2, 1), resumen(3, 1), ...[4, 5, 6, 7, 8, 9].map((id) => resumen(id, 0))]
    expect(textoResumenRecetas(lista)).toBe('3 de 9 productos listos para producir · 6 sin receta')
  })

  it('agrega las recetas duplicadas', () => {
    expect(textoResumenRecetas([resumen(1, 1), resumen(2, 0), resumen(3, 2)])).toBe('1 de 3 productos listos para producir · 1 sin receta · 1 con recetas duplicadas')
    expect(textoResumenRecetas([resumen(1, 2), resumen(2, 3)])).toBe('0 de 2 productos listos para producir · 2 con recetas duplicadas')
  })

  it('usa singular con un solo producto', () => {
    expect(textoResumenRecetas([resumen(1, 0)])).toBe('0 de 1 producto listo para producir · 1 sin receta')
  })

  it('avisa cuando todos están listos o no hay productos', () => {
    expect(textoResumenRecetas([resumen(1, 1), resumen(2, 1)])).toBe('Todos los productos tienen receta')
    expect(textoResumenRecetas([])).toBe('No hay productos finales activos')
  })
})

describe('consumoPorUnidad', () => {
  it('divide la cantidad de la tanda entre el rendimiento', () => {
    expect(consumoPorUnidad(0.5, 100)).toBe(0.005)
    expect(consumoPorUnidad(10, 4)).toBe(2.5)
    expect(consumoPorUnidad(1, 3)).toBe(0.333333)
  })

  it('no calcula con datos inválidos', () => {
    expect(consumoPorUnidad(1, 0)).toBeNull()
    expect(consumoPorUnidad(null, 10)).toBeNull()
    expect(consumoPorUnidad(1, null)).toBeNull()
    expect(consumoPorUnidad(-1, 10)).toBeNull()
  })

  it('formatea hasta 4 decimales sin perder los consumos muy pequeños', () => {
    expect(formatearConsumo(0.005)).toBe('0.005')
    expect(formatearConsumo(0.333333)).toBe('0.3333')
    expect(formatearConsumo(0.00001)).toBe('0.00001')
  })
})

describe('versiones', () => {
  it('toma la mayor versión guardada', () => {
    const versiones = [3, 1, 2].map((version) => ({ idReceta: version, version, rendimiento: 1, estado: 'INACTIVA' as const, fechaInicio: '2026-01-01', fechaFin: null, vigente: false, cantidadIngredientes: 1 }))
    expect(ultimaVersion(versiones)).toBe(3)
    expect(siguienteVersion(versiones)).toBe(4)
    expect(siguienteVersion([])).toBe(1)
  })
})

describe('formulario de receta', () => {
  it('parte vacío con una línea', () => {
    expect(formularioVacio()).toEqual({ rendimiento: null, lineas: [{ clave: 1, idInsumo: null, cantidad: null }] })
  })

  it('se llena con la versión actual', () => {
    const detalle = { rendimiento: 100, ingredientes: [
      { idInsumo: 4, nombre: 'Harina', unidad: 'KG', cantidad: 25 }, { idInsumo: 7, nombre: 'Sal', unidad: 'KG', cantidad: 0.5 },
    ] }
    expect(formularioDesdeReceta(detalle)).toEqual({ rendimiento: 100, lineas: [
      { clave: 1, idInsumo: 4, cantidad: 25 }, { clave: 2, idInsumo: 7, cantidad: 0.5 },
    ] })
    expect(formularioDesdeReceta({ rendimiento: 5, ingredientes: [] }).lineas).toHaveLength(1)
  })

  it('acepta una receta válida', () => {
    const errores = validarReceta(formulario(100, [[4, 25], [7, 0.5]]), new Set([4, 7]))
    expect(hayErrores(errores)).toBe(false)
  })

  it('valida el rendimiento', () => {
    for (const valor of [null, 0, -1, 1.0001, 1000000000]) {
      expect(validarReceta(formulario(valor, [[4, 1]])).rendimiento).toBe(mensajeRendimiento)
    }
    expect(validarReceta(formulario(999999999.999, [[4, 1]])).rendimiento).toBeUndefined()
  })

  it('exige entre 1 y 50 ingredientes', () => {
    expect(validarReceta(formulario(1, [])).ingredientes).toBe('Agregue al menos un ingrediente.')
    const muchos = formulario(1, Array.from({ length: 51 }, (_, indice) => [indice + 1, 1] as [number, number]))
    expect(validarReceta(muchos).ingredientes).toBe('Una receta admite como máximo 50 ingredientes.')
    muchos.lineas.pop()
    expect(validarReceta(muchos).ingredientes).toBeUndefined()
  })

  it('valida insumo y cantidad de cada línea', () => {
    const errores = validarReceta(formulario(1, [[null, 1], [4, 0], [4, 1.5], [9, 2.0005]]), new Set([4]))
    expect(errores.lineas[1]).toEqual({ insumo: 'Elija un insumo.' })
    expect(errores.lineas[2]).toEqual({ cantidad: mensajeCantidad })
    expect(errores.lineas[3]).toEqual({ insumo: 'Insumo repetido.' })
    expect(errores.lineas[4]).toEqual({ insumo: 'Este insumo ya no está activo; elija otro.', cantidad: mensajeCantidad })
  })

  it('construye la solicitud con la versión de partida', () => {
    expect(construirSolicitud(9, formulario(100, [[4, 25], [7, 0.5]]), 2)).toEqual({
      idProducto: 9, rendimiento: 100, versionBase: 2, ingredientes: [{ idInsumo: 4, cantidad: 25 }, { idInsumo: 7, cantidad: 0.5 }],
    })
    expect(construirSolicitud(9, formulario(10, [[4, 1]]), null).versionBase).toBeNull()
  })

  it('detecta cambios respecto de lo inicial', () => {
    const inicial = formulario(100, [[4, 25], [7, 0.5]])
    expect(hayCambios(formulario(100, [[4, 25], [7, 0.5]]), inicial)).toBe(false)
    expect(hayCambios(formulario(100.0000001, [[4, 25], [7, 0.5]]), inicial)).toBe(false)
    expect(hayCambios(formulario(90, [[4, 25], [7, 0.5]]), inicial)).toBe(true)
    expect(hayCambios(formulario(100, [[4, 25], [7, 0.6]]), inicial)).toBe(true)
    expect(hayCambios(formulario(100, [[4, 25], [8, 0.5]]), inicial)).toBe(true)
    expect(hayCambios(formulario(100, [[4, 25]]), inicial)).toBe(true)
    expect(hayCambios(formularioVacio(), formularioVacio())).toBe(false)
    expect(hayCambios(formulario(null, [[4, null]]), formularioVacio())).toBe(true)
  })
})

import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import EstadoPedido from './EstadoPedido.vue'
import { ESTADOS, etiquetaEstado } from '../api/pedidos'

const css = readFileSync(resolve(process.cwd(), 'src/tema/tokens.css'), 'utf8')
const bloques = css.split('.tema-oscuro')
const nombres = ['pendiente', 'confirmado', 'produccion', 'listo', 'despachado', 'cancelado']

function canal(valor: number): number {
  const normalizado = valor / 255
  return normalizado <= 0.04045 ? normalizado / 12.92 : ((normalizado + 0.055) / 1.055) ** 2.4
}
function luminancia(hex: string): number {
  const numeros = hex.match(/[\da-f]{2}/gi)!.map((par) => canal(parseInt(par, 16)))
  return numeros[0]! * .2126 + numeros[1]! * .7152 + numeros[2]! * .0722
}
function contraste(a: string, b: string): number {
  const valores = [luminancia(a), luminancia(b)].sort((x, y) => y - x)
  return (valores[0]! + .05) / (valores[1]! + .05)
}
function token(bloque: string, nombre: string, tipo: string): string {
  const valor = bloque.match(new RegExp(`--psc-estado-${nombre}-${tipo}:\\s*(#[0-9A-Fa-f]{6})`))?.[1]
  if (!valor) throw new Error(`Falta token ${nombre}-${tipo}`)
  return valor
}

describe('EstadoPedido', () => {
  it.each(ESTADOS)('presenta %s con etiqueta legible', (estado) => {
    expect(mount(EstadoPedido, { props: { estado } }).text()).toBe(etiquetaEstado(estado))
  })

  it.each(['claro', 'oscuro'] as const)('mantiene contraste 4.5:1 en tema %s', (tema) => {
    const bloque = tema === 'claro' ? bloques[0]! : bloques[1]!
    for (const nombre of nombres) {
      expect(contraste(token(bloque, nombre, 'texto'), token(bloque, nombre, 'fondo')), nombre).toBeGreaterThanOrEqual(4.5)
    }
  })
})

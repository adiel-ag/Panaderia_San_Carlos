import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { reactive } from 'vue'
import PrimeVue from 'primevue/config'
import RecetaView from './RecetaView.vue'

const api = vi.hoisted(() => ({
  listarRecetas: vi.fn(), obtenerReceta: vi.fn(), listarVersiones: vi.fn(), push: vi.fn(), sesion: { rol: 'PRODUCCION' },
}))
const route = reactive({ params: { idProducto: '9' }, query: {} as Record<string, string> })
vi.mock('../api/recetas', async (original) => ({ ...(await original<typeof import('../api/recetas')>()),
  listarRecetas: api.listarRecetas, obtenerReceta: api.obtenerReceta, listarVersiones: api.listarVersiones }))
vi.mock('../stores/sesion', () => ({ useSesionStore: () => api.sesion }))
vi.mock('vue-router', async (original) => ({
  ...(await original<typeof import('vue-router')>()), useRoute: () => route, useRouter: () => ({ push: api.push }),
}))

function resumen(recetasVigentes: number) {
  const vigente = recetasVigentes === 1
  return { idProducto: 9, nombreProducto: 'Pan francés', unidad: 'UND', idReceta: vigente ? 21 : null, version: vigente ? 2 : null,
    rendimiento: vigente ? 100 : null, cantidadIngredientes: vigente ? 2 : 0, fechaInicio: vigente ? '2026-10-01' : null, recetasVigentes }
}
const receta = {
  idReceta: 21, idProducto: 9, nombreProducto: 'Pan francés', unidad: 'UND', version: 2, rendimiento: 100, estado: 'ACTIVA',
  fechaInicio: '2026-10-01', fechaFin: null, vigente: true,
  ingredientes: [{ idInsumo: 4, nombre: 'Harina', unidad: 'KG', cantidad: 25 }, { idInsumo: 7, nombre: 'Sal', unidad: 'KG', cantidad: 0.5 }],
}
const anterior = { ...receta, idReceta: 20, version: 1, rendimiento: 80, estado: 'INACTIVA', fechaFin: '2026-10-01', vigente: false,
  ingredientes: [{ idInsumo: 4, nombre: 'Harina', unidad: 'KG', cantidad: 20 }] }
const versiones = [
  { idReceta: 21, version: 2, rendimiento: 100, estado: 'ACTIVA', fechaInicio: '2026-10-01', fechaFin: null, vigente: true, cantidadIngredientes: 2 },
  { idReceta: 20, version: 1, rendimiento: 80, estado: 'INACTIVA', fechaInicio: '2026-09-01', fechaFin: '2026-10-01', vigente: false, cantidadIngredientes: 1 },
]
const montajes: VueWrapper[] = []
async function montar() {
  const wrapper = mount(RecetaView, { global: { plugins: [PrimeVue], stubs: {
    RouterLink: { template: '<a><slot /></a>' },
    Dialog: { props: ['visible', 'header'], template: '<div v-if="visible" class="dialogo-stub"><h3>{{ header }}</h3><slot /></div>' },
  } } })
  montajes.push(wrapper)
  await flushPromises()
  return wrapper
}
function boton(wrapper: VueWrapper, texto: string) { return wrapper.findAll('button').find((item) => item.text() === texto) }
afterEach(() => { montajes.splice(0).forEach((wrapper) => wrapper.unmount()) })
beforeEach(() => {
  vi.clearAllMocks()
  api.sesion.rol = 'PRODUCCION'
  route.params.idProducto = '9'
  api.listarRecetas.mockResolvedValue([resumen(1)])
  api.obtenerReceta.mockImplementation((id: number) => Promise.resolve(id === 21 ? receta : anterior))
  api.listarVersiones.mockResolvedValue(versiones)
  api.push.mockResolvedValue(undefined)
})

describe('RecetaView', () => {
  it('muestra la receta vigente con el consumo por unidad', async () => {
    const wrapper = await montar()
    expect(api.obtenerReceta).toHaveBeenCalledWith(21)
    expect(api.listarVersiones).toHaveBeenCalledWith(9)
    expect(wrapper.find('h1').text()).toBe('Pan francés')
    expect(wrapper.find('.titulo-receta .estado-receta--vigente').exists()).toBe(true)
    const datos = wrapper.find('.datos-pedido').text()
    expect(datos).toContain('v2')
    expect(datos).toContain('100 u por tanda')
    const filas = wrapper.findAll('.tabla-ingredientes tbody tr')
    expect(filas).toHaveLength(2)
    expect(filas[0]!.text()).toContain('Harina')
    expect(filas[0]!.findAll('td')[1]!.text()).toBe('25kg')
    expect(filas[0]!.findAll('td')[2]!.text()).toBe('0.25kg')
    expect(filas[1]!.findAll('td')[2]!.text()).toBe('0.005kg')
  })

  it('ofrece una nueva versión a producción', async () => {
    const wrapper = await montar()
    await boton(wrapper, 'Nueva versión')!.trigger('click')
    expect(api.push).toHaveBeenCalledWith({ name: 'receta-nueva', params: { idProducto: 9 } })
  })

  it('lista el historial y abre los ingredientes de una versión', async () => {
    const wrapper = await montar()
    const filas = wrapper.findAll('.tabla-versiones tbody tr')
    expect(filas).toHaveLength(2)
    expect(filas[0]!.find('.estado-receta--vigente').text()).toBe('Vigente')
    expect(filas[1]!.text()).toContain('Inactiva')
    expect(filas[1]!.text()).toContain('80u')
    await filas[1]!.find('button[aria-label="Ver ingredientes de la versión 1"]').trigger('click')
    await flushPromises()
    expect(api.obtenerReceta).toHaveBeenCalledWith(20)
    const dialogo = wrapper.find('.dialogo-stub')
    expect(dialogo.text()).toContain('Versión 1')
    expect(dialogo.text()).toContain('Rinde 80 u por tanda')
    expect(dialogo.text()).toContain('Harina')
  })

  it('explica que un producto sin receta no se puede producir', async () => {
    api.listarRecetas.mockResolvedValue([resumen(0)])
    api.listarVersiones.mockResolvedValue([])
    const wrapper = await montar()
    expect(api.obtenerReceta).not.toHaveBeenCalled()
    expect(wrapper.find('.receta-vacia').text()).toContain('Este producto aún no tiene receta, por eso no se puede producir.')
    expect(wrapper.find('.receta-vacia .ilustracion-pan').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('Historial de versiones')
    await boton(wrapper, 'Crear receta')!.trigger('click')
    expect(api.push).toHaveBeenCalledWith({ name: 'receta-nueva', params: { idProducto: 9 } })
  })

  it('advierte cuando hay recetas duplicadas', async () => {
    api.listarRecetas.mockResolvedValue([resumen(2)])
    const wrapper = await montar()
    expect(wrapper.text()).toContain('Este producto tiene más de una receta activa')
    expect(wrapper.find('.estado-receta--ambigua').exists()).toBe(true)
    expect(boton(wrapper, 'Nueva versión')).toBeDefined()
  })

  it('deja a gerencia solo consultar', async () => {
    api.sesion.rol = 'GERENTE'
    const wrapper = await montar()
    expect(boton(wrapper, 'Nueva versión')).toBeUndefined()
    expect(wrapper.text()).toContain('Harina')
    api.listarRecetas.mockResolvedValue([resumen(0)])
    route.params.idProducto = '10'
    await flushPromises()
    route.params.idProducto = '9'
    await flushPromises()
    expect(wrapper.text()).toContain('Este producto aún no tiene receta')
    expect(boton(wrapper, 'Crear receta')).toBeUndefined()
  })

  it('avisa si el producto no existe', async () => {
    route.params.idProducto = '99'
    const wrapper = await montar()
    expect(wrapper.text()).toContain('No se encontró el producto.')
  })
})

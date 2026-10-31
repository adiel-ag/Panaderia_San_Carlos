import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { reactive } from 'vue'
import PrimeVue from 'primevue/config'
import SelectButton from 'primevue/selectbutton'
import RecetasView from './RecetasView.vue'

const { listarRecetas, replace, push, sesion } = vi.hoisted(() => ({
  listarRecetas: vi.fn(), replace: vi.fn(), push: vi.fn(), sesion: { rol: 'PRODUCCION' },
}))
const route = reactive({ query: {} as Record<string, string> })
vi.mock('../api/recetas', async (original) => ({ ...(await original<typeof import('../api/recetas')>()), listarRecetas }))
vi.mock('../stores/sesion', () => ({ useSesionStore: () => sesion }))
vi.mock('vue-router', async (original) => ({
  ...(await original<typeof import('vue-router')>()), useRoute: () => route, useRouter: () => ({ replace, push }),
}))

const lista = [
  { idProducto: 9, nombreProducto: 'Pan francés', unidad: 'UND', idReceta: 21, version: 2, rendimiento: 100, cantidadIngredientes: 4, fechaInicio: '2026-10-01', recetasVigentes: 1 },
  { idProducto: 10, nombreProducto: 'Pastel', unidad: 'KG', idReceta: null, version: null, rendimiento: null, cantidadIngredientes: 0, fechaInicio: null, recetasVigentes: 0 },
  { idProducto: 11, nombreProducto: 'Rosca', unidad: 'UND', idReceta: null, version: null, rendimiento: null, cantidadIngredientes: 0, fechaInicio: null, recetasVigentes: 2 },
]
const montajes: VueWrapper[] = []
async function montar() {
  const wrapper = mount(RecetasView, { global: { plugins: [PrimeVue], stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
  montajes.push(wrapper)
  await flushPromises()
  return wrapper
}
function filas(wrapper: VueWrapper) { return wrapper.findAll('tbody tr').filter((fila) => fila.find('td').exists() && !fila.find('.tabla-estado').exists()) }
afterEach(() => { montajes.splice(0).forEach((wrapper) => wrapper.unmount()) })
beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn(), addListener: vi.fn(), removeListener: vi.fn() }))
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })
  route.query = {}
  sesion.rol = 'PRODUCCION'
  listarRecetas.mockResolvedValue(lista)
  push.mockResolvedValue(undefined)
})

describe('RecetasView', () => {
  it('resume y muestra cada producto con su estado', async () => {
    const wrapper = await montar()
    expect(wrapper.text()).toContain('1 de 3 productos listos para producir · 1 sin receta · 1 con recetas duplicadas')
    const [pan, pastel, rosca] = filas(wrapper)
    expect(pan!.find('.estado-receta--vigente').text()).toBe('Vigente')
    expect(pan!.text()).toContain('v2')
    expect(pan!.text()).toContain('100u')
    expect(pastel!.find('.estado-receta--sin-receta').text()).toBe('Sin receta')
    expect(pastel!.text()).toContain('—')
    const duplicada = rosca!.find('.estado-receta--ambigua')
    expect(duplicada.text()).toBe('Duplicada')
    expect(duplicada.attributes('title')).toBe('Hay más de una receta activa; guarde una versión nueva para corregirlo')
  })

  it('filtra con la query y la cambia con replace', async () => {
    route.query = { filtro: 'con-receta' }
    const wrapper = await montar()
    expect(filas(wrapper)).toHaveLength(1)
    expect(wrapper.findComponent(SelectButton).props('modelValue')).toBe('con-receta')
    wrapper.findComponent(SelectButton).vm.$emit('update:modelValue', 'sin-receta')
    expect(replace).toHaveBeenCalledWith({ name: 'recetas', query: { filtro: 'sin-receta' } })
    route.query = { filtro: 'sin-receta' }
    await flushPromises()
    expect(filas(wrapper).map((fila) => fila.find('td').text())).toEqual(['Pastel', 'Rosca'])
    wrapper.findComponent(SelectButton).vm.$emit('update:modelValue', 'todos')
    expect(replace).toHaveBeenLastCalledWith({ name: 'recetas', query: {} })
  })

  it('ofrece crear receta o nueva versión a producción', async () => {
    const wrapper = await montar()
    expect(wrapper.find('[data-accion="receta-9"]').text()).toBe('Nueva versión')
    expect(wrapper.find('[data-accion="receta-10"]').text()).toBe('Crear receta')
    expect(wrapper.find('[data-accion="receta-11"]').text()).toBe('Nueva versión')
    await wrapper.find('[data-accion="receta-10"]').trigger('click')
    expect(push).toHaveBeenCalledWith({ name: 'receta-nueva', params: { idProducto: 10 } })
  })

  it('deja a gerencia solo consultar', async () => {
    sesion.rol = 'GERENTE'
    const wrapper = await montar()
    expect(wrapper.find('[data-accion^="receta-"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Crear receta')
    expect(wrapper.text()).not.toContain('Nueva versión')
  })

  it('abre el detalle al hacer clic en la fila', async () => {
    const wrapper = await montar()
    await filas(wrapper)[0]!.find('td:nth-child(3)').trigger('click')
    expect(push).toHaveBeenCalledWith({ name: 'receta', params: { idProducto: 9 } })
  })

  it('muestra el error y reintenta', async () => {
    listarRecetas.mockRejectedValueOnce(new Error('red'))
    const wrapper = await montar()
    expect(wrapper.text()).toContain('No se pudieron cargar las recetas.')
    await wrapper.findAll('button').find((boton) => boton.text() === 'Reintentar')!.trigger('click')
    await flushPromises()
    expect(listarRecetas).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('Pan francés')
  })

  it('avisa cuando el filtro no tiene productos', async () => {
    route.query = { filtro: 'sin-receta' }
    listarRecetas.mockResolvedValue([lista[0]])
    const wrapper = await montar()
    expect(wrapper.text()).toContain('Todos los productos tienen receta')
    expect(wrapper.text()).toContain('No hay productos con este filtro.')
  })
})

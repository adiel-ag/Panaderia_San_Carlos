import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import PrimeVue from 'primevue/config'
import SelectButton from 'primevue/selectbutton'
import Textarea from 'primevue/textarea'
import ProduccionesView from './ProduccionesView.vue'

const { listarProducciones, crearProduccion, pedidosDisponibles, planificarProduccion, replace, push, toast, sesion } = vi.hoisted(() => ({
  listarProducciones: vi.fn(), crearProduccion: vi.fn(), replace: vi.fn(), push: vi.fn(), toast: vi.fn(),
  pedidosDisponibles: vi.fn(), planificarProduccion: vi.fn(),
  sesion: { rol: 'PRODUCCION' },
}))
const route = reactive({ query: {} as Record<string, string>, fullPath: '/produccion' })
vi.mock('../api/producciones', async (original) => ({ ...(await original<typeof import('../api/producciones')>()), listarProducciones, crearProduccion, pedidosDisponibles, planificarProduccion }))
vi.mock('../stores/sesion', () => ({ useSesionStore: () => sesion }))
vi.mock('vue-router', async (original) => ({
  ...(await original<typeof import('vue-router')>()), useRoute: () => route, useRouter: () => ({ replace, push }),
}))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: toast }) }))

const vacia = { items: [], total: 0, pagina: 1, tamano: 20 }
const produccion = { idProduccion: 12, idUsuario: 1, nombreUsuario: 'Luis', fechaProduccion: '2026-10-31T06:30:00', estado: 'EN_PROCESO', observacion: null, cantidadPedidos: 3 }
const montajes: Array<{ unmount: () => void }> = []
function montar() {
  const wrapper = mount(ProduccionesView, { global: { plugins: [PrimeVue], stubs: {
    RouterLink: { template: '<a><slot /></a>' },
    Dialog: { props: ['visible'], template: '<div v-if="visible" class="dialogo-stub"><slot /></div>' },
  } } })
  montajes.push(wrapper)
  return wrapper
}
// Respuestas según la consulta: tamano 1 es el conteo del resumen.
function responder(conteos: { EN_PROCESO: number; PLANIFICADA: number }, lista = vacia) {
  listarProducciones.mockImplementation((estado: 'EN_PROCESO' | 'PLANIFICADA', _pagina: number, tamano: number) =>
    Promise.resolve(tamano === 1 ? { ...vacia, total: conteos[estado] } : lista))
}
afterEach(() => { montajes.splice(0).forEach((wrapper) => wrapper.unmount()) })
beforeEach(() => {
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn(), addListener: vi.fn(), removeListener: vi.fn() }))
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })
  vi.clearAllMocks()
  route.query = {}
  sesion.rol = 'PRODUCCION'
  responder({ EN_PROCESO: 0, PLANIFICADA: 0 })
  push.mockResolvedValue(undefined)
  pedidosDisponibles.mockResolvedValue([])
  planificarProduccion.mockResolvedValue({ idProduccion: 44 })
})

describe('ProduccionesView', () => {
  it('planifica los pedidos marcados y navega al detalle', async () => {
    pedidosDisponibles.mockResolvedValue([
      { idPedido: 7, nombreSucursal: 'Centro', cantidadProductos: 2 },
      { idPedido: 8, nombreSucursal: 'Norte', cantidadProductos: 1 },
    ])
    const wrapper = montar()
    await flushPromises()
    await wrapper.findAll('button').find((boton) => boton.text() === 'Planificar por fecha')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('#7 · Centro · 2 productos')
    const casillas = wrapper.findAll('input[type="checkbox"]')
    expect(casillas).toHaveLength(2)
    expect((casillas[0]!.element as HTMLInputElement).checked).toBe(true)
    await casillas[1]!.setValue(false)
    await wrapper.find('.dialogo-stub form').trigger('submit')
    await flushPromises()
    expect(planificarProduccion).toHaveBeenCalledWith(expect.objectContaining({ idPedidos: [7] }))
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ summary: 'Producción #44 planificada con 1 pedidos' }))
    expect(push).toHaveBeenCalledWith({ name: 'produccion-detalle', params: { id: 44 } })
  })

  it('explica cuando no hay pedidos y oculta el botón al gerente', async () => {
    const wrapper = montar()
    await flushPromises()
    await wrapper.findAll('button').find((boton) => boton.text() === 'Planificar por fecha')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('No hay pedidos confirmados libres para esa fecha.')
    sesion.rol = 'GERENTE'
    const gerente = montar()
    await flushPromises()
    expect(gerente.text()).not.toContain('Planificar por fecha')
  })
  it('consulta la página 1 sin estado y muestra el resumen', async () => {
    responder({ EN_PROCESO: 1, PLANIFICADA: 2 }, { ...vacia, total: 1, items: [produccion] } as never)
    const wrapper = montar()
    await flushPromises()
    expect(listarProducciones).toHaveBeenCalledWith(null, 1, 20)
    expect(listarProducciones).toHaveBeenCalledWith('EN_PROCESO', 1, 1, false)
    expect(listarProducciones).toHaveBeenCalledWith('PLANIFICADA', 1, 1, false)
    expect(wrapper.text()).toContain('1 en proceso · 2 planificadas')
    expect(wrapper.text()).toContain('#12')
    expect(wrapper.text()).toContain('Luis')
  })

  it('aplica el estado y la página de la query', async () => {
    route.query = { estado: 'EN_PROCESO', pagina: '2' }
    responder({ EN_PROCESO: 0, PLANIFICADA: 0 }, { ...vacia, total: 30 })
    montar()
    await flushPromises()
    expect(listarProducciones).toHaveBeenCalledWith('EN_PROCESO', 2, 20)
  })

  it('cambia el filtro con replace y vuelve a la página 1', async () => {
    route.query = { pagina: '2' }
    responder({ EN_PROCESO: 0, PLANIFICADA: 0 }, { ...vacia, total: 30 })
    const wrapper = montar()
    await flushPromises()
    wrapper.findComponent(SelectButton).vm.$emit('update:modelValue', 'PLANIFICADA')
    expect(replace).toHaveBeenCalledWith({ name: 'produccion', query: { estado: 'PLANIFICADA' } })
    wrapper.findComponent(SelectButton).vm.$emit('update:modelValue', 'TODAS')
    expect(replace).toHaveBeenLastCalledWith({ name: 'produccion', query: {} })
  })

  it('dice sin producciones activas y lo oculta si falla el conteo', async () => {
    const wrapper = montar()
    await flushPromises()
    expect(wrapper.text()).toContain('Sin producciones activas')
    listarProducciones.mockImplementation((_estado: string, _pagina: number, tamano: number) =>
      tamano === 1 ? Promise.reject(new Error('red')) : Promise.resolve(vacia))
    const otro = montar()
    await flushPromises()
    expect(otro.text()).not.toContain('Sin producciones activas')
    expect(otro.text()).not.toContain('en proceso ·')
  })

  it.each(['ADMINISTRADOR', 'PRODUCCION'])('muestra Nueva producción para %s', async (rol) => {
    sesion.rol = rol
    const wrapper = montar()
    await flushPromises()
    expect(wrapper.findAll('button').some((boton) => boton.text().includes('Nueva producción'))).toBe(true)
  })

  it('oculta Nueva producción para gerente', async () => {
    sesion.rol = 'GERENTE'
    const wrapper = montar()
    await flushPromises()
    expect(wrapper.text()).not.toContain('Nueva producción')
    expect(wrapper.text()).toContain('Aún no hay producciones.')
  })

  it('crea la producción desde el diálogo y navega a su detalle', async () => {
    crearProduccion.mockResolvedValue({ idProduccion: 31 })
    const wrapper = montar()
    await flushPromises()
    await wrapper.findAll('button').find((boton) => boton.text().includes('Nueva producción'))!.trigger('click')
    await nextTick()
    wrapper.findComponent(Textarea).vm.$emit('update:modelValue', '  Horneada temprana ')
    await nextTick()
    expect(wrapper.text()).toContain('20/500')
    await wrapper.find('.dialogo-stub form').trigger('submit')
    await flushPromises()
    expect(crearProduccion).toHaveBeenCalledTimes(1)
    expect(crearProduccion).toHaveBeenCalledWith('Horneada temprana')
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', summary: 'Producción #31 creada' }))
    expect(push).toHaveBeenCalledWith({ name: 'produccion-detalle', params: { id: 31 } })
  })

  it('envía null cuando la observación queda vacía', async () => {
    crearProduccion.mockResolvedValue({ idProduccion: 32 })
    const wrapper = montar()
    await flushPromises()
    await wrapper.findAll('button').find((boton) => boton.text().includes('Nueva producción'))!.trigger('click')
    await nextTick()
    await wrapper.find('.dialogo-stub form').trigger('submit')
    await flushPromises()
    expect(crearProduccion).toHaveBeenCalledWith(null)
  })

  it('muestra el vacío del filtro', async () => {
    route.query = { estado: 'CANCELADA' }
    const wrapper = montar()
    await flushPromises()
    expect(wrapper.text()).toContain('No hay producciones con este filtro.')
  })

  it('muestra error en la tabla y permite reintentar', async () => {
    listarProducciones.mockImplementation((_estado: string, _pagina: number, tamano: number) =>
      tamano === 1 ? Promise.resolve(vacia) : Promise.reject(new Error('red')))
    const wrapper = montar()
    await flushPromises()
    expect(wrapper.text()).toContain('No se pudieron cargar las producciones.')
    responder({ EN_PROCESO: 0, PLANIFICADA: 0 })
    await wrapper.findAll('button').find((boton) => boton.text() === 'Reintentar')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Aún no hay producciones.')
  })

  it('descarta una respuesta anterior cuando cambia la query', async () => {
    let resolver!: (valor: unknown) => void
    listarProducciones.mockImplementationOnce(() => new Promise((resolve) => { resolver = resolve }))
    const wrapper = montar()
    route.query = { estado: 'FINALIZADA' }
    await nextTick()
    await flushPromises()
    resolver({ ...vacia, total: 1, items: [produccion] })
    await flushPromises()
    expect(wrapper.text()).not.toContain('#12')
    expect(wrapper.text()).toContain('No hay producciones con este filtro.')
  })
})

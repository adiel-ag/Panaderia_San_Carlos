import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { reactive } from 'vue'
import PrimeVue from 'primevue/config'
import SelectButton from 'primevue/selectbutton'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Checkbox from 'primevue/checkbox'
import Textarea from 'primevue/textarea'
import InventarioView from './InventarioView.vue'
import { ApiError } from '../api/cliente'

const api = vi.hoisted(() => ({
  listarExistencias: vi.fn(), registrarMovimiento: vi.fn(), replace: vi.fn(), toast: vi.fn(), sesion: { rol: 'BODEGA' },
}))
const route = reactive({ query: {} as Record<string, string>, fullPath: '/inventario' })
vi.mock('../api/inventario', async (original) => ({ ...(await original<typeof import('../api/inventario')>()),
  listarExistencias: api.listarExistencias, registrarMovimiento: api.registrarMovimiento }))
vi.mock('../stores/sesion', () => ({ useSesionStore: () => api.sesion }))
vi.mock('vue-router', async (original) => ({
  ...(await original<typeof import('vue-router')>()), useRoute: () => route, useRouter: () => ({ replace: api.replace }),
}))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: api.toast }) }))

const vacia = { items: [], total: 0, pagina: 1, tamano: 20 }
function existencia(idProducto: number, nombre: string, existencia: number | null, stockMinimo = 10, unidad = 'KG') {
  return { idProducto, nombre, tipo: 'INSUMO', unidad, existencia, stockMinimo, bajoMinimo: existencia === null ? null : existencia < stockMinimo }
}
const harina = existencia(1, 'Harina', 25.5)
const azucar = existencia(2, 'Azúcar', 3)
const levadura = existencia(3, 'Levadura', null)
const huevos = existencia(4, 'Huevos', 0, 12, 'UND')
// tamano 1 es el conteo del resumen.
function responder(lista: unknown = vacia, bajos = 0) {
  api.listarExistencias.mockImplementation((_filtros: unknown, _pagina: number, tamano: number) =>
    Promise.resolve(tamano === 1 ? { ...vacia, total: bajos } : lista))
}
function llamadasLista() { return api.listarExistencias.mock.calls.filter((llamada) => llamada[2] === 20) }
const montajes: VueWrapper[] = []
async function montar() {
  const wrapper = mount(InventarioView, { attachTo: document.body, global: { plugins: [PrimeVue], stubs: {
    Dialog: { props: ['visible', 'header'], template: '<div v-if="visible" class="dialogo-stub"><h2>{{ header }}</h2><slot /></div>' },
  } } })
  montajes.push(wrapper)
  await flushPromises()
  return wrapper
}
function boton(wrapper: VueWrapper, texto: string) {
  return wrapper.findAll('button').find((item) => item.text() === texto)
}
async function abrir(wrapper: VueWrapper, accion: 'entrada' | 'salida', nombre: string) {
  await wrapper.find(`[aria-label="Registrar ${accion} de ${nombre}"]`).trigger('click')
  await flushPromises()
}
async function escribirCantidad(wrapper: VueWrapper, valor: number | null) {
  wrapper.findComponent(InputNumber).vm.$emit('update:modelValue', valor)
  await flushPromises()
}
afterEach(() => {
  montajes.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.useRealTimers()
})
beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn(), addListener: vi.fn(), removeListener: vi.fn() }))
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })
  route.query = {}
  route.fullPath = '/inventario'
  api.sesion.rol = 'BODEGA'
  responder()
  api.registrarMovimiento.mockResolvedValue({ idProducto: 1, existencia: 30.5 })
})

describe('InventarioView', () => {
  it('consulta las existencias y el conteo bajo el mínimo por defecto', async () => {
    responder({ ...vacia, total: 4, items: [harina, azucar, levadura, huevos] }, 2)
    const wrapper = await montar()
    expect(api.listarExistencias).toHaveBeenCalledWith({ tipo: undefined, nombre: undefined, bajoMinimo: undefined }, 1, 20)
    expect(api.listarExistencias).toHaveBeenCalledWith({ tipo: undefined, bajoMinimo: true }, 1, 1, false)
    expect(wrapper.find('h1').text()).toBe('Inventario')
    expect(wrapper.find('.resumen-inventario').text()).toContain('2 productos bajo el mínimo')
    const filas = wrapper.findAll('tbody tr').map((fila) => fila.findAll('td').slice(0, 5).map((celda) => celda.text()))
    expect(filas).toEqual([
      ['Harina', 'Insumo', '25.5kg', '10kg', ''],
      ['Azúcar', 'Insumo', '3kg', '10kg', 'Bajo el mínimo'],
      ['Levadura', 'Insumo', '— Sin registro', '10kg', 'Sin inventario'],
      ['Huevos', 'Insumo', '0u', '12u', 'Bajo el mínimo'],
    ])
    expect(wrapper.text()).not.toContain('Limpiar filtros')
  })

  it('lee los filtros de la URL y el resumen respeta el tipo', async () => {
    route.query = { tipo: 'PRODUCTO_FINAL', q: 'pan', bajo: '1', pagina: '2' }
    responder({ ...vacia, total: 30, items: [harina] }, 3)
    const wrapper = await montar()
    expect(api.listarExistencias).toHaveBeenCalledWith({ tipo: 'PRODUCTO_FINAL', nombre: 'pan', bajoMinimo: true }, 2, 20)
    expect(api.listarExistencias).toHaveBeenCalledWith({ tipo: 'PRODUCTO_FINAL', bajoMinimo: true }, 1, 1, false)
    expect((wrapper.find('#buscar-producto').element as HTMLInputElement).value).toBe('pan')
    expect(wrapper.text()).toContain('Limpiar filtros')
    // El filtro ya está aplicado: no se ofrece de nuevo.
    expect(boton(wrapper, 'Ver solo bajo el mínimo')).toBeUndefined()
  })

  it('cambia el tipo y el filtro bajo el mínimo con replace y vuelve a la página 1', async () => {
    route.query = { pagina: '2', q: 'harina' }
    responder({ ...vacia, total: 30 })
    const wrapper = await montar()
    wrapper.findComponent(SelectButton).vm.$emit('update:modelValue', 'INSUMO')
    expect(api.replace).toHaveBeenLastCalledWith({ name: 'inventario', query: { tipo: 'INSUMO', q: 'harina' } })
    wrapper.findComponent(Checkbox).vm.$emit('update:modelValue', true)
    expect(api.replace).toHaveBeenLastCalledWith({ name: 'inventario', query: { q: 'harina', bajo: '1' } })
    route.query = { tipo: 'INSUMO' }
    await flushPromises()
    wrapper.findComponent(SelectButton).vm.$emit('update:modelValue', 'todos')
    expect(api.replace).toHaveBeenLastCalledWith({ name: 'inventario', query: {} })
  })

  it('ofrece ver solo los productos bajo el mínimo desde el resumen', async () => {
    responder({ ...vacia, total: 1, items: [azucar] }, 1)
    const wrapper = await montar()
    expect(wrapper.find('.resumen-inventario').text()).toContain('1 producto bajo el mínimo')
    await boton(wrapper, 'Ver solo bajo el mínimo')!.trigger('click')
    expect(api.replace).toHaveBeenCalledWith({ name: 'inventario', query: { bajo: '1' } })
  })

  it('sin productos bajo el mínimo no ofrece el atajo y oculta el resumen si falla', async () => {
    const wrapper = await montar()
    expect(wrapper.find('.resumen-inventario').text()).toBe('Ningún producto bajo el mínimo')
    expect(boton(wrapper, 'Ver solo bajo el mínimo')).toBeUndefined()
    api.listarExistencias.mockImplementation((_filtros: unknown, _pagina: number, tamano: number) =>
      tamano === 1 ? Promise.reject(new Error('red')) : Promise.resolve(vacia))
    const fallida = await montar()
    expect(fallida.find('.resumen-inventario').exists()).toBe(false)
  })

  it('aplica la búsqueda tras 300 ms, con Enter al instante y la borra', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const wrapper = await montar()
    const campo = wrapper.findComponent(InputText)
    campo.vm.$emit('update:modelValue', '  harina ')
    vi.advanceTimersByTime(299)
    expect(api.replace).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(api.replace).toHaveBeenCalledWith({ name: 'inventario', query: { q: 'harina' } })
    campo.vm.$emit('update:modelValue', 'azúcar')
    await wrapper.find('#buscar-producto').trigger('keydown', { key: 'Enter' })
    expect(api.replace).toHaveBeenLastCalledWith({ name: 'inventario', query: { q: 'azúcar' } })
    vi.advanceTimersByTime(500)
    expect(api.replace).toHaveBeenCalledTimes(2)
    route.query = { q: 'azúcar' }
    await flushPromises()
    expect(api.listarExistencias).toHaveBeenLastCalledWith({ tipo: undefined, nombre: 'azúcar', bajoMinimo: undefined }, 1, 20)
    await wrapper.find('[aria-label="Borrar búsqueda"]').trigger('click')
    expect(api.replace).toHaveBeenLastCalledWith({ name: 'inventario', query: {} })
  })

  it.each(['ADMINISTRADOR', 'BODEGA'])('%s ve los botones de movimiento', async (rol) => {
    api.sesion.rol = rol
    responder({ ...vacia, total: 3, items: [harina, levadura, huevos] })
    const wrapper = await montar()
    expect(wrapper.find('[aria-label="Registrar entrada de Harina"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="Registrar salida de Harina"]').attributes('disabled')).toBeUndefined()
    // Sin inventario o en cero no hay salida posible.
    for (const nombre of ['Levadura', 'Huevos']) {
      const salida = wrapper.find(`[aria-label="Registrar salida de ${nombre}"]`)
      expect(salida.attributes('disabled')).toBeDefined()
      expect(salida.element.parentElement!.getAttribute('title')).toBe('No hay existencias para registrar una salida')
      expect(wrapper.find(`[aria-label="Registrar entrada de ${nombre}"]`).attributes('disabled')).toBeUndefined()
    }
  })

  it.each(['GERENTE', 'PRODUCCION', 'DESPACHO'])('%s solo consulta', async (rol) => {
    api.sesion.rol = rol
    responder({ ...vacia, total: 1, items: [harina] })
    const wrapper = await montar()
    expect(wrapper.text()).toContain('Harina')
    expect(wrapper.find('[aria-label^="Registrar"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Movimientos')
  })

  it('registra una entrada, avisa y recarga en el lugar', async () => {
    responder({ ...vacia, total: 1, items: [harina] }, 1)
    const wrapper = await montar()
    await abrir(wrapper, 'entrada', 'Harina')
    const dialogo = wrapper.find('.dialogo-stub')
    expect(dialogo.find('h2').text()).toBe('Registrar entrada')
    expect(dialogo.find('.producto-movimiento').text()).toBe('Harina')
    expect(dialogo.text()).toContain('Existencia actual: 25.5 kg')
    expect(dialogo.text()).toContain('0/500')
    await escribirCantidad(wrapper, 5)
    expect(dialogo.find('.quedara-movimiento').text()).toBe('Quedará: 30.5 kg')
    await wrapper.findComponent(Textarea).setValue('  Compra semanal  ')
    responder({ ...vacia, total: 1, items: [{ ...harina, existencia: 30.5 }] }, 0)
    const formulario = dialogo.find('form')
    await formulario.trigger('submit')
    await formulario.trigger('submit')
    await flushPromises()
    expect(api.registrarMovimiento).toHaveBeenCalledTimes(1)
    expect(api.registrarMovimiento).toHaveBeenCalledWith('entrada', 1, 5, 'Compra semanal')
    expect(api.toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', summary: 'Entrada registrada · existencia: 30.5 kg' }))
    expect(wrapper.find('.dialogo-stub').exists()).toBe(false)
    expect(llamadasLista()).toHaveLength(2)
    expect(wrapper.find('tbody').text()).toContain('30.5kg')
    expect(wrapper.find('.resumen-inventario').text()).toContain('Ningún producto bajo el mínimo')
    expect(document.activeElement).toBe(wrapper.find('[aria-label="Registrar entrada de Harina"]').element)
  })

  it('una entrada sin inventario parte de cero', async () => {
    responder({ ...vacia, total: 1, items: [levadura] })
    api.registrarMovimiento.mockResolvedValue({ idProducto: 3, existencia: 2 })
    const wrapper = await montar()
    await abrir(wrapper, 'entrada', 'Levadura')
    expect(wrapper.find('.dialogo-stub').text()).toContain('Sin inventario registrado')
    await escribirCantidad(wrapper, 2)
    expect(wrapper.find('.quedara-movimiento').text()).toBe('Quedará: 2 kg')
    await wrapper.find('.dialogo-stub form').trigger('submit')
    await flushPromises()
    expect(api.registrarMovimiento).toHaveBeenCalledWith('entrada', 3, 2, null)
  })

  it('valida la salida contra la existencia antes de enviar', async () => {
    responder({ ...vacia, total: 1, items: [harina] })
    api.registrarMovimiento.mockResolvedValue({ idProducto: 1, existencia: 0.5 })
    const wrapper = await montar()
    await abrir(wrapper, 'salida', 'Harina')
    expect(wrapper.find('.dialogo-stub h2').text()).toBe('Registrar salida')
    await wrapper.find('.dialogo-stub form').trigger('submit')
    expect(wrapper.find('.error-campo').text()).toContain('Ingrese una cantidad mayor que 0')
    await escribirCantidad(wrapper, 30)
    expect(wrapper.find('.quedara-movimiento').text()).toBe('Quedará: -4.5 kg')
    expect(wrapper.find('.quedara-movimiento').classes()).toContain('quedara-movimiento--excede')
    expect(wrapper.find('.error-campo').text()).toBe('No hay existencias suficientes: hay 25.5 kg.')
    await wrapper.find('.dialogo-stub form').trigger('submit')
    expect(api.registrarMovimiento).not.toHaveBeenCalled()
    await escribirCantidad(wrapper, 25)
    expect(wrapper.find('.error-campo').exists()).toBe(false)
    await wrapper.find('.dialogo-stub form').trigger('submit')
    await flushPromises()
    expect(api.registrarMovimiento).toHaveBeenCalledWith('salida', 1, 25, null)
    expect(api.toast).toHaveBeenCalledWith(expect.objectContaining({ summary: 'Salida registrada · existencia: 0.5 kg' }))
  })

  it('con un 409 deja el diálogo abierto con sus datos', async () => {
    responder({ ...vacia, total: 1, items: [harina] })
    api.registrarMovimiento.mockRejectedValue(new ApiError(409, 'CONFLICTO', 'Inventario insuficiente'))
    const wrapper = await montar()
    await abrir(wrapper, 'salida', 'Harina')
    await escribirCantidad(wrapper, 5)
    await wrapper.find('.dialogo-stub form').trigger('submit')
    await flushPromises()
    const dialogo = wrapper.find('.dialogo-stub')
    expect(dialogo.exists()).toBe(true)
    expect(dialogo.text()).toContain('Quedará: 20.5 kg')
    expect(dialogo.text()).not.toContain('Inventario insuficiente')
    expect(api.toast).not.toHaveBeenCalled()
    expect(llamadasLista()).toHaveLength(1)
    expect(boton(wrapper, 'Registrar salida')!.attributes('disabled')).toBeUndefined()
  })

  it('muestra en el diálogo el mensaje de un 400', async () => {
    responder({ ...vacia, total: 1, items: [harina] })
    api.registrarMovimiento.mockRejectedValue(new ApiError(400, 'SOLICITUD_INVALIDA', 'La cantidad debe ser mayor que cero'))
    const wrapper = await montar()
    await abrir(wrapper, 'entrada', 'Harina')
    await escribirCantidad(wrapper, 1)
    await wrapper.find('.dialogo-stub form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('.dialogo-stub').text()).toContain('La cantidad debe ser mayor que cero')
  })

  it('muestra las salidas vacías según los filtros', async () => {
    const sinFiltros = await montar()
    expect(sinFiltros.text()).toContain('No hay productos activos.')
    expect(sinFiltros.text()).not.toContain('Limpiar filtros')
    route.query = { q: 'zz' }
    const filtrada = await montar()
    expect(filtrada.text()).toContain('No hay productos con estos filtros.')
    const limpiar = filtrada.findAll('button').filter((item) => item.text() === 'Limpiar filtros')
    expect(limpiar).toHaveLength(2)
    await limpiar[1]!.trigger('click')
    expect(api.replace).toHaveBeenCalledWith({ name: 'inventario', query: {} })
  })

  it('avisa el error de carga, reintenta e ignora respuestas viejas', async () => {
    api.listarExistencias.mockImplementation((_filtros: unknown, _pagina: number, tamano: number) =>
      tamano === 1 ? Promise.resolve(vacia) : Promise.reject(new Error('red')))
    const wrapper = await montar()
    expect(wrapper.text()).toContain('No se pudo cargar el inventario.')
    let primera: (valor: unknown) => void = () => {}
    api.listarExistencias.mockImplementationOnce(() => new Promise((resolve) => { primera = resolve }))
    await boton(wrapper, 'Reintentar')!.trigger('click')
    responder({ ...vacia, total: 1, items: [azucar] })
    route.query = { tipo: 'INSUMO' }
    await flushPromises()
    primera({ ...vacia, total: 1, items: [harina] })
    await flushPromises()
    expect(wrapper.text()).toContain('Azúcar')
    expect(wrapper.text()).not.toContain('Harina')
  })
})

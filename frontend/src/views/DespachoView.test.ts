import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import PrimeVue from 'primevue/config'
import SelectButton from 'primevue/selectbutton'
import Textarea from 'primevue/textarea'
import DespachoView from './DespachoView.vue'
import { ApiError } from '../api/cliente'
import { hoyLocal, sumarDias } from '../api/fechas'

const api = vi.hoisted(() => ({
  listarPedidos: vi.fn(), obtenerPedido: vi.fn(), listarProductos: vi.fn(), listarSucursales: vi.fn(), despacharPedido: vi.fn(),
  replace: vi.fn(), push: vi.fn(), toast: vi.fn(), sesion: { rol: 'DESPACHO' },
}))
const route = reactive({ query: {} as Record<string, string>, fullPath: '/despacho' })
vi.mock('../api/pedidos', async (original) => ({ ...(await original<typeof import('../api/pedidos')>()),
  listarPedidos: api.listarPedidos, obtenerPedido: api.obtenerPedido, listarProductos: api.listarProductos, listarSucursales: api.listarSucursales }))
vi.mock('../api/despachos', () => ({ despacharPedido: api.despacharPedido }))
vi.mock('../stores/sesion', () => ({ useSesionStore: () => api.sesion }))
vi.mock('vue-router', async (original) => ({
  ...(await original<typeof import('vue-router')>()), useRoute: () => route, useRouter: () => ({ replace: api.replace, push: api.push }),
}))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: api.toast }) }))

const hoy = hoyLocal()
const vacia = { items: [], total: 0, pagina: 1, tamano: 20 }
function pedido(idPedido: number, fechaEntrega = hoy, estado = 'LISTO_DESPACHO') {
  return { idPedido, idSucursal: 3, nombreSucursal: 'Centro', idUsuario: 1, nombreUsuario: 'Ana', fechaPedido: '2026-10-30T08:00:00',
    fechaEntrega, estado, observacion: null, cantidadProductos: 2 }
}
const productos = [
  { idProducto: 9, nombre: 'Pan francés', nombreTipoProducto: 'PRODUCTO_FINAL', abreviaturaUnidadMedida: 'UND', nombreUnidadMedida: 'Unidad', estado: 'ACTIVO' },
  { idProducto: 10, nombre: 'Pastel', nombreTipoProducto: 'PRODUCTO_FINAL', abreviaturaUnidadMedida: 'KG', nombreUnidadMedida: 'Kilogramo', estado: 'ACTIVO' },
]
const detallePedido = { idPedido: 7, idSucursal: 3, idUsuario: 1, fechaEntrega: hoy, estado: 'LISTO_DESPACHO', observacion: null, detalles: [
  { idPedidoDetalle: 1, idPedido: 7, idProducto: 9, nombreProducto: 'Pan francés', cantidad: 40 },
  { idPedidoDetalle: 2, idPedido: 7, idProducto: 10, nombreProducto: 'Pastel', cantidad: 2.5 },
] }
// tamano 1 es el conteo del resumen; con entregaHasta, los atrasados.
function responder(lista: unknown = vacia, conteos = { listos: 0, atrasados: 0 }) {
  api.listarPedidos.mockImplementation((filtros: { entregaHasta?: string }, _pagina: number, tamano: number) =>
    Promise.resolve(tamano === 1 ? { ...vacia, total: filtros.entregaHasta ? conteos.atrasados : conteos.listos } : lista))
}
const montajes: VueWrapper[] = []
async function montar() {
  const wrapper = mount(DespachoView, { attachTo: document.body, global: { plugins: [PrimeVue], stubs: {
    RouterLink: { template: '<a class="enlace-stub"><slot /></a>' },
    Dialog: { props: ['visible', 'header'], template: '<div v-if="visible" class="dialogo-stub"><h2>{{ header }}</h2><slot /></div>' },
  } } })
  montajes.push(wrapper)
  await flushPromises()
  return wrapper
}
function boton(wrapper: VueWrapper, texto: string) {
  return wrapper.findAll('button').find((item) => item.text() === texto)
}
async function abrirDialogo(wrapper: VueWrapper, id = 7) {
  await wrapper.find(`[aria-label="Despachar pedido #${id}"]`).trigger('click')
  await flushPromises()
}
afterEach(() => { montajes.splice(0).forEach((wrapper) => wrapper.unmount()) })
beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn(), addListener: vi.fn(), removeListener: vi.fn() }))
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })
  route.query = {}
  route.fullPath = '/despacho'
  api.sesion.rol = 'DESPACHO'
  responder()
  api.listarSucursales.mockResolvedValue([{ idSucursal: 3, nombre: 'Centro' }])
  api.obtenerPedido.mockResolvedValue(detallePedido)
  api.listarProductos.mockResolvedValue(productos)
  api.despacharPedido.mockResolvedValue({ idDespacho: 1, idPedido: 7, idSucursal: 3, idUsuario: 2, fechaDespacho: '2026-10-31T10:00:00', estado: 'ENTREGADO', observacion: null })
  api.push.mockResolvedValue(undefined)
})

describe('DespachoView', () => {
  it('consulta los listos para despachar y muestra el resumen con atrasados', async () => {
    responder({ ...vacia, total: 2, items: [pedido(7, sumarDias(hoy, -1)), pedido(8, sumarDias(hoy, 1))] }, { listos: 2, atrasados: 1 })
    const wrapper = await montar()
    expect(api.listarPedidos).toHaveBeenCalledWith({ estado: 'LISTO_DESPACHO', idSucursal: undefined }, 1, 20)
    expect(api.listarPedidos).toHaveBeenCalledWith({ estado: 'LISTO_DESPACHO', idSucursal: undefined }, 1, 1, false)
    expect(api.listarPedidos).toHaveBeenCalledWith({ estado: 'LISTO_DESPACHO', idSucursal: undefined, entregaHasta: sumarDias(hoy, -1) }, 1, 1, false)
    expect(wrapper.find('h1').text()).toBe('Despacho')
    expect(wrapper.text()).toContain('2 pedidos listos para despachar · 1 atrasado')
    expect(wrapper.find('.marca-atrasado').text()).toBe('Atrasado')
    expect(wrapper.find('.fecha-relativa').text()).toBe('Mañana')
    expect(wrapper.find('[aria-label="Despachar pedido #7"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('Limpiar filtros')
  })

  it('oculta el resumen si falla el conteo', async () => {
    api.listarPedidos.mockImplementation((_filtros: unknown, _pagina: number, tamano: number) =>
      tamano === 1 ? Promise.reject(new Error('red')) : Promise.resolve(vacia))
    const wrapper = await montar()
    expect(wrapper.find('.hallazgo-pedidos').exists()).toBe(false)
  })

  it('consulta los despachados de la semana sin botón Despachar', async () => {
    route.query = { vista: 'despachados', sucursal: '3', pagina: '2' }
    responder({ ...vacia, total: 30, items: [pedido(5, sumarDias(hoy, -2), 'DESPACHADO')] })
    const wrapper = await montar()
    expect(api.listarPedidos).toHaveBeenCalledWith({ estado: 'DESPACHADO', idSucursal: 3, entregaDesde: sumarDias(hoy, -7) }, 2, 20)
    expect(api.listarPedidos).toHaveBeenCalledWith({ estado: 'LISTO_DESPACHO', idSucursal: 3 }, 1, 1, false)
    expect(wrapper.find('[aria-label^="Despachar pedido"]').exists()).toBe(false)
    expect(wrapper.find('.estado-pedido').text()).toBe('Despachado')
    expect(wrapper.find('.marca-atrasado').exists()).toBe(false)
    expect(wrapper.text()).toContain('Limpiar filtros')
  })

  it('cambia la vista con replace y vuelve a la página 1', async () => {
    route.query = { pagina: '2' }
    responder({ ...vacia, total: 30 })
    const wrapper = await montar()
    wrapper.findComponent(SelectButton).vm.$emit('update:modelValue', 'despachados')
    expect(api.replace).toHaveBeenCalledWith({ name: 'despacho', query: { vista: 'despachados' } })
  })

  it('al hacer clic en la fila abre el detalle, pero el botón no navega', async () => {
    route.fullPath = '/despacho?sucursal=3'
    responder({ ...vacia, total: 1, items: [pedido(7)] }, { listos: 1, atrasados: 0 })
    const wrapper = await montar()
    await abrirDialogo(wrapper)
    expect(api.push).not.toHaveBeenCalled()
    expect(wrapper.find('.dialogo-stub').exists()).toBe(true)
    await wrapper.findAll('tbody tr')[0]!.findAll('td')[1]!.trigger('click')
    expect(api.push).toHaveBeenCalledWith({ name: 'pedido', params: { id: 7 }, query: { volver: '/despacho?sucursal=3' } })
  })

  it('carga los productos del pedido con sus unidades en el diálogo', async () => {
    responder({ ...vacia, total: 1, items: [pedido(7)] }, { listos: 1, atrasados: 0 })
    const wrapper = await montar()
    await abrirDialogo(wrapper)
    expect(api.obtenerPedido).toHaveBeenCalledWith(7)
    const dialogo = wrapper.find('.dialogo-stub')
    expect(dialogo.find('h2').text()).toBe('Despachar pedido #7')
    expect(dialogo.text()).toContain('Centro')
    const filas = dialogo.findAll('tbody tr').map((fila) => fila.findAll('td').map((celda) => celda.text()))
    expect(filas).toEqual([['Pan francés', '40u'], ['Pastel', '2.5kg']])
    expect(dialogo.text()).toContain('Se descontará del inventario de producto terminado.')
    expect(dialogo.text()).toContain('0/500')
  })

  it('avisa si no se pudieron cargar los productos y permite reintentar', async () => {
    responder({ ...vacia, total: 1, items: [pedido(7)] })
    api.obtenerPedido.mockRejectedValueOnce(new ApiError(500, 'ERROR', 'falló'))
    const wrapper = await montar()
    await abrirDialogo(wrapper)
    expect(wrapper.text()).toContain('No se pudieron cargar los productos del pedido.')
    expect(boton(wrapper, 'Confirmar despacho')!.attributes('disabled')).toBeDefined()
    await boton(wrapper, 'Reintentar')!.trigger('click')
    await flushPromises()
    expect(wrapper.find('.dialogo-stub').text()).toContain('Pan francés')
  })

  it.each([['', null], ['  Salió con Luis  ', 'Salió con Luis']])('despacha con la observación %j, avisa y recarga', async (texto, esperado) => {
    responder({ ...vacia, total: 1, items: [pedido(7)] }, { listos: 1, atrasados: 0 })
    const wrapper = await montar()
    await abrirDialogo(wrapper)
    await wrapper.findComponent(Textarea).setValue(texto)
    responder(vacia)
    const formulario = wrapper.find('.dialogo-stub form')
    await formulario.trigger('submit')
    await formulario.trigger('submit')
    await flushPromises()
    expect(api.despacharPedido).toHaveBeenCalledTimes(1)
    expect(api.despacharPedido).toHaveBeenCalledWith(7, esperado)
    expect(api.toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', summary: 'Pedido #7 despachado' }))
    expect(wrapper.find('.dialogo-stub').exists()).toBe(false)
    expect(api.listarPedidos.mock.calls.filter((llamada) => llamada[2] === 20)).toHaveLength(2)
    expect(wrapper.text()).toContain('No hay pedidos listos para despachar')
    // El pedido salió de la lista: el foco vuelve al título.
    expect(document.activeElement).toBe(wrapper.find('h1').element)
  })

  it('mantiene los datos a la vista mientras recarga tras despachar', async () => {
    responder({ ...vacia, total: 2, items: [pedido(7), pedido(8)] }, { listos: 2, atrasados: 0 })
    const wrapper = await montar()
    await abrirDialogo(wrapper)
    let resolver: (valor: unknown) => void = () => {}
    api.listarPedidos.mockImplementation(() => new Promise((resolve) => { resolver = resolve }))
    await wrapper.find('.dialogo-stub form').trigger('submit')
    await nextTick()
    await nextTick()
    expect(wrapper.find('[aria-label="Despachar pedido #8"]').exists()).toBe(true)
    expect(wrapper.find('.p-datatable-mask').exists()).toBe(false)
    expect(wrapper.text()).toContain('2 pedidos listos para despachar')
    resolver({ ...vacia, total: 1, items: [pedido(8)] })
    await flushPromises()
  })

  it('con un 409 deja el diálogo abierto con sus datos', async () => {
    responder({ ...vacia, total: 1, items: [pedido(7)] }, { listos: 1, atrasados: 0 })
    api.despacharPedido.mockRejectedValue(new ApiError(409, 'CONFLICTO', 'Inventario insuficiente para despachar el pedido'))
    const wrapper = await montar()
    await abrirDialogo(wrapper)
    await wrapper.find('.dialogo-stub form').trigger('submit')
    await flushPromises()
    const dialogo = wrapper.find('.dialogo-stub')
    expect(dialogo.exists()).toBe(true)
    expect(dialogo.text()).toContain('Pan francés')
    expect(dialogo.text()).not.toContain('Inventario insuficiente')
    expect(api.toast).not.toHaveBeenCalled()
    expect(boton(wrapper, 'Confirmar despacho')!.attributes('disabled')).toBeUndefined()
  })

  it('muestra en el diálogo el mensaje de un 404', async () => {
    responder({ ...vacia, total: 1, items: [pedido(7)] })
    api.despacharPedido.mockRejectedValue(new ApiError(404, 'NO_ENCONTRADO', 'Pedido inexistente'))
    const wrapper = await montar()
    await abrirDialogo(wrapper)
    await wrapper.find('.dialogo-stub form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('.dialogo-stub').text()).toContain('Pedido inexistente')
  })

  it('muestra las salidas vacías de cada vista', async () => {
    const listos = await montar()
    expect(listos.text()).toContain('No hay pedidos listos para despachar.')
    expect(listos.text()).not.toContain('Limpiar filtros')
    route.query = { sucursal: '3' }
    const filtrada = await montar()
    expect(filtrada.text()).toContain('No hay pedidos listos para despachar.')
    expect(filtrada.findAll('button').filter((item) => item.text() === 'Limpiar filtros')).toHaveLength(2)
    route.query = { vista: 'despachados' }
    const despachados = await montar()
    expect(despachados.text()).toContain('No se despacharon pedidos esta semana.')
  })

  it('avisa el error de carga, reintenta e ignora respuestas viejas', async () => {
    api.listarPedidos.mockImplementation((_filtros: unknown, _pagina: number, tamano: number) =>
      tamano === 1 ? Promise.resolve(vacia) : Promise.reject(new Error('red')))
    const wrapper = await montar()
    expect(wrapper.text()).toContain('No se pudieron cargar los pedidos.')
    let primera: (valor: unknown) => void = () => {}
    api.listarPedidos.mockImplementationOnce(() => new Promise((resolve) => { primera = resolve }))
    await boton(wrapper, 'Reintentar')!.trigger('click')
    responder({ ...vacia, total: 1, items: [pedido(9)] })
    route.query = { sucursal: '3' }
    await flushPromises()
    primera({ ...vacia, total: 1, items: [pedido(4)] })
    await flushPromises()
    expect(wrapper.text()).toContain('#9')
    expect(wrapper.text()).not.toContain('#4')
  })
})

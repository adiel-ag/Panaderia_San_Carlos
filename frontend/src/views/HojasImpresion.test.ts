import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { reactive, type Component } from 'vue'
import PrimeVue from 'primevue/config'
import HojaDespachoView from './HojaDespachoView.vue'
import HojaProduccionView from './HojaProduccionView.vue'
import { ApiError } from '../api/cliente'

const api = vi.hoisted(() => ({
  obtenerPedido: vi.fn(), listarSucursales: vi.fn(), listarProductos: vi.fn(),
  obtenerProduccion: vi.fn(), obtenerDetalleProduccion: vi.fn(), obtenerInsumos: vi.fn(),
}))
const route = reactive({ params: { id: '123' }, query: {} as Record<string, string> })
vi.mock('../api/pedidos', async (original) => ({ ...(await original<typeof import('../api/pedidos')>()),
  obtenerPedido: api.obtenerPedido, listarSucursales: api.listarSucursales, listarProductos: api.listarProductos }))
vi.mock('../api/producciones', async (original) => ({ ...(await original<typeof import('../api/producciones')>()),
  obtenerProduccion: api.obtenerProduccion, obtenerDetalleProduccion: api.obtenerDetalleProduccion, obtenerInsumos: api.obtenerInsumos }))
vi.mock('vue-router', async (original) => ({ ...(await original<typeof import('vue-router')>()), useRoute: () => route }))

const productos = [
  { idProducto: 9, nombre: 'Pan francés', nombreTipoProducto: 'PRODUCTO_FINAL', abreviaturaUnidadMedida: 'UND', nombreUnidadMedida: 'Unidad', estado: 'ACTIVO' },
  { idProducto: 10, nombre: 'Pastel', nombreTipoProducto: 'PRODUCTO_FINAL', abreviaturaUnidadMedida: 'KG', nombreUnidadMedida: 'Kilogramo', estado: 'ACTIVO' },
]
function pedido(idPedido: number, detalles: [number, string, number][]) {
  return { idPedido, idSucursal: 3, idUsuario: 1, fechaEntrega: '2026-11-02', estado: 'LISTO_DESPACHO', observacion: 'Entregar temprano',
    detalles: detalles.map(([idProducto, nombreProducto, cantidad], i) => ({ idPedidoDetalle: idPedido * 10 + i, idPedido, idProducto, nombreProducto, cantidad })) }
}
function resumen(idPedido: number) {
  return { idPedido, idSucursal: 3, nombreSucursal: 'Centro', idUsuario: 1, nombreUsuario: 'Ana', fechaPedido: '2026-10-30T08:00:00',
    fechaEntrega: '2026-11-02', estado: 'CONFIRMADO', observacion: null, cantidadProductos: 1 }
}
function produccion(estado: string, pedidos = [resumen(7), resumen(8)]) {
  return { idProduccion: 5, idUsuario: 1, nombreUsuario: 'Luis', fechaProduccion: '2026-10-31T06:30:00', estado, observacion: null, cantidadPedidos: pedidos.length, pedidos }
}
const insumos = { idProduccion: 5, estado: 'PLANIFICADA', productosSinReceta: [], suficiente: true,
  insumos: [{ idInsumo: 2, nombre: 'Harina', unidad: 'KG', requerido: 12.5, disponible: 40, faltante: 0, sinInventario: false }] }

const montajes: VueWrapper[] = []
async function montar(componente: Component) {
  const wrapper = mount(componente, { global: { plugins: [PrimeVue], stubs: { RouterLink: { props: ['to'], template: '<a :data-to="JSON.stringify(to)"><slot /></a>' } } } })
  montajes.push(wrapper)
  await flushPromises()
  return wrapper
}
afterEach(() => { montajes.splice(0).forEach((wrapper) => wrapper.unmount()) })
beforeEach(() => {
  vi.clearAllMocks()
  route.params.id = '123'
  route.query = {}
  api.obtenerPedido.mockImplementation((id: number) => Promise.resolve(id === 123 ? pedido(123, [[9, 'Pan francés', 40]])
    : id === 7 ? pedido(7, [[9, 'Pan francés', 10], [10, 'Pastel', 1.5]]) : pedido(8, [[9, 'Pan francés', 5]])))
  api.listarSucursales.mockResolvedValue([{ idSucursal: 3, nombre: 'Centro' }])
  api.listarProductos.mockResolvedValue(productos)
  api.obtenerProduccion.mockResolvedValue(produccion('PLANIFICADA'))
  api.obtenerDetalleProduccion.mockResolvedValue([{ idProduccionDetalle: 1, idProduccion: 5, idProducto: 9, idReceta: 2, cantidadPlanificada: 30, cantidadProducida: 0 }])
  api.obtenerInsumos.mockResolvedValue(insumos)
})

describe('HojaDespachoView', () => {
  it('presenta los datos del pedido, la columna Recibido vacía y las firmas', async () => {
    const wrapper = await montar(HojaDespachoView)
    const texto = wrapper.text()
    for (const parte of ['Panadería San Carlos', 'Hoja de despacho', 'Pedido #123', 'Centro', 'Entregar temprano', 'Pan francés', '40', 'UND', 'Recibido', 'Entregó', 'Recibió', 'Fecha']) {
      expect(texto).toContain(parte)
    }
    expect(wrapper.findAll('tbody td.hoja-columna-vacia').every((celda) => celda.text() === '')).toBe(true)
    expect(wrapper.find('.acciones-hoja').classes()).toContain('no-imprimir')
  })

  it('imprime con window.print y vuelve al detalle conservando el origen', async () => {
    route.query = { volver: '/despacho?vista=listos' }
    const imprimir = vi.spyOn(window, 'print').mockImplementation(() => {})
    const wrapper = await montar(HojaDespachoView)
    await wrapper.findAll('button').find((boton) => boton.text() === 'Imprimir')!.trigger('click')
    expect(imprimir).toHaveBeenCalled()
    expect(JSON.parse(wrapper.find('.acciones-hoja a').attributes('data-to')!)).toEqual({ name: 'pedido', params: { id: 123 }, query: { volver: '/despacho?vista=listos' } })
    expect(wrapper.find('.acciones-hoja a').text()).toBe('← Volver')
    imprimir.mockRestore()
  })

  it('avisa si el pedido no existe', async () => {
    api.obtenerPedido.mockRejectedValue(new ApiError(404, 'Recurso no encontrado', 'No existe'))
    const wrapper = await montar(HojaDespachoView)
    expect(wrapper.text()).toContain('No se encontró el pedido #123.')
  })
})

describe('HojaProduccionView', () => {
  beforeEach(() => { route.params.id = '5' })

  it('en una planificada suma los productos de sus pedidos y lista los insumos', async () => {
    const wrapper = await montar(HojaProduccionView)
    expect(api.obtenerPedido).toHaveBeenCalledWith(7)
    expect(api.obtenerPedido).toHaveBeenCalledWith(8)
    expect(api.obtenerDetalleProduccion).not.toHaveBeenCalled()
    const filas = wrapper.findAll('table').at(0)!.findAll('tbody tr').map((fila) => fila.findAll('td').map((celda) => celda.text()))
    expect(filas).toEqual([['Pan francés', '15', 'UND', ''], ['Pastel', '1.5', 'KG', '']])
    expect(wrapper.text()).toContain('Hoja de producción')
    expect(wrapper.text()).toContain('Insumos necesarios')
    expect(wrapper.text()).toContain('Harina')
    expect(wrapper.text()).toContain('Responsable de producción')
  })

  it('en proceso usa el plan generado', async () => {
    api.obtenerProduccion.mockResolvedValue(produccion('EN_PROCESO'))
    const wrapper = await montar(HojaProduccionView)
    expect(api.obtenerPedido).not.toHaveBeenCalled()
    expect(wrapper.findAll('table').at(0)!.find('tbody tr').text()).toContain('30')
  })

  it('no ofrece la hoja para producciones finalizadas', async () => {
    api.obtenerProduccion.mockResolvedValue(produccion('FINALIZADA'))
    const wrapper = await montar(HojaProduccionView)
    expect(wrapper.text()).toContain('solo está disponible')
    expect(wrapper.findAll('button').find((boton) => boton.text() === 'Imprimir')!.attributes('disabled')).toBeDefined()
  })
})

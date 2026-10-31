import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import PrimeVue from 'primevue/config'
import { reactive } from 'vue'
import PedidoDetalleView from './PedidoDetalleView.vue'
import { ApiError } from '../api/cliente'

const { obtenerPedido, obtenerDespacho, listarSucursales, listarProductos, sesion } = vi.hoisted(() => ({
  obtenerPedido: vi.fn(), obtenerDespacho: vi.fn(), listarSucursales: vi.fn(), listarProductos: vi.fn(),
  sesion: { rol: 'GERENTE' },
}))
const route = reactive({ params: { id: '123' }, query: {} as Record<string, string> })
vi.mock('../api/pedidos', async (original) => ({ ...(await original<typeof import('../api/pedidos')>()), obtenerPedido, obtenerDespacho, listarSucursales, listarProductos }))
vi.mock('../stores/sesion', () => ({ useSesionStore: () => sesion }))
vi.mock('vue-router', async (original) => ({ ...(await original<typeof import('vue-router')>()), useRoute: () => route }))

const pedido = { idPedido: 123, idSucursal: 3, idUsuario: 1, fechaEntrega: '2026-10-31', estado: 'PENDIENTE', observacion: null,
  detalles: [{ idPedidoDetalle: 4, idPedido: 123, idProducto: 9, nombreProducto: 'Pan francés', cantidad: 2.5 }] }
const montajes: Array<{ unmount: () => void }> = []
function montar() {
  const wrapper = mount(PedidoDetalleView, { global: { plugins: [PrimeVue], stubs: { RouterLink: true } } })
  montajes.push(wrapper)
  return wrapper
}
afterEach(() => { montajes.splice(0).forEach((wrapper) => wrapper.unmount()) })
beforeEach(() => {
  vi.clearAllMocks()
  sesion.rol = 'GERENTE'
  obtenerPedido.mockResolvedValue({ ...pedido })
  listarSucursales.mockResolvedValue([{ idSucursal: 3, nombre: 'Centro' }])
  listarProductos.mockResolvedValue([{ idProducto: 9, nombre: 'Pan francés', abreviaturaUnidadMedida: 'kg', nombreUnidadMedida: 'kilogramo', estado: 'ACTIVO' }])
  obtenerDespacho.mockResolvedValue(null)
})
describe('PedidoDetalleView', () => {
  it('muestra producto, cantidad y unidad sin pedir despacho pendiente', async () => {
    const wrapper = montar()
    await flushPromises()
    expect(wrapper.text()).toContain('Pan francés')
    expect(wrapper.text()).toContain('2.5kg')
    expect(wrapper.text()).toContain('Centro')
    expect(obtenerDespacho).not.toHaveBeenCalled()
  })
  it('consulta el despacho solo para un pedido despachado', async () => {
    obtenerPedido.mockResolvedValue({ ...pedido, estado: 'DESPACHADO' })
    const wrapper = montar()
    await flushPromises()
    expect(obtenerDespacho).toHaveBeenCalledWith(123)
    expect(wrapper.text()).toContain('Sin despacho registrado.')
  })
  it('no solicita despacho para PRODUCCION, que carece de permiso', async () => {
    sesion.rol = 'PRODUCCION'
    obtenerPedido.mockResolvedValue({ ...pedido, estado: 'DESPACHADO' })
    const wrapper = montar()
    await flushPromises()
    expect(obtenerDespacho).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('No tiene permiso para consultar el despacho.')
  })
  it('avisa si no se pudieron cargar las unidades y permite reintentar', async () => {
    listarProductos.mockRejectedValueOnce(new Error('red'))
    const wrapper = montar()
    await flushPromises()
    expect(wrapper.text()).toContain('No se pudieron cargar las unidades de los productos.')
    await wrapper.findAll('button').find((boton) => boton.text() === 'Reintentar')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('kg')
  })
  it('informa un pedido inexistente', async () => {
    obtenerPedido.mockRejectedValue(new ApiError(404, 'NO_ENCONTRADO', 'No existe'))
    const wrapper = montar()
    await flushPromises()
    expect(wrapper.text()).toContain('No se encontró el pedido #123.')
  })
})

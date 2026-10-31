import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import PrimeVue from 'primevue/config'
import { reactive } from 'vue'
import PedidoDetalleView from './PedidoDetalleView.vue'
import { ApiError } from '../api/cliente'

const { obtenerPedido, obtenerDespacho, listarSucursales, listarProductos, cancelarPedido, recibirPedido, toast, confirmar, sesion } = vi.hoisted(() => ({
  obtenerPedido: vi.fn(), obtenerDespacho: vi.fn(), listarSucursales: vi.fn(), listarProductos: vi.fn(), cancelarPedido: vi.fn(), recibirPedido: vi.fn(),
  toast: vi.fn(), confirmar: vi.fn(), sesion: { rol: 'GERENTE' },
}))
const route = reactive({ params: { id: '123' }, query: {} as Record<string, string>, fullPath: '/pedidos/123' })
vi.mock('../api/pedidos', async (original) => ({ ...(await original<typeof import('../api/pedidos')>()), obtenerPedido, obtenerDespacho, listarSucursales, listarProductos, cancelarPedido, recibirPedido }))
vi.mock('../stores/sesion', () => ({ useSesionStore: () => sesion }))
vi.mock('vue-router', async (original) => ({ ...(await original<typeof import('vue-router')>()), useRoute: () => route }))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: toast }) }))
// El diálogo de confirmación acepta solo.
vi.mock('primevue/useconfirm', () => ({ useConfirm: () => ({ require: (opciones: { accept: () => void }) => { confirmar(opciones); opciones.accept() } }) }))

const pedido = { idPedido: 123, idSucursal: 3, idUsuario: 1, fechaEntrega: '2026-10-31', estado: 'PENDIENTE', observacion: null,
  detalles: [{ idPedidoDetalle: 4, idPedido: 123, idProducto: 9, nombreProducto: 'Pan francés', cantidad: 2.5 }] }
const montajes: Array<{ unmount: () => void }> = []
function montar() {
  const wrapper = mount(PedidoDetalleView, { global: { plugins: [PrimeVue], stubs: {
    RouterLink: { name: 'RouterLink', props: ['to'], template: '<a><slot /></a>' },
    Dialog: { props: ['visible'], template: '<div v-if="visible" class="dialogo-stub"><slot /></div>' },
  } } })
  montajes.push(wrapper)
  return wrapper
}
afterEach(() => { montajes.splice(0).forEach((wrapper) => wrapper.unmount()) })
beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })
  sesion.rol = 'GERENTE'
  obtenerPedido.mockResolvedValue({ ...pedido })
  listarSucursales.mockResolvedValue([{ idSucursal: 3, nombre: 'Centro' }])
  listarProductos.mockResolvedValue([{ idProducto: 9, nombre: 'Pan francés', abreviaturaUnidadMedida: 'kg', nombreUnidadMedida: 'kilogramo', estado: 'ACTIVO' }])
  obtenerDespacho.mockResolvedValue(null)
  cancelarPedido.mockResolvedValue({ ...pedido, estado: 'CANCELADO' })
  recibirPedido.mockResolvedValue({})
})
function botonCancelar(wrapper: ReturnType<typeof montar>) {
  return wrapper.findAll('button').find((boton) => boton.text() === 'Cancelar pedido')
}
describe('PedidoDetalleView', () => {
  it('muestra editar solo fuera de producción', async () => {
    sesion.rol = 'SUCURSAL'
    const wrapper = montar()
    await flushPromises()
    expect(wrapper.text()).toContain('Editar pedido')
    expect(wrapper.findAllComponents({ name: 'RouterLink' }).some((enlace) => JSON.stringify(enlace.props('to')).includes('pedido-editar'))).toBe(true)
    wrapper.unmount()
    montajes.pop()
    obtenerPedido.mockResolvedValue({ ...pedido, idProduccion: 2 })
    const ocupado = montar()
    await flushPromises()
    expect(ocupado.text()).not.toContain('Editar pedido')
  })

  it('registra recepción y muestra el despacho entregado', async () => {
    sesion.rol = 'SUCURSAL'
    obtenerPedido.mockResolvedValue({ ...pedido, estado: 'DESPACHADO' })
    obtenerDespacho.mockResolvedValueOnce({ idPedido: 123, estado: 'DESPACHADO', observacion: null, fechaDespacho: '2026-10-31T10:00:00' })
      .mockResolvedValueOnce({ idPedido: 123, estado: 'ENTREGADO', observacion: '[Recibido conforme]', fechaDespacho: '2026-10-31T10:00:00' })
    const wrapper = montar()
    await flushPromises()
    await wrapper.findAll('button').find((boton) => boton.text() === 'Confirmar recepción')!.trigger('click')
    await wrapper.find('.dialogo-stub form').trigger('submit')
    await flushPromises()
    expect(recibirPedido).toHaveBeenCalledWith(123, null)
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ summary: 'Recepción registrada' }))
    expect(wrapper.text()).toContain('Entregado')
  })
  it('muestra producto, cantidad y unidad sin pedir despacho pendiente', async () => {
    const wrapper = montar()
    await flushPromises()
    expect(wrapper.text()).toContain('Pan francés')
    expect(wrapper.text()).toContain('2.5kg')
    expect(wrapper.text()).toContain('Centro')
    expect(obtenerDespacho).not.toHaveBeenCalled()
  })
  it('vuelve a Despacho cuando se abrió desde allí', async () => {
    route.query = { volver: '/despacho?vista=despachados' }
    const wrapper = mount(PedidoDetalleView, { global: { plugins: [PrimeVue], stubs: { RouterLink: { props: ['to'], template: '<a :data-to="to"><slot /></a>' } } } })
    montajes.push(wrapper)
    await flushPromises()
    const volver = wrapper.find('.volver-pedidos')
    expect(volver.text()).toBe('← Despacho')
    expect(volver.attributes('data-to')).toBe('/despacho?vista=despachados')
    route.query = {}
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
  it('ofrece cancelar solo pedidos pendientes o confirmados a ADMINISTRADOR y SUCURSAL', async () => {
    const casos: Array<[string, string, boolean]> = [
      ['ADMINISTRADOR', 'PENDIENTE', true], ['SUCURSAL', 'CONFIRMADO', true], ['ADMINISTRADOR', 'EN_PRODUCCION', false],
      ['SUCURSAL', 'DESPACHADO', false], ['SUCURSAL', 'CANCELADO', false], ['GERENTE', 'PENDIENTE', false],
      ['PRODUCCION', 'CONFIRMADO', false], ['DESPACHO', 'CONFIRMADO', false],
    ]
    for (const [rol, estado, visible] of casos) {
      sesion.rol = rol
      obtenerPedido.mockResolvedValue({ ...pedido, estado })
      const wrapper = montar()
      await flushPromises()
      expect(Boolean(botonCancelar(wrapper)), `${rol} ${estado}`).toBe(visible)
      montajes.pop()
      wrapper.unmount()
    }
  })
  it('el botón de cancelar es secundario y destructivo, no relleno', async () => {
    sesion.rol = 'SUCURSAL'
    const wrapper = montar()
    await flushPromises()
    const boton = botonCancelar(wrapper)!
    expect(boton.classes()).toContain('p-button-danger')
    expect(boton.classes()).toContain('p-button-outlined')
    expect(boton.find('.pi-times').exists()).toBe(true)
  })
  it('confirma, cancela y recarga el pedido en su lugar', async () => {
    sesion.rol = 'SUCURSAL'
    obtenerPedido.mockResolvedValueOnce({ ...pedido }).mockResolvedValueOnce({ ...pedido, estado: 'CANCELADO' })
    const wrapper = montar()
    await flushPromises()
    await botonCancelar(wrapper)!.trigger('click')
    await flushPromises()
    expect(confirmar).toHaveBeenCalledWith(expect.objectContaining({
      header: '¿Cancelar el pedido #123?',
      message: 'El pedido quedará cancelado y no se podrá producir ni despachar. Solo se puede cancelar si aún no está en una producción.',
      acceptLabel: 'Cancelar pedido', rejectLabel: 'Volver', acceptProps: expect.objectContaining({ severity: 'danger' }),
    }))
    expect(cancelarPedido).toHaveBeenCalledWith(123)
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', summary: 'Pedido #123 cancelado' }))
    expect(obtenerPedido).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('Cancelado')
    expect(wrapper.text()).toContain('Pan francés')
    expect(botonCancelar(wrapper)).toBeUndefined()
  })
  it('no ofrece cancelar un pedido que ya está en una producción y enlaza a ella', async () => {
    sesion.rol = 'ADMINISTRADOR'
    obtenerPedido.mockResolvedValue({ ...pedido, estado: 'CONFIRMADO', idProduccion: 22 })
    const wrapper = montar()
    await flushPromises()
    expect(botonCancelar(wrapper)).toBeUndefined()
    expect(wrapper.text()).toContain('Producción')
    expect(wrapper.findAllComponents({ name: 'RouterLink' }).some((enlace) => JSON.stringify(enlace.props('to')).includes('"id":22'))).toBe(true)
  })
  it('conserva la página si otro lo asoció a una producción mientras tanto (409)', async () => {
    sesion.rol = 'ADMINISTRADOR'
    obtenerPedido.mockResolvedValue({ ...pedido, estado: 'CONFIRMADO' })
    cancelarPedido.mockRejectedValue(new ApiError(409, 'CONFLICTO', 'El pedido ya está asociado a una producción'))
    const wrapper = montar()
    await flushPromises()
    await botonCancelar(wrapper)!.trigger('click')
    await flushPromises()
    expect(cancelarPedido).toHaveBeenCalledWith(123)
    expect(toast).not.toHaveBeenCalled()
    expect(obtenerPedido).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('Pan francés')
    expect(botonCancelar(wrapper)!.attributes('disabled')).toBeUndefined()
  })
})

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { reactive, nextTick } from 'vue'
import PrimeVue from 'primevue/config'
import Select from 'primevue/select'
import InputNumber from 'primevue/inputnumber'
import NuevoPedidoView from './NuevoPedidoView.vue'
import { ApiError } from '../api/cliente'
import { hoyLocal, sumarDias } from '../api/fechas'

const { crearPedido, editarPedido, obtenerPedido, listarProductos, listarSucursales, replace, toast, sesion } = vi.hoisted(() => ({
  crearPedido: vi.fn(), editarPedido: vi.fn(), obtenerPedido: vi.fn(), listarProductos: vi.fn(), listarSucursales: vi.fn(), replace: vi.fn(), toast: vi.fn(),
  sesion: { rol: 'SUCURSAL', usuario: { idSucursal: 3 as number | null } },
}))
const route = reactive({ query: {} as Record<string, string>, name: 'pedido-nuevo', params: { id: '' } })
vi.mock('../api/pedidos', async (original) => ({ ...(await original<typeof import('../api/pedidos')>()), crearPedido, editarPedido, obtenerPedido, listarProductos, listarSucursales }))
vi.mock('../stores/sesion', () => ({ useSesionStore: () => sesion }))
vi.mock('vue-router', async (original) => ({ ...(await original<typeof import('vue-router')>()), useRoute: () => route, useRouter: () => ({ replace }), onBeforeRouteLeave: vi.fn() }))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: toast }) }))
vi.mock('primevue/useconfirm', () => ({ useConfirm: () => ({ require: vi.fn() }) }))

const productos = [
  { idProducto: 9, nombre: 'Pan francés', nombreTipoProducto: 'PRODUCTO_FINAL', abreviaturaUnidadMedida: 'UND', nombreUnidadMedida: 'Unidad', estado: 'ACTIVO' },
  { idProducto: 10, nombre: 'Pastel', nombreTipoProducto: 'PRODUCTO_FINAL', abreviaturaUnidadMedida: 'KG', nombreUnidadMedida: 'Kilogramo', estado: 'ACTIVO' },
]
const montajes: Array<{ unmount: () => void }> = []
async function montar() {
  const wrapper = mount(NuevoPedidoView, { global: { plugins: [PrimeVue], stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
  montajes.push(wrapper)
  await flushPromises()
  return wrapper
}
async function elegir(wrapper: Awaited<ReturnType<typeof montar>>, indice: number, valor: number) {
  wrapper.findAllComponents(Select)[indice]!.vm.$emit('update:modelValue', valor)
  await nextTick()
}
async function cantidad(wrapper: Awaited<ReturnType<typeof montar>>, indice: number, valor: number) {
  wrapper.findAllComponents(InputNumber)[indice]!.vm.$emit('update:modelValue', valor)
  await nextTick()
}
async function registrar(wrapper: Awaited<ReturnType<typeof montar>>) {
  await wrapper.find('form').trigger('submit')
  await flushPromises()
}
afterEach(() => montajes.splice(0).forEach((wrapper) => wrapper.unmount()))
beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn(), addListener: vi.fn(), removeListener: vi.fn() }))
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })
  sesion.rol = 'SUCURSAL'
  sesion.usuario.idSucursal = 3
  route.query = {}
  route.name = 'pedido-nuevo'
  route.params.id = ''
  listarProductos.mockResolvedValue(productos)
  listarSucursales.mockResolvedValue([{ idSucursal: 3, nombre: 'Centro' }])
  crearPedido.mockResolvedValue({ idPedido: 41 })
  replace.mockResolvedValue(undefined)
  editarPedido.mockResolvedValue({})
})

describe('NuevoPedidoView', () => {
  it('precarga un pedido para editar y guarda sin cambiar su sucursal', async () => {
    route.name = 'pedido-editar'
    route.params.id = '41'
    sesion.rol = 'ADMINISTRADOR'
    obtenerPedido.mockResolvedValue({
      idPedido: 41, idSucursal: 3, fechaEntrega: sumarDias(hoyLocal(), 1),
      estado: 'CONFIRMADO', observacion: 'Antes', idProduccion: null,
      detalles: [{ idPedidoDetalle: 2, idPedido: 41, idProducto: 9, nombreProducto: 'Pan', cantidad: 3 }],
    })
    const wrapper = await montar()
    expect(wrapper.text()).toContain('Editar pedido #41')
    expect(wrapper.findAllComponents(Select)).toHaveLength(1)
    expect(wrapper.findComponent(InputNumber).props('modelValue')).toBe(3)
    await cantidad(wrapper, 0, 4)
    await registrar(wrapper)
    expect(editarPedido).toHaveBeenCalledWith(41, {
      fechaEntrega: sumarDias(hoyLocal(), 1), observacion: 'Antes',
      detalles: [{ idProducto: 9, cantidad: 4 }],
    })
    expect(crearPedido).not.toHaveBeenCalled()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ summary: 'Pedido #41 actualizado' }))
    expect(replace).toHaveBeenCalledWith({ name: 'pedido', params: { id: 41 } })
  })
  it('usa la sucursal del usuario y oculta el selector', async () => {
    const wrapper = await montar()
    expect(wrapper.text()).toContain('Centro')
    expect(wrapper.findAllComponents(Select)).toHaveLength(1)
    await elegir(wrapper, 0, 9)
    await cantidad(wrapper, 0, 2.5)
    await registrar(wrapper)
    expect(crearPedido).toHaveBeenCalledWith({ idSucursal: 3, fechaEntrega: sumarDias(hoyLocal(), 1), observacion: null, detalles: [{ idProducto: 9, cantidad: 2.5 }] })
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', summary: 'Pedido #41 registrado' }))
    expect(replace).toHaveBeenCalledWith({ name: 'pedido', params: { id: 41 } })
  })

  it('exige elegir sucursal para administrador', async () => {
    sesion.rol = 'ADMINISTRADOR'
    sesion.usuario.idSucursal = null
    const wrapper = await montar()
    await elegir(wrapper, 1, 9)
    await cantidad(wrapper, 0, 1)
    await registrar(wrapper)
    expect(crearPedido).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Elija una sucursal.')
    await elegir(wrapper, 0, 3)
    await registrar(wrapper)
    expect(crearPedido).toHaveBeenCalledWith(expect.objectContaining({ idSucursal: 3 }))
  })

  it('muestra error 400 y conserva los datos', async () => {
    crearPedido.mockRejectedValue(new ApiError(400, 'DATOS_INVALIDOS', 'No se permite esa entrega'))
    const wrapper = await montar()
    await elegir(wrapper, 0, 9)
    await cantidad(wrapper, 0, 4)
    await registrar(wrapper)
    expect(wrapper.text()).toContain('No se permite esa entrega')
    expect(wrapper.findComponent(InputNumber).props('modelValue')).toBe(4)
    expect(replace).not.toHaveBeenCalled()
  })

  it('quita elegidos de otras líneas y permite agregar y quitar', async () => {
    const wrapper = await montar()
    await elegir(wrapper, 0, 9)
    await wrapper.findAll('button').find((boton) => boton.text().includes('Agregar producto'))!.trigger('click')
    await nextTick()
    expect(wrapper.findAllComponents(Select)).toHaveLength(2)
    expect((wrapper.findAllComponents(Select)[1]!.props('options') as typeof productos).map((item) => item.idProducto)).toEqual([10])
    await wrapper.find('button[aria-label="Quitar línea"]').trigger('click')
    expect(wrapper.findAllComponents(Select)).toHaveLength(1)
  })
})

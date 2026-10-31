import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import PrimeVue from 'primevue/config'
import InputNumber from 'primevue/inputnumber'
import Select from 'primevue/select'
import ProduccionDetalleView from './ProduccionDetalleView.vue'
import { ApiError } from '../api/cliente'

const api = vi.hoisted(() => ({
  obtenerProduccion: vi.fn(), obtenerDetalleProduccion: vi.fn(), asociarPedido: vi.fn(), generarPlan: vi.fn(),
  registrarProducido: vi.fn(), finalizarProduccion: vi.fn(), cancelarProduccion: vi.fn(), listarPedidos: vi.fn(), listarProductos: vi.fn(),
  toast: vi.fn(), confirmar: vi.fn(), sesion: { rol: 'PRODUCCION' },
}))
const route = reactive({ params: { id: '5' }, query: {} as Record<string, string> })
vi.mock('../api/producciones', async (original) => ({ ...(await original<typeof import('../api/producciones')>()),
  obtenerProduccion: api.obtenerProduccion, obtenerDetalleProduccion: api.obtenerDetalleProduccion, asociarPedido: api.asociarPedido,
  generarPlan: api.generarPlan, registrarProducido: api.registrarProducido, finalizarProduccion: api.finalizarProduccion,
  cancelarProduccion: api.cancelarProduccion }))
vi.mock('../api/pedidos', async (original) => ({ ...(await original<typeof import('../api/pedidos')>()),
  listarPedidos: api.listarPedidos, listarProductos: api.listarProductos }))
vi.mock('../stores/sesion', () => ({ useSesionStore: () => api.sesion }))
vi.mock('vue-router', async (original) => ({ ...(await original<typeof import('vue-router')>()), useRoute: () => route }))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: api.toast }) }))
// El diálogo de confirmación acepta solo.
vi.mock('primevue/useconfirm', () => ({ useConfirm: () => ({ require: (opciones: { accept: () => void }) => { api.confirmar(opciones); opciones.accept() } }) }))

function pedido(idPedido: number, estado = 'CONFIRMADO') {
  return { idPedido, idSucursal: 3, nombreSucursal: 'Centro', idUsuario: 1, nombreUsuario: 'Ana', fechaPedido: '2026-10-30T08:00:00',
    fechaEntrega: '2026-11-02', estado, observacion: null, cantidadProductos: 2 }
}
function produccion(estado: string, pedidos = [pedido(7, estado === 'PLANIFICADA' ? 'CONFIRMADO' : 'EN_PRODUCCION')]) {
  return { idProduccion: 5, idUsuario: 1, nombreUsuario: 'Luis', fechaProduccion: '2026-10-31T06:30:00', estado, observacion: 'Turno de la mañana', cantidadPedidos: pedidos.length, pedidos }
}
const productos = [
  { idProducto: 9, nombre: 'Pan francés', nombreTipoProducto: 'PRODUCTO_FINAL', abreviaturaUnidadMedida: 'UND', nombreUnidadMedida: 'Unidad', estado: 'ACTIVO' },
  { idProducto: 10, nombre: 'Pastel', nombreTipoProducto: 'PRODUCTO_FINAL', abreviaturaUnidadMedida: 'KG', nombreUnidadMedida: 'Kilogramo', estado: 'ACTIVO' },
]
const plan = [
  { idProduccionDetalle: 1, idProduccion: 5, idProducto: 9, idReceta: 2, cantidadPlanificada: 10, cantidadProducida: 0 },
  { idProduccionDetalle: 2, idProduccion: 5, idProducto: 10, idReceta: 3, cantidadPlanificada: 2.5, cantidadProducida: 2.5 },
]
const montajes: VueWrapper[] = []
async function montar() {
  const wrapper = mount(ProduccionDetalleView, { global: { plugins: [PrimeVue], stubs: {
    RouterLink: { template: '<a><slot /></a>' },
    Dialog: { props: ['visible'], template: '<div v-if="visible" class="dialogo-stub"><slot /></div>' },
  } } })
  montajes.push(wrapper)
  await flushPromises()
  return wrapper
}
function boton(wrapper: VueWrapper, texto: string) {
  return wrapper.findAll('button').find((item) => item.text() === texto)
}
afterEach(() => { montajes.splice(0).forEach((wrapper) => wrapper.unmount()) })
beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn(), addListener: vi.fn(), removeListener: vi.fn() }))
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })
  api.sesion.rol = 'PRODUCCION'
  route.query = {}
  api.obtenerProduccion.mockResolvedValue(produccion('PLANIFICADA'))
  api.obtenerDetalleProduccion.mockResolvedValue(plan.map((linea) => ({ ...linea })))
  api.listarProductos.mockResolvedValue(productos)
  api.listarPedidos.mockResolvedValue({ items: [pedido(7), pedido(8), pedido(9)], total: 3, pagina: 1, tamano: 100 })
  api.asociarPedido.mockResolvedValue({ idProduccion: 5, mensaje: 'ok' })
  api.generarPlan.mockResolvedValue([])
  api.registrarProducido.mockResolvedValue(undefined)
  api.finalizarProduccion.mockResolvedValue(undefined)
  api.cancelarProduccion.mockResolvedValue(produccion('CANCELADA', []))
})

describe('ProduccionDetalleView', () => {
  it('muestra datos, avance y pedidos de una producción planificada', async () => {
    const wrapper = await montar()
    expect(wrapper.find('h1').text()).toBe('Producción #5')
    expect(wrapper.text()).toContain('Luis')
    expect(wrapper.text()).toContain('Turno de la mañana')
    expect(wrapper.find('[aria-current="step"]').text()).toBe('Planificada')
    expect(wrapper.text()).toContain('#7')
    expect(wrapper.text()).toContain('Centro')
    expect(api.obtenerDetalleProduccion).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('Plan de producción')
  })

  it('muestra el plan con unidades y faltante', async () => {
    api.obtenerProduccion.mockResolvedValue(produccion('FINALIZADA'))
    const wrapper = await montar()
    expect(api.obtenerDetalleProduccion).toHaveBeenCalledWith(5)
    const filas = wrapper.findAll('.tabla-plan tbody tr').map((fila) => fila.findAll('td').map((celda) => celda.text()))
    expect(filas).toEqual([['Pan francés', '10u', '0u', '10u'], ['Pastel', '2.5kg', '2.5kg', '—']])
    expect(wrapper.text()).toContain('1 de 2 productos completos')
    expect(wrapper.find('[aria-current="step"]').text()).toBe('Finalizada')
  })

  it('no muestra acciones al gerente', async () => {
    api.sesion.rol = 'GERENTE'
    const planificada = await montar()
    expect(planificada.text()).not.toContain('Agregar pedido')
    expect(planificada.text()).not.toContain('Generar plan')
    api.obtenerProduccion.mockResolvedValue(produccion('EN_PROCESO'))
    const enProceso = await montar()
    expect(enProceso.findAllComponents(InputNumber)).toHaveLength(0)
    expect(enProceso.text()).not.toContain('Finalizar producción')
    expect(enProceso.text()).toContain('10u')
  })

  it('ofrece Agregar pedido solo en planificada', async () => {
    api.obtenerProduccion.mockResolvedValue(produccion('EN_PROCESO'))
    const wrapper = await montar()
    expect(wrapper.text()).not.toContain('Agregar pedido')
  })

  it('lista solo los confirmados no asociados y agrega el elegido', async () => {
    const wrapper = await montar()
    await boton(wrapper, 'Agregar pedido')!.trigger('click')
    await flushPromises()
    expect(api.listarPedidos).toHaveBeenCalledWith({ estado: 'CONFIRMADO' }, 1, 100)
    const select = wrapper.findComponent(Select)
    expect((select.props('options') as { value: number; label: string }[]).map((opcion) => opcion.value)).toEqual([8, 9])
    expect((select.props('options') as { label: string }[])[0]!.label).toMatch(/^#8 · Centro · entrega /)
    select.vm.$emit('update:modelValue', 8)
    await nextTick()
    await wrapper.find('.dialogo-stub form').trigger('submit')
    await flushPromises()
    expect(api.asociarPedido).toHaveBeenCalledWith(5, 8)
    expect(api.obtenerProduccion).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.dialogo-stub').exists()).toBe(false)
  })

  it('avisa cuando no hay pedidos confirmados para agregar', async () => {
    api.listarPedidos.mockResolvedValue({ items: [pedido(7)], total: 1, pagina: 1, tamano: 100 })
    const wrapper = await montar()
    await boton(wrapper, 'Agregar pedido')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('No hay pedidos confirmados para agregar.')
  })

  it('deshabilita Generar plan sin pedidos', async () => {
    api.obtenerProduccion.mockResolvedValue(produccion('PLANIFICADA', []))
    const wrapper = await montar()
    expect(wrapper.text()).toContain('Esta producción aún no tiene pedidos.')
    expect(boton(wrapper, 'Generar plan')!.attributes('disabled')).toBeDefined()
  })

  it('genera el plan tras confirmar y recarga', async () => {
    const wrapper = await montar()
    api.obtenerProduccion.mockResolvedValue(produccion('EN_PROCESO'))
    await boton(wrapper, 'Generar plan')!.trigger('click')
    await flushPromises()
    expect(api.confirmar).toHaveBeenCalledWith(expect.objectContaining({ header: '¿Generar el plan de producción?', acceptLabel: 'Generar plan' }))
    expect(api.generarPlan).toHaveBeenCalledWith(5)
    expect(api.generarPlan).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('Plan de producción')
    expect(wrapper.find('[aria-current="step"]').text()).toBe('En proceso')
  })

  it('registra la cantidad producida con los ids y el cuerpo correctos', async () => {
    api.obtenerProduccion.mockResolvedValue(produccion('EN_PROCESO'))
    const wrapper = await montar()
    const guardar = wrapper.find('button[aria-label="Guardar cantidad de Pan francés"]')
    expect(guardar.attributes('disabled')).toBeDefined()
    wrapper.findAllComponents(InputNumber)[0]!.vm.$emit('update:modelValue', 10)
    await nextTick()
    expect(guardar.attributes('disabled')).toBeUndefined()
    await guardar.trigger('click')
    await flushPromises()
    expect(api.registrarProducido).toHaveBeenCalledWith(5, 9, 10)
    expect(api.toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', summary: 'Cantidad registrada' }))
    expect(wrapper.text()).toContain('2 de 2 productos completos')
  })

  it('habilita Finalizar al completar y finaliza tras confirmar', async () => {
    api.obtenerProduccion.mockResolvedValue(produccion('EN_PROCESO'))
    const wrapper = await montar()
    expect(boton(wrapper, 'Finalizar producción')!.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Registre al menos lo planificado en cada producto.')
    wrapper.findAllComponents(InputNumber)[0]!.vm.$emit('update:modelValue', 12)
    await nextTick()
    await wrapper.find('button[aria-label="Guardar cantidad de Pan francés"]').trigger('click')
    await flushPromises()
    expect(boton(wrapper, 'Finalizar producción')!.attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).not.toContain('Registre al menos lo planificado en cada producto.')
    api.obtenerProduccion.mockResolvedValue(produccion('FINALIZADA'))
    api.obtenerDetalleProduccion.mockResolvedValue([{ ...plan[0]!, cantidadProducida: 12 }, plan[1]!])
    await boton(wrapper, 'Finalizar producción')!.trigger('click')
    await flushPromises()
    expect(api.confirmar).toHaveBeenCalledWith(expect.objectContaining({ header: '¿Finalizar la producción?', acceptLabel: 'Finalizar' }))
    expect(api.finalizarProduccion).toHaveBeenCalledWith(5)
    expect(api.toast).toHaveBeenCalledWith(expect.objectContaining({ summary: 'Producción finalizada' }))
    expect(wrapper.text()).not.toContain('Finalizar producción')
  })

  it('conserva los datos si falla una acción', async () => {
    api.generarPlan.mockRejectedValue(new ApiError(409, 'CONFLICTO', 'Existen pedidos que ya no están confirmados'))
    const wrapper = await montar()
    await boton(wrapper, 'Generar plan')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('#7')
    expect(boton(wrapper, 'Generar plan')!.attributes('disabled')).toBeUndefined()
  })

  it('ofrece cancelar solo producciones planificadas a ADMINISTRADOR y PRODUCCION', async () => {
    const casos: Array<[string, string, boolean]> = [
      ['PRODUCCION', 'PLANIFICADA', true], ['ADMINISTRADOR', 'PLANIFICADA', true], ['GERENTE', 'PLANIFICADA', false],
      ['PRODUCCION', 'EN_PROCESO', false], ['ADMINISTRADOR', 'FINALIZADA', false], ['PRODUCCION', 'CANCELADA', false],
    ]
    for (const [rol, estado, visible] of casos) {
      api.sesion.rol = rol
      api.obtenerProduccion.mockResolvedValue(produccion(estado))
      const wrapper = await montar()
      expect(Boolean(boton(wrapper, 'Cancelar producción')), `${rol} ${estado}`).toBe(visible)
      montajes.pop()
      wrapper.unmount()
    }
  })

  it('confirma, cancela y recarga la producción en su lugar', async () => {
    api.obtenerProduccion.mockResolvedValueOnce(produccion('PLANIFICADA')).mockResolvedValueOnce(produccion('CANCELADA', []))
    const wrapper = await montar()
    const cancelar = boton(wrapper, 'Cancelar producción')!
    expect(cancelar.classes()).toContain('p-button-danger')
    expect(cancelar.classes()).toContain('p-button-outlined')
    await cancelar.trigger('click')
    await flushPromises()
    expect(api.confirmar).toHaveBeenCalledWith(expect.objectContaining({
      header: '¿Cancelar la producción #5?', message: 'Se cancelará y sus pedidos quedarán libres para otra producción.',
      acceptLabel: 'Cancelar producción', rejectLabel: 'Volver', acceptProps: expect.objectContaining({ severity: 'danger' }),
    }))
    expect(api.cancelarProduccion).toHaveBeenCalledWith(5)
    expect(api.toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', summary: 'Producción #5 cancelada' }))
    expect(api.obtenerProduccion).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('Cancelada')
    expect(wrapper.text()).toContain('Esta producción aún no tiene pedidos.')
    expect(boton(wrapper, 'Cancelar producción')).toBeUndefined()
    expect(boton(wrapper, 'Generar plan')).toBeUndefined()
  })

  it('conserva la página si la producción ya no se puede cancelar (409)', async () => {
    api.cancelarProduccion.mockRejectedValue(new ApiError(409, 'CONFLICTO', 'Solo se puede cancelar una producción planificada'))
    const wrapper = await montar()
    await boton(wrapper, 'Cancelar producción')!.trigger('click')
    await flushPromises()
    expect(api.cancelarProduccion).toHaveBeenCalledWith(5)
    expect(api.toast).not.toHaveBeenCalled()
    expect(api.obtenerProduccion).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('#7')
    expect(boton(wrapper, 'Cancelar producción')!.attributes('disabled')).toBeUndefined()
  })

  it('informa una producción inexistente', async () => {
    api.obtenerProduccion.mockRejectedValue(new ApiError(404, 'NO_ENCONTRADO', 'No existe'))
    const wrapper = await montar()
    expect(wrapper.text()).toContain('No se encontró la producción #5.')
    expect(wrapper.text()).toContain('Volver a Producción')
  })
})

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { reactive, nextTick } from 'vue'
import PrimeVue from 'primevue/config'
import PedidosView from './PedidosView.vue'

const { listarPedidos, listarSucursales, replace, push, sesion } = vi.hoisted(() => ({
  listarPedidos: vi.fn(), listarSucursales: vi.fn(), replace: vi.fn(), push: vi.fn(),
  sesion: { rol: 'GERENTE', usuario: { idSucursal: null as number | null } },
}))
const route = reactive({ query: {} as Record<string, string>, fullPath: '/pedidos' })
vi.mock('../api/pedidos', async (original) => ({ ...(await original<typeof import('../api/pedidos')>()), listarPedidos, listarSucursales }))
vi.mock('../stores/sesion', () => ({ useSesionStore: () => sesion }))
vi.mock('vue-router', async (original) => ({
  ...(await original<typeof import('vue-router')>()), useRoute: () => route, useRouter: () => ({ replace, push }),
}))

const vacia = { items: [], total: 0, pagina: 1, tamano: 20 }
const montajes: Array<{ unmount: () => void }> = []
function montar() {
  const wrapper = mount(PedidosView, { global: { plugins: [PrimeVue], stubs: { RouterLink: true } } })
  montajes.push(wrapper)
  return wrapper
}
afterEach(() => { montajes.splice(0).forEach((wrapper) => wrapper.unmount()) })

beforeEach(() => {
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn(), addListener: vi.fn(), removeListener: vi.fn() }))
  vi.clearAllMocks()
  route.query = {}
  sesion.rol = 'GERENTE'
  sesion.usuario.idSucursal = null
  listarPedidos.mockResolvedValue(vacia)
  listarSucursales.mockResolvedValue([{ idSucursal: 3, nombre: 'Centro' }])
})

describe('PedidosView', () => {
  it('consulta desde hoy con página 1 y muestra la salida vacía de inicio', async () => {
    const wrapper = montar()
    await flushPromises()
    expect(listarPedidos).toHaveBeenCalledWith(expect.objectContaining({ entregaDesde: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/), entregaHasta: undefined }), 1, 20)
    expect(wrapper.text()).toContain('No hay pedidos para entregar desde hoy.')
    expect(wrapper.text()).toContain('Ver todas las fechas')
  })

  it('oculta sucursal para SUCURSAL y aplica su id a la consulta', async () => {
    sesion.rol = 'SUCURSAL'
    sesion.usuario.idSucursal = 3
    const wrapper = montar()
    await flushPromises()
    expect(wrapper.text()).not.toContain('Todas las sucursales')
    expect(wrapper.findAll('th').map((celda) => celda.text())).not.toContain('Sucursal')
    expect(listarPedidos).toHaveBeenCalledWith(expect.objectContaining({ idSucursal: 3 }), 1, 20)
  })

  it('muestra error en la tabla y permite reintentar', async () => {
    listarPedidos.mockRejectedValueOnce(new Error('red'))
    const wrapper = montar()
    await flushPromises()
    expect(wrapper.text()).toContain('No se pudieron cargar los pedidos.')
    await wrapper.findAll('button').find((boton) => boton.text() === 'Reintentar')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('No hay pedidos para entregar desde hoy.')
  })

  it('descarta una respuesta anterior cuando cambia la query', async () => {
    let resolver!: (valor: typeof vacia) => void
    listarPedidos.mockImplementationOnce(() => new Promise((resolve) => { resolver = resolve }))
    const wrapper = montar()
    route.query = { entrega: 'todas' }
    await nextTick()
    await flushPromises()
    resolver({ ...vacia, total: 1, items: [{ idPedido: 7, idSucursal: 3, nombreSucursal: 'Centro', idUsuario: 1, nombreUsuario: 'Ana', fechaPedido: '2026-10-31T10:00:00', fechaEntrega: '2026-11-01', estado: 'PENDIENTE', observacion: null, cantidadProductos: 2 }] } as never)
    await flushPromises()
    expect(wrapper.text()).not.toContain('#7')
    expect(wrapper.text()).toContain('No hay pedidos con estos filtros.')
  })
  it('regresa a la última página si la query pide una página fuera de rango', async () => {
    route.query = { pagina: '99' }
    listarPedidos.mockResolvedValue({ ...vacia, total: 21, pagina: 99 })
    const wrapper = mount(PedidosView, { global: { plugins: [PrimeVue], stubs: {
      RouterLink: true, DataTable: true, Column: true, Select: true, SelectButton: true,
    } } })
    montajes.push(wrapper)
    await flushPromises()
    expect(replace).toHaveBeenCalledWith({ name: 'pedidos', query: { pagina: '2' } })
  })
})

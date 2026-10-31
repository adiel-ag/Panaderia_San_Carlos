import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import PrimeVue from 'primevue/config'
import SucursalesView from './SucursalesView.vue'

const { listarTodasSucursales } = vi.hoisted(() => ({ listarTodasSucursales: vi.fn() }))
vi.mock('../api/sucursales', async (original) => ({ ...(await original<typeof import('../api/sucursales')>()), listarTodasSucursales }))
vi.mock('primevue/useconfirm', () => ({ useConfirm: () => ({ require: vi.fn() }) }))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: vi.fn() }) }))
const montajes: VueWrapper[] = []
async function montar() {
  const wrapper = mount(SucursalesView, { global: { plugins: [PrimeVue] } })
  montajes.push(wrapper)
  await flushPromises()
  return wrapper
}
afterEach(() => montajes.splice(0).forEach((wrapper) => wrapper.unmount()))
beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }))
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })
  listarTodasSucursales.mockResolvedValue([{ idSucursal: 7, nombre: 'Centro', direccion: 'Calle 1',
    telefono: '1234', usuariosActivos: 2, pedidosAbiertos: 3, estado: 'ACTIVO' }])
})
describe('SucursalesView', () => {
  it('muestra cantidades, estado y acciones de administración', async () => {
    const wrapper = await montar()
    expect(wrapper.text()).toContain('Nueva sucursal')
    expect(wrapper.find('tbody').text()).toContain('Centro')
    expect(wrapper.find('tbody').text()).toContain('Calle 1')
    expect(wrapper.find('tbody').text()).toContain('1234')
    expect(wrapper.find('[aria-label="Editar Centro"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="Desactivar Centro"]').exists()).toBe(true)
  })
  it('abre formulario de edición', async () => {
    const wrapper = await montar()
    await wrapper.find('[aria-label="Editar Centro"]').trigger('click')
    await flushPromises()
    expect(document.body.querySelector('#sucursal-nombre')).not.toBeNull()
    expect(document.body.querySelector('#sucursal-telefono')).not.toBeNull()
  })
})

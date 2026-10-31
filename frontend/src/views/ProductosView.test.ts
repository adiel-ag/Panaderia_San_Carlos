import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { reactive } from 'vue'
import PrimeVue from 'primevue/config'
import SelectButton from 'primevue/selectbutton'
import Select from 'primevue/select'
import ProductosView from './ProductosView.vue'

const { listarProductos, listarUnidades, listarTiposProducto, replace, sesion } = vi.hoisted(() => ({
  listarProductos: vi.fn(), listarUnidades: vi.fn(), listarTiposProducto: vi.fn(), replace: vi.fn(),
  sesion: { rol: 'ADMINISTRADOR' },
}))
const route = reactive({ query: {} as Record<string, string> })
vi.mock('../api/productos', async (original) => ({ ...(await original<typeof import('../api/productos')>()),
  listarProductos, listarUnidades, listarTiposProducto }))
vi.mock('../stores/sesion', () => ({ useSesionStore: () => sesion }))
vi.mock('vue-router', async (original) => ({ ...(await original<typeof import('vue-router')>()),
  useRoute: () => route, useRouter: () => ({ replace }) }))
vi.mock('primevue/useconfirm', () => ({ useConfirm: () => ({ require: vi.fn() }) }))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: vi.fn() }) }))

const lista = [
  { idProducto: 1, nombre: 'Harina', nombreTipoProducto: 'INSUMO', abreviaturaUnidadMedida: 'KG', stockMinimo: 2, estado: 'ACTIVO', enUso: true, idTipoProducto: 1, idUnidad: 2 },
  { idProducto: 2, nombre: 'Pan', nombreTipoProducto: 'PRODUCTO_FINAL', abreviaturaUnidadMedida: 'UND', stockMinimo: 1, estado: 'INACTIVO', enUso: false, idTipoProducto: 2, idUnidad: 1 },
]
const montajes: VueWrapper[] = []
async function montar() {
  const wrapper = mount(ProductosView, { global: { plugins: [PrimeVue] } })
  montajes.push(wrapper)
  await flushPromises()
  return wrapper
}
afterEach(() => montajes.splice(0).forEach((wrapper) => wrapper.unmount()))
beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }))
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })
  route.query = {}
  sesion.rol = 'ADMINISTRADOR'
  listarProductos.mockResolvedValue(lista)
  listarUnidades.mockResolvedValue([{ idUnidad: 1, nombre: 'Unidad', abreviatura: 'UND' }])
  listarTiposProducto.mockResolvedValue([{ idTipoProducto: 1, nombre: 'Insumo' }])
})

describe('ProductosView', () => {
  it('resume todos y muestra activos por defecto', async () => {
    const wrapper = await montar()
    expect(wrapper.text()).toContain('1 insumos · 1 productos finales · 1 inactivos')
    expect(wrapper.text()).toContain('Harina')
    expect(wrapper.find('tbody').text()).not.toContain('Pan')
    expect(wrapper.text()).toContain('Nuevo producto')
  })
  it('usa query para filtros y deja a gerencia solo consultar', async () => {
    route.query = { estado: 'INACTIVO' }
    sesion.rol = 'GERENTE'
    const wrapper = await montar()
    expect(wrapper.find('tbody').text()).toContain('Pan')
    expect(wrapper.text()).not.toContain('Nuevo producto')
    expect(wrapper.text()).not.toContain('Editar')
    wrapper.findAllComponents(SelectButton)[0]!.vm.$emit('update:modelValue', 'insumos')
    expect(replace).toHaveBeenCalledWith({ name: 'productos', query: { tipo: 'insumos', estado: 'INACTIVO' } })
  })
  it('bloquea tipo y unidad al editar un producto usado', async () => {
    const wrapper = await montar()
    await wrapper.find('[aria-label="Editar Harina"]').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('El tipo y la unidad no se pueden cambiar')
    expect(wrapper.findAllComponents(Select).map((select) => select.props('disabled'))).toEqual([true, true])
  })
})

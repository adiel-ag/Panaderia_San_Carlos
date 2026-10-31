import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import InicioView from './InicioView.vue'

const { sesion, listarPedidos, listarProducciones, listarExistencias, listarRecetas } = vi.hoisted(() => ({
  sesion: { rol: 'ADMINISTRADOR', usuario: { nombre: 'Ana', idSucursal: null } },
  listarPedidos: vi.fn(), listarProducciones: vi.fn(), listarExistencias: vi.fn(), listarRecetas: vi.fn(),
}))
vi.mock('../stores/sesion', () => ({ useSesionStore: () => sesion }))
vi.mock('../api/pedidos', async (original) => ({ ...(await original<typeof import('../api/pedidos')>()), listarPedidos }))
vi.mock('../api/producciones', async (original) => ({ ...(await original<typeof import('../api/producciones')>()), listarProducciones }))
vi.mock('../api/inventario', async (original) => ({ ...(await original<typeof import('../api/inventario')>()), listarExistencias }))
vi.mock('../api/recetas', async (original) => ({ ...(await original<typeof import('../api/recetas')>()), listarRecetas }))

beforeEach(() => {
  vi.clearAllMocks()
  sesion.rol = 'ADMINISTRADOR'
  listarPedidos.mockImplementation((filtros: { estado: string }) => Promise.resolve({ items: [], total: filtros.estado === 'PENDIENTE' ? 3 : filtros.estado === 'LISTO_DESPACHO' ? 2 : 0, pagina: 1, tamano: 1 }))
  listarProducciones.mockResolvedValue({ items: [], total: 1, pagina: 1, tamano: 1 })
  listarExistencias.mockResolvedValue({ items: [], total: 4, pagina: 1, tamano: 1 })
  listarRecetas.mockResolvedValue([{ recetasVigentes: 0 }, { recetasVigentes: 1 }])
})

describe('InicioView', () => {
  it('presenta indicadores y gráfico del día con totales completos de la API', async () => {
    const wrapper = mount(InicioView, { global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
    await flushPromises()
    expect(wrapper.text()).toContain('Operación de hoy')
    expect(wrapper.text()).toContain('Pedidos con entrega hoy')
    expect(wrapper.text()).toContain('5')
    expect(wrapper.find('[aria-label="Pedidos por estado para hoy"]').exists()).toBe(true)
    expect(listarPedidos).toHaveBeenCalledWith(expect.objectContaining({ estado: 'PENDIENTE', entregaDesde: expect.any(String), entregaHasta: expect.any(String) }), 1, 1, false)
    expect(wrapper.text()).toContain('Productos sin receta')
  })

  it('para BODEGA consulta inventario sin pedir datos de pedidos ni producción', async () => {
    sesion.rol = 'BODEGA'
    const wrapper = mount(InicioView, { global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
    await flushPromises()
    expect(listarPedidos).not.toHaveBeenCalled()
    expect(listarProducciones).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Insumos bajo mínimo')
  })
})

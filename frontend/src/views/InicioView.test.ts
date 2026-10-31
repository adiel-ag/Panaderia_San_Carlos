import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import InicioView from './InicioView.vue'

const { sesion, listarPedidos, listarProducciones, listarExistencias, listarRecetas, obtenerAlertas } = vi.hoisted(() => ({
  sesion: { rol: 'ADMINISTRADOR', usuario: { nombre: 'Ana', idSucursal: null } },
  listarPedidos: vi.fn(), listarProducciones: vi.fn(), listarExistencias: vi.fn(), listarRecetas: vi.fn(), obtenerAlertas: vi.fn(),
}))
vi.mock('../api/reportes', () => ({ obtenerAlertas }))
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
  obtenerAlertas.mockResolvedValue({ pedidosAtrasados: 0, productosSinReceta: 0, insumosBajoMinimo: 0 })
})
const enlace = { props: ['to'], template: '<a :data-to="JSON.stringify(to)"><slot /></a>' }

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

  it('muestra solo las alertas con conteo mayor que cero y enlaza al filtro de cada módulo', async () => {
    obtenerAlertas.mockResolvedValue({ pedidosAtrasados: 3, productosSinReceta: 0, insumosBajoMinimo: 1 })
    const wrapper = mount(InicioView, { global: { stubs: { RouterLink: enlace } } })
    await flushPromises()
    const lista = wrapper.find('[aria-label="Alertas"]')
    expect(lista.exists()).toBe(true)
    expect(lista.text()).toContain('3 pedidos atrasados')
    expect(lista.text()).toContain('1 insumo bajo el mínimo')
    expect(lista.text()).not.toContain('sin receta')
    const destinos = lista.findAll('a').map((item) => JSON.parse(item.attributes('data-to')!))
    expect(destinos).toEqual([
      { name: 'pedidos', query: { entrega: 'todas' } },
      { name: 'inventario', query: { tipo: 'INSUMO', bajo: '1' } },
    ])
    // Las alertas van antes que los indicadores.
    expect(wrapper.html().indexOf('Alertas')).toBeLessThan(wrapper.html().indexOf('Pedidos con entrega hoy'))
  })

  it('enlaza los productos sin receta al filtro de Recetas', async () => {
    obtenerAlertas.mockResolvedValue({ pedidosAtrasados: 0, productosSinReceta: 2, insumosBajoMinimo: 0 })
    const wrapper = mount(InicioView, { global: { stubs: { RouterLink: enlace } } })
    await flushPromises()
    const alerta = wrapper.find('[data-alerta="sin-receta"]')
    expect(alerta.text()).toBe('2 productos sin receta')
    expect(JSON.parse(alerta.find('a').attributes('data-to')!)).toEqual({ name: 'recetas', query: { filtro: 'sin-receta' } })
  })

  it('oculta el bloque de alertas si todas son cero o si la consulta falla', async () => {
    const vacio = mount(InicioView, { global: { stubs: { RouterLink: enlace } } })
    await flushPromises()
    expect(vacio.find('[aria-label="Alertas"]').exists()).toBe(false)
    obtenerAlertas.mockRejectedValue(new Error('red'))
    const fallo = mount(InicioView, { global: { stubs: { RouterLink: enlace } } })
    await flushPromises()
    expect(fallo.find('[aria-label="Alertas"]').exists()).toBe(false)
    expect(fallo.text()).toContain('Pedidos con entrega hoy')
  })
})

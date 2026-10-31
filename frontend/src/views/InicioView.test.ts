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

  it('muestra la fecha de hoy y el rol en texto normal, sin la marca repetida', async () => {
    const wrapper = mount(InicioView, { global: { stubs: { RouterLink: enlace } } })
    await flushPromises()
    const fecha = wrapper.get('.saludo-fecha').text()
    const esperada = new Intl.DateTimeFormat('es-GT', { day: 'numeric', month: 'long' }).formatToParts(new Date())
    expect(fecha).toMatch(/^\p{Lu}\p{Ll}+ \d{1,2} de \p{Ll}+ · Administrador$/u)
    expect(fecha).toContain(`${esperada.find((parte) => parte.type === 'day')?.value} de ${esperada.find((parte) => parte.type === 'month')?.value}`)
    expect(wrapper.find('.saludo-contexto').exists()).toBe(false)
  })

  it('al pasar la medianoche actualiza la fecha y consulta los pedidos del día nuevo', async () => {
    vi.useFakeTimers({ toFake: ['Date', 'setTimeout', 'clearTimeout'] })
    try {
      vi.setSystemTime(new Date(2026, 9, 31, 23, 59, 0))
      const wrapper = mount(InicioView, { global: { stubs: { RouterLink: enlace } } })
      await flushPromises()
      expect(wrapper.get('.saludo-fecha').text()).toContain('31 de octubre')
      listarPedidos.mockClear()
      await vi.advanceTimersByTimeAsync(2 * 60 * 1000)
      await flushPromises()
      expect(wrapper.get('.saludo-fecha').text()).toContain('1 de noviembre')
      expect(listarPedidos).toHaveBeenCalledWith(expect.objectContaining({ entregaDesde: '2026-11-01', entregaHasta: '2026-11-01' }), 1, 1, false)
      wrapper.unmount()
    } finally {
      vi.useRealTimers()
    }
  })

  it('una carga vieja que responde tarde no pisa las cifras de la carga más reciente', async () => {
    let soltarVieja: () => void = () => {}
    const vieja = new Promise<void>((resolver) => { soltarVieja = resolver })
    listarPedidos.mockImplementation(async (filtros: { estado: string }) => {
      await vieja
      return { items: [], total: filtros.estado === 'PENDIENTE' ? 9 : 0, pagina: 1, tamano: 1 }
    })
    obtenerAlertas.mockImplementationOnce(async () => { await vieja; return { pedidosAtrasados: 5, productosSinReceta: 0, insumosBajoMinimo: 0 } })
    const wrapper = mount(InicioView, { global: { stubs: { RouterLink: enlace } } })
    await flushPromises()
    // Segunda carga (como la de medianoche): responde al instante con otras cifras.
    listarPedidos.mockImplementation((filtros: { estado: string }) => Promise.resolve({ items: [], total: filtros.estado === 'PENDIENTE' ? 1 : 0, pagina: 1, tamano: 1 }))
    const vm = wrapper.vm as unknown as { cargar?: () => Promise<void> }
    const actualizar = wrapper.findAll('button').find((boton) => boton.text().includes('Actualizar'))
    if (actualizar) await actualizar.trigger('click')
    else await vm.cargar?.()
    await flushPromises()
    soltarVieja()
    await flushPromises()
    const valor = wrapper.findAll('.dashboard-indicador').find((tarjeta) => tarjeta.text().includes('Pedidos con entrega hoy'))
    expect(valor?.get('.dashboard-indicador-valor').text()).toBe('1')
    expect(wrapper.find('[aria-label="Alertas"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Cargando indicadores')
  })

  it('presenta Hoy en el horno con el conteo de cada etapa y enlaza a Pedidos filtrado', async () => {
    const wrapper = mount(InicioView, { global: { stubs: { RouterLink: enlace } } })
    await flushPromises()
    const horno = wrapper.get('[aria-labelledby="titulo-horno"]')
    expect(horno.get('h3').text()).toBe('Hoy en el horno')
    const pasos = horno.findAll('.horno-paso')
    expect(pasos.map((paso) => [paso.attributes('data-etapa'), paso.find('.horno-valor').text(), paso.find('.horno-etiqueta').text()])).toEqual([
      ['PENDIENTE', '3', 'Pendiente'],
      ['CONFIRMADO', '0', 'Confirmado'],
      ['EN_PRODUCCION', '0', 'En producción'],
      ['LISTO_DESPACHO', '2', 'Listo para despacho'],
      ['DESPACHADO', '0', 'Despachado'],
    ])
    expect(pasos.map((paso) => JSON.parse(paso.get('a').attributes('data-to')!))).toEqual(
      ['PENDIENTE', 'CONFIRMADO', 'EN_PRODUCCION', 'LISTO_DESPACHO', 'DESPACHADO'].map((estado) => ({ name: 'pedidos', query: { entrega: 'hoy', estado } })),
    )
    expect(horno.text()).not.toContain('Cancelado')
    expect(pasos[0]!.get('a').attributes('aria-label')).toBe('3 Pendiente: pedidos con entrega hoy')
  })

  it('no muestra Hoy en el horno a quien no consulta pedidos', async () => {
    sesion.rol = 'BODEGA'
    const wrapper = mount(InicioView, { global: { stubs: { RouterLink: enlace } } })
    await flushPromises()
    expect(wrapper.find('[aria-labelledby="titulo-horno"]').exists()).toBe(false)
  })
})

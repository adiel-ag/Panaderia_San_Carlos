import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { reactive } from 'vue'
import PrimeVue from 'primevue/config'
import ReportesView from './ReportesView.vue'
import { ApiError } from '../api/cliente'

const api = vi.hoisted(() => ({
  pedidos: vi.fn(), produccion: vi.fn(), consumo: vi.fn(), replace: vi.fn(), descargar: vi.fn(),
}))
const route = reactive({ query: {} as Record<string, string> })
vi.mock('../api/reportes', () => ({ reportePedidosPorSucursal: api.pedidos, reporteProduccionPorDia: api.produccion, reporteConsumoInsumos: api.consumo }))
vi.mock('vue-router', async (original) => ({ ...(await original<typeof import('vue-router')>()), useRoute: () => route, useRouter: () => ({ replace: api.replace }) }))
vi.mock('./reportes', async (original) => ({ ...(await original<typeof import('./reportes')>()), descargarCsv: api.descargar }))

const montajes: VueWrapper[] = []
async function montar() {
  const wrapper = mount(ReportesView, { global: { plugins: [PrimeVue] } })
  montajes.push(wrapper)
  await flushPromises()
  return wrapper
}
function boton(wrapper: VueWrapper, texto: string) {
  return wrapper.findAll('button').find((item) => item.text().includes(texto))
}
afterEach(() => { montajes.splice(0).forEach((wrapper) => wrapper.unmount()) })
beforeEach(() => {
  vi.clearAllMocks()
  route.query = { reporte: 'pedidos-por-sucursal', desde: '2026-10-01', hasta: '2026-10-31' }
  api.pedidos.mockResolvedValue([
    { idSucursal: 1, sucursal: 'Centro', pedidos: 3, despachados: 1, pendientes: 2, productos: [{ idProducto: 9, producto: 'Pan', unidad: 'UND', cantidad: 40 }] },
    { idSucursal: 2, sucursal: 'Norte', pedidos: 2, despachados: 2, pendientes: 0, productos: [] },
  ])
  api.produccion.mockResolvedValue([{ fecha: '2026-10-05', producciones: 2, productos: [{ idProducto: 9, producto: 'Pan', unidad: 'UND', planificado: 50, producido: 48 }] }])
  api.consumo.mockResolvedValue([
    { idInsumo: 2, insumo: 'Harina', unidad: 'KG', consumido: 20 }, { idInsumo: 3, insumo: 'Azúcar', unidad: 'KG', consumido: 5 },
  ])
})

describe('ReportesView', () => {
  it('consulta pedidos por sucursal con el rango de la URL y muestra totales', async () => {
    const wrapper = await montar()
    expect(api.pedidos).toHaveBeenCalledWith('2026-10-01', '2026-10-31')
    expect(wrapper.text()).toContain('Centro')
    expect(wrapper.text()).toContain('Pan: 40UND')
    expect(wrapper.find('tfoot').text()).toContain('Total')
    expect(wrapper.find('tfoot').text()).toContain('5')
    expect(api.produccion).not.toHaveBeenCalled()
  })

  it('usa del primer día del mes a hoy cuando la URL no trae fechas', async () => {
    route.query = {}
    await montar()
    const [desde, hasta] = api.pedidos.mock.calls[0] as [string, string]
    const hoy = new Date()
    const esperado = `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`
    expect(hasta).toBe(esperado)
    expect(desde).toBe(`${esperado.slice(0, 8)}01`)
  })

  it('cambia de reporte por la URL y dibuja barras de consumo', async () => {
    route.query = { reporte: 'consumo-insumos', desde: '2026-10-01', hasta: '2026-10-31' }
    const wrapper = await montar()
    expect(api.consumo).toHaveBeenCalledWith('2026-10-01', '2026-10-31')
    const barras = wrapper.findAll('.barras-consumo .dashboard-barra')
    expect(barras.map((barra) => barra.attributes('style'))).toEqual(['width: 100%;', 'width: 25%;'])
    expect(wrapper.text()).toContain('2 insumos')
  })

  it('muestra producción por día', async () => {
    route.query = { reporte: 'produccion-por-dia', desde: '2026-10-01', hasta: '2026-10-31' }
    const wrapper = await montar()
    expect(api.produccion).toHaveBeenCalled()
    expect(wrapper.text()).toContain('48UND')
  })

  it('valida el rango antes de navegar y conserva el reporte elegido', async () => {
    const wrapper = await montar()
    await wrapper.find('#reporte-desde').setValue('2026-11-05')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.text()).toContain('La fecha inicial no puede ser posterior a la final.')
    expect(api.replace).not.toHaveBeenCalled()
    await wrapper.find('#reporte-desde').setValue('2026-09-01')
    await wrapper.find('form').trigger('submit')
    expect(api.replace).toHaveBeenCalledWith({ name: 'reportes', query: { reporte: 'pedidos-por-sucursal', desde: '2026-09-01', hasta: '2026-10-31' } })
  })

  it('muestra en línea el mensaje 400 del servidor', async () => {
    api.pedidos.mockRejectedValue(new ApiError(400, 'Solicitud inválida', 'El rango no puede superar 366 días.'))
    const wrapper = await montar()
    expect(wrapper.text()).toContain('El rango no puede superar 366 días.')
    expect(boton(wrapper, 'Descargar CSV')?.attributes('disabled')).toBeDefined()
  })

  it('muestra error general con reintento y estado vacío', async () => {
    api.pedidos.mockRejectedValueOnce(new Error('red')).mockResolvedValueOnce([])
    const wrapper = await montar()
    expect(wrapper.text()).toContain('No se pudo cargar el reporte.')
    await boton(wrapper, 'Reintentar')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('No hay datos en este rango de fechas.')
  })

  it('descarga el CSV del reporte visible con su nombre de archivo', async () => {
    const wrapper = await montar()
    await boton(wrapper, 'Descargar CSV')!.trigger('click')
    expect(api.descargar).toHaveBeenCalledTimes(1)
    const [nombre, contenido] = api.descargar.mock.calls[0] as [string, string]
    expect(nombre).toBe('pedidos-por-sucursal_2026-10-01_2026-10-31.csv')
    expect(contenido.startsWith('﻿Sucursal;Pedidos;')).toBe(true)
    expect(contenido).toContain('Centro;3;1;2;Pan;40;UND')
  })
})

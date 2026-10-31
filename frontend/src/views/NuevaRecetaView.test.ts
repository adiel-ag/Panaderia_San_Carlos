import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import PrimeVue from 'primevue/config'
import Select from 'primevue/select'
import InputNumber from 'primevue/inputnumber'
import NuevaRecetaView from './NuevaRecetaView.vue'
import { ApiError } from '../api/cliente'

const api = vi.hoisted(() => ({
  listarRecetas: vi.fn(), obtenerReceta: vi.fn(), listarVersiones: vi.fn(), crearReceta: vi.fn(), listarProductos: vi.fn(),
  replace: vi.fn(), push: vi.fn(), toast: vi.fn(), confirmar: vi.fn(), salida: { guardia: null as null | (() => unknown) },
}))
const route = reactive({ params: { idProducto: '9' }, query: {} as Record<string, string> })
vi.mock('../api/recetas', async (original) => ({ ...(await original<typeof import('../api/recetas')>()),
  listarRecetas: api.listarRecetas, obtenerReceta: api.obtenerReceta, listarVersiones: api.listarVersiones, crearReceta: api.crearReceta }))
vi.mock('../api/pedidos', async (original) => ({ ...(await original<typeof import('../api/pedidos')>()), listarProductos: api.listarProductos }))
vi.mock('vue-router', async (original) => ({
  ...(await original<typeof import('vue-router')>()), useRoute: () => route, useRouter: () => ({ replace: api.replace, push: api.push }),
  onBeforeRouteLeave: (guardia: () => unknown) => { api.salida.guardia = guardia },
}))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: api.toast }) }))
// El diálogo de confirmación acepta solo.
vi.mock('primevue/useconfirm', () => ({ useConfirm: () => ({ require: (opciones: { accept: () => void }) => { api.confirmar(opciones); opciones.accept() } }) }))

function resumen(recetasVigentes: number) {
  const vigente = recetasVigentes === 1
  return { idProducto: 9, nombreProducto: 'Pan francés', unidad: 'UND', idReceta: vigente ? 21 : null, version: vigente ? 2 : null,
    rendimiento: vigente ? 100 : null, cantidadIngredientes: vigente ? 1 : 0, fechaInicio: vigente ? '2026-10-01' : null, recetasVigentes }
}
const receta = {
  idReceta: 21, idProducto: 9, nombreProducto: 'Pan francés', unidad: 'UND', version: 2, rendimiento: 100, estado: 'ACTIVA',
  fechaInicio: '2026-10-01', fechaFin: null, vigente: true, ingredientes: [{ idInsumo: 4, nombre: 'Harina', unidad: 'KG', cantidad: 25 }],
}
const versiones = [
  { idReceta: 21, version: 2, rendimiento: 100, estado: 'ACTIVA', fechaInicio: '2026-10-01', fechaFin: null, vigente: true, cantidadIngredientes: 1 },
  { idReceta: 20, version: 1, rendimiento: 80, estado: 'INACTIVA', fechaInicio: '2026-09-01', fechaFin: '2026-10-01', vigente: false, cantidadIngredientes: 1 },
]
function producto(idProducto: number, nombre: string, tipo = 'INSUMO', unidad = 'KG', estado = 'ACTIVO') {
  return { idProducto, nombre, nombreTipoProducto: tipo, abreviaturaUnidadMedida: unidad, nombreUnidadMedida: unidad, estado }
}
const productos = [
  producto(9, 'Pan francés', 'PRODUCTO_FINAL', 'UND'), producto(7, 'Sal'), producto(4, 'Harina'), producto(5, 'Levadura'),
  producto(6, 'Azúcar vieja', 'INSUMO', 'KG', 'INACTIVO'), producto(8, 'Agua', 'INSUMO', 'L'),
]
const montajes: VueWrapper[] = []
async function montar() {
  const wrapper = mount(NuevaRecetaView, { global: { plugins: [PrimeVue], stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
  montajes.push(wrapper)
  await flushPromises()
  return wrapper
}
function selects(wrapper: VueWrapper) { return wrapper.findAllComponents(Select) }
function numeros(wrapper: VueWrapper) { return wrapper.findAllComponents(InputNumber) }
async function elegir(wrapper: VueWrapper, indice: number, valor: number) {
  selects(wrapper)[indice]!.vm.$emit('update:modelValue', valor)
  await nextTick()
}
async function escribir(wrapper: VueWrapper, indice: number, valor: number | null) {
  numeros(wrapper)[indice]!.vm.$emit('update:modelValue', valor)
  await nextTick()
}
function boton(wrapper: VueWrapper, texto: string) { return wrapper.findAll('button').find((item) => item.text() === texto) }
async function guardar(wrapper: VueWrapper) {
  await wrapper.find('form').trigger('submit')
  await flushPromises()
}
afterEach(() => { montajes.splice(0).forEach((wrapper) => wrapper.unmount()) })
beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn(), addListener: vi.fn(), removeListener: vi.fn() }))
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })
  route.params.idProducto = '9'
  api.salida.guardia = null
  api.listarRecetas.mockResolvedValue([resumen(1)])
  api.obtenerReceta.mockResolvedValue(receta)
  api.listarVersiones.mockResolvedValue(versiones)
  api.listarProductos.mockResolvedValue(productos)
  api.crearReceta.mockResolvedValue({ ...receta, idReceta: 22, version: 3 })
  api.replace.mockResolvedValue(undefined)
})

describe('NuevaRecetaView', () => {
  it('parte de la versión vigente y explica qué pasa al guardar', async () => {
    const wrapper = await montar()
    expect(api.obtenerReceta).toHaveBeenCalledWith(21)
    expect(wrapper.find('h1').text()).toBe('Nueva versión · Pan francés')
    expect(wrapper.text()).toContain('Guardar crea la versión 3 y reemplaza la actual. Las producciones anteriores conservan la receta con la que se hicieron.')
    expect(numeros(wrapper)[0]!.props('modelValue')).toBe(100)
    expect(numeros(wrapper)[0]!.props('suffix')).toBe(' u por tanda')
    expect(selects(wrapper)[0]!.props('modelValue')).toBe(4)
    expect(numeros(wrapper)[1]!.props('modelValue')).toBe(25)
    expect(numeros(wrapper)[1]!.props('suffix')).toBe(' kg')
    expect(wrapper.find('[data-consumo="1"]').text()).toBe('0.25 kg')
  })

  it('ofrece solo insumos activos y quita los elegidos en otras líneas', async () => {
    const wrapper = await montar()
    expect((selects(wrapper)[0]!.props('options') as typeof productos).map((item) => item.nombre)).toEqual(['Agua', 'Harina', 'Levadura', 'Sal'])
    await boton(wrapper, 'Agregar ingrediente')!.trigger('click')
    await nextTick()
    expect((selects(wrapper)[1]!.props('options') as typeof productos).map((item) => item.idProducto)).toEqual([8, 5, 7])
    await elegir(wrapper, 1, 7)
    expect((selects(wrapper)[0]!.props('options') as typeof productos).map((item) => item.idProducto)).toEqual([8, 4, 5])
    await wrapper.find('button[aria-label="Quitar Sal"]').trigger('click')
    expect(selects(wrapper)).toHaveLength(1)
  })

  it('envía la receta con la versión de partida y navega al detalle', async () => {
    const wrapper = await montar()
    await escribir(wrapper, 0, 50)
    await boton(wrapper, 'Agregar ingrediente')!.trigger('click')
    await nextTick()
    await elegir(wrapper, 1, 7)
    await escribir(wrapper, 2, 0.5)
    expect(wrapper.find('[data-consumo="2"]').text()).toBe('0.01 kg')
    await guardar(wrapper)
    expect(api.crearReceta).toHaveBeenCalledWith({
      idProducto: 9, rendimiento: 50, versionBase: 2, ingredientes: [{ idInsumo: 4, cantidad: 25 }, { idInsumo: 7, cantidad: 0.5 }],
    })
    expect(api.toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', summary: 'Receta de Pan francés guardada (versión 3)' }))
    expect(api.replace).toHaveBeenCalledWith({ name: 'receta', params: { idProducto: 9 } })
    // Ya guardado, salir no pide confirmación.
    expect(await api.salida.guardia!()).toBe(true)
    expect(api.confirmar).not.toHaveBeenCalled()
  })

  it('crea la primera receta sin versión de partida', async () => {
    api.listarRecetas.mockResolvedValue([resumen(0)])
    api.listarVersiones.mockResolvedValue([])
    const wrapper = await montar()
    expect(api.obtenerReceta).not.toHaveBeenCalled()
    expect(wrapper.find('h1').text()).toBe('Receta de Pan francés')
    expect(wrapper.text()).toContain('Guardar crea la versión 1')
    await escribir(wrapper, 0, 10)
    await elegir(wrapper, 0, 4)
    await escribir(wrapper, 1, 2)
    await guardar(wrapper)
    expect(api.crearReceta).toHaveBeenCalledWith({ idProducto: 9, rendimiento: 10, versionBase: null, ingredientes: [{ idInsumo: 4, cantidad: 2 }] })
  })

  it('usa la versión más alta cuando hay recetas duplicadas', async () => {
    api.listarRecetas.mockResolvedValue([resumen(2)])
    const wrapper = await montar()
    expect(wrapper.find('h1').text()).toBe('Nueva versión · Pan francés')
    await escribir(wrapper, 0, 10)
    await elegir(wrapper, 0, 4)
    await escribir(wrapper, 1, 2)
    await guardar(wrapper)
    expect(api.crearReceta).toHaveBeenCalledWith(expect.objectContaining({ versionBase: 2 }))
  })

  it('valida antes de enviar', async () => {
    api.listarRecetas.mockResolvedValue([resumen(0)])
    api.listarVersiones.mockResolvedValue([])
    const wrapper = await montar()
    await guardar(wrapper)
    expect(api.crearReceta).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Ingrese un rendimiento mayor que 0')
    expect(wrapper.text()).toContain('Elija un insumo.')
  })

  it('muestra el error 400 y conserva los datos', async () => {
    api.crearReceta.mockRejectedValue(new ApiError(400, 'DATOS_INVALIDOS', 'El ingrediente #4 no existe'))
    const wrapper = await montar()
    await escribir(wrapper, 1, 30)
    await guardar(wrapper)
    expect(wrapper.text()).toContain('El ingrediente #4 no existe')
    expect(numeros(wrapper)[1]!.props('modelValue')).toBe(30)
    expect(api.replace).not.toHaveBeenCalled()
  })

  it('ante un cambio concurrente conserva los datos y enlaza la versión actual', async () => {
    const mensaje = 'La receta cambió mientras la editaba (ahora es la versión 3). Revise la versión actual y vuelva a intentar.'
    api.crearReceta.mockRejectedValue(new ApiError(409, 'CONFLICTO', mensaje))
    const wrapper = await montar()
    await escribir(wrapper, 1, 30)
    await guardar(wrapper)
    const aviso = wrapper.find('.conflicto-receta')
    expect(aviso.text()).toContain(mensaje)
    expect(aviso.text()).toContain('Ver la versión actual')
    expect(numeros(wrapper)[1]!.props('modelValue')).toBe(30)
    expect(api.replace).not.toHaveBeenCalled()
  })

  it('pide confirmar antes de salir con cambios', async () => {
    const wrapper = await montar()
    expect(await api.salida.guardia!()).toBe(true)
    expect(api.confirmar).not.toHaveBeenCalled()
    await escribir(wrapper, 0, 120)
    expect(await api.salida.guardia!()).toBe(true)
    expect(api.confirmar).toHaveBeenCalledWith(expect.objectContaining({ header: '¿Descartar los cambios?' }))
  })

  it('avisa si el producto no existe', async () => {
    route.params.idProducto = '99'
    api.listarVersiones.mockRejectedValue(new ApiError(404, 'NO_ENCONTRADO', 'No existe'))
    const wrapper = await montar()
    expect(wrapper.text()).toContain('No se encontró el producto.')
    expect(wrapper.find('form').exists()).toBe(false)
  })
})

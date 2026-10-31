import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import PrimeVue from 'primevue/config'
import IrPedido from './IrPedido.vue'

const { push } = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ fullPath: '/despacho?vista=listos' }),
  useRouter: () => ({ push }),
}))

beforeEach(() => { vi.clearAllMocks() })

describe('IrPedido', () => {
  it.each(['', '0', '-2', '1.5', 'abc', '9007199254740993'])('rechaza el número %s', async (valor) => {
    const wrapper = mount(IrPedido, { global: { plugins: [PrimeVue] } })
    await wrapper.find('input').setValue(valor)
    await wrapper.find('form').trigger('submit')
    expect(wrapper.text()).toContain('Escriba un número de pedido válido')
    expect(push).not.toHaveBeenCalled()
  })

  it('abre el pedido y conserva la ruta de regreso', async () => {
    const wrapper = mount(IrPedido, { global: { plugins: [PrimeVue] } })
    await wrapper.find('input').setValue('#27')
    await wrapper.find('form').trigger('submit')
    expect(push).toHaveBeenCalledWith({ name: 'pedido', params: { id: 27 }, query: { volver: '/despacho?vista=listos' } })
  })
})

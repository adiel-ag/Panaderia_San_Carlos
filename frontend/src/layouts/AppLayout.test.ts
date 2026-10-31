import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import PrimeVue from 'primevue/config'
import { nextTick } from 'vue'
import AppLayout from './AppLayout.vue'

const { sesion, router, toast, ruta } = vi.hoisted(() => ({
  sesion: {
    rol: 'GERENTE',
    usuario: { idUsuario: 1, username: 'ana', nombre: 'Ana', rol: 'GERENTE', idSucursal: null },
    cerrarSesion: vi.fn(),
  },
  router: { replace: vi.fn() },
  toast: { add: vi.fn() },
  ruta: { name: 'pedido' },
}))

vi.mock('../stores/sesion', () => ({ useSesionStore: () => sesion }))
vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRouter: () => router,
  useRoute: () => ruta,
}))
vi.mock('primevue/usetoast', () => ({ useToast: () => toast }))

describe('AppLayout', () => {
  it('resalta Pedidos en la ruta de detalle', () => {
    const wrapper = mount(AppLayout, {
      global: { plugins: [PrimeVue], stubs: { RouterLink: true, RouterView: true, Drawer: true } },
    })
    expect(wrapper.find('[data-modulo="pedidos"]').classes()).toContain('router-link-active')
  })

  it('resalta Producción en su detalle', () => {
    ruta.name = 'produccion-detalle'
    const wrapper = mount(AppLayout, {
      global: { plugins: [PrimeVue], stubs: { RouterLink: true, RouterView: true, Drawer: true } },
    })
    expect(wrapper.find('[data-modulo="produccion"]').classes()).toContain('router-link-active')
    expect(wrapper.find('[data-modulo="pedidos"]').classes()).not.toContain('router-link-active')
    ruta.name = 'pedido'
  })

  it('navega al login y avisa si el servidor rechaza el cierre', async () => {
    sesion.cerrarSesion.mockRejectedValue(new Error('red'))
    const wrapper = mount(AppLayout, {
      global: {
        plugins: [PrimeVue],
        stubs: { RouterLink: true, RouterView: true, Drawer: true },
      },
    })
    await wrapper.find('#cerrar-sesion').trigger('click')
    await Promise.resolve()
    await nextTick()
    expect(router.replace).toHaveBeenCalledWith({ name: 'login' })
    expect(toast.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'warn',
      detail: 'No se pudo confirmar el cierre de sesión con el servidor. Se reintentará automáticamente.',
    }))
  })
})

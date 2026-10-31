import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import PrimeVue from 'primevue/config'
import { nextTick } from 'vue'
import { ApiError } from '../api/cliente'
import LoginView from './LoginView.vue'

const { sesion, router, route } = vi.hoisted(() => ({
  sesion: { iniciarSesion: vi.fn() },
  router: { replace: vi.fn() },
  route: { query: {} as Record<string, string> },
}))

vi.mock('../stores/sesion', () => ({ useSesionStore: () => sesion }))
vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRouter: () => router,
  useRoute: () => route,
}))

async function completar(wrapper: ReturnType<typeof mount>) {
  await wrapper.find('#usuario').setValue('ana')
  await wrapper.find('#contrasena').setValue('clave12345')
  await wrapper.find('form').trigger('submit')
  await Promise.resolve()
  await nextTick()
}

describe('LoginView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    route.query = {}
  })

  it('redirige al destino seguro tras iniciar sesión', async () => {
    route.query = { redirect: '/pedidos' }
    sesion.iniciarSesion.mockResolvedValue({})
    const wrapper = mount(LoginView, { global: { plugins: [PrimeVue] } })
    await completar(wrapper)
    expect(sesion.iniciarSesion).toHaveBeenCalledWith('ana', 'clave12345')
    expect(router.replace).toHaveBeenCalledWith('/pedidos')
  })

  it('descarta un destino externo tras iniciar sesión', async () => {
    route.query = { redirect: '//evil.com' }
    sesion.iniciarSesion.mockResolvedValue({})
    const wrapper = mount(LoginView, { global: { plugins: [PrimeVue] } })
    await completar(wrapper)
    expect(router.replace).toHaveBeenCalledWith('/')
  })

  it('muestra el mensaje del servidor y limpia la contraseña al fallar con 401', async () => {
    sesion.iniciarSesion.mockRejectedValue(new ApiError(401, 'CREDENCIALES', 'La cuenta está bloqueada.'))
    const wrapper = mount(LoginView, { global: { plugins: [PrimeVue] } })
    await completar(wrapper)
    expect(wrapper.text()).toContain('La cuenta está bloqueada.')
    expect((wrapper.find('#contrasena').element as HTMLInputElement).value).toBe('')
  })

  it('muestra el aviso de conexión de la ruta', () => {
    route.query = { aviso: 'sin-conexion' }
    const wrapper = mount(LoginView, { global: { plugins: [PrimeVue] } })
    expect(wrapper.text()).toContain('No se pudo comprobar la sesión. Verifique la conexión e intente de nuevo.')
  })

  it('muestra un mensaje claro para un error de red', async () => {
    sesion.iniciarSesion.mockRejectedValue(new ApiError(0, 'SIN_CONEXION', 'Error técnico'))
    const wrapper = mount(LoginView, { global: { plugins: [PrimeVue] } })
    await completar(wrapper)
    expect(wrapper.text()).toContain('No se pudo conectar con el servidor.')
  })
})

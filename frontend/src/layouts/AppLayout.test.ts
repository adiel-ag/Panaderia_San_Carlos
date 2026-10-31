import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import PrimeVue from 'primevue/config'
import { nextTick } from 'vue'
import AppLayout from './AppLayout.vue'
import { useTema } from '../tema/useTema'

const { sesion, router, toast, ruta } = vi.hoisted(() => ({
  sesion: {
    rol: 'GERENTE',
    usuario: { idUsuario: 1, username: 'ana', nombre: 'Ana', rol: 'GERENTE', idSucursal: null },
    cerrarSesion: vi.fn(),
  },
  router: { replace: vi.fn(), push: vi.fn() },
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
  it('muestra la identificación institucional en un pie fuera del contenido', () => {
    const wrapper = mount(AppLayout, {
      global: { plugins: [PrimeVue], stubs: { RouterLink: true, RouterView: true, Drawer: true } },
    })
    expect(wrapper.find('footer').text()).toContain('Universidad Mariano Gálvez de Guatemala')
  })
  it('permite saltar el menú y llegar al contenido principal', () => {
    const wrapper = mount(AppLayout, {
      global: { plugins: [PrimeVue], stubs: { RouterLink: true, RouterView: true, Drawer: true } },
    })
    expect(wrapper.find('a.enlace-saltar').attributes('href')).toBe('#contenido-principal')
    expect(wrapper.find('main#contenido-principal').exists()).toBe(true)
  })
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

  it.each(['receta', 'receta-nueva'])('resalta Recetas en %s', (nombre) => {
    ruta.name = nombre
    const wrapper = mount(AppLayout, {
      global: { plugins: [PrimeVue], stubs: { RouterLink: true, RouterView: true, Drawer: true } },
    })
    expect(wrapper.find('[data-modulo="recetas"]').classes()).toContain('router-link-active')
    expect(wrapper.find('[data-modulo="produccion"]').classes()).not.toContain('router-link-active')
    ruta.name = 'pedido'
  })

  it('resalta Despacho en el detalle abierto desde Despacho', () => {
    sesion.rol = 'ADMINISTRADOR'
    Object.assign(ruta, { query: { volver: '/despacho?vista=despachados' } })
    const wrapper = mount(AppLayout, {
      global: { plugins: [PrimeVue], stubs: { RouterLink: true, RouterView: true, Drawer: true } },
    })
    expect(wrapper.find('[data-modulo="despacho"]').classes()).toContain('router-link-active')
    expect(wrapper.find('[data-modulo="pedidos"]').classes()).not.toContain('router-link-active')
    Object.assign(ruta, { query: {} })
    sesion.rol = 'GERENTE'
  })

  it.each([['hoja-despacho', 'pedidos', {}], ['hoja-despacho', 'despacho', { volver: '/despacho' }], ['hoja-produccion', 'produccion', {}]])(
    'resalta el módulo en la hoja de impresión %s', (nombre, modulo, query) => {
      sesion.rol = 'ADMINISTRADOR'
      Object.assign(ruta, { name: nombre, query })
      const wrapper = mount(AppLayout, {
        global: { plugins: [PrimeVue], stubs: { RouterLink: true, RouterView: true, Drawer: true } },
      })
      expect(wrapper.find(`[data-modulo="${modulo}"]`).classes()).toContain('router-link-active')
      Object.assign(ruta, { name: 'pedido', query: {} })
      sesion.rol = 'GERENTE'
    })

  it('agrupa el menú lateral con encabezados solo para los grupos visibles', () => {
    const wrapper = mount(AppLayout, {
      global: { plugins: [PrimeVue], stubs: { RouterLink: true, RouterView: true, Drawer: true } },
    })
    const grupos = wrapper.findAll('.navegacion-lateral .grupo-menu')
    expect(grupos.map((grupo) => grupo.find('.grupo-menu-titulo').text())).toEqual(['Operación', 'Catálogos', 'Administración'])
    expect(grupos[1]!.findAll('[data-modulo]').map((enlace) => enlace.attributes('data-modulo'))).toEqual(['recetas', 'productos'])
    sesion.rol = 'SUCURSAL'
    const sucursal = mount(AppLayout, {
      global: { plugins: [PrimeVue], stubs: { RouterLink: true, RouterView: true, Drawer: true } },
    })
    expect(sucursal.findAll('.navegacion-lateral .grupo-menu-titulo').map((titulo) => titulo.text())).toEqual(['Operación'])
    sesion.rol = 'GERENTE'
  })

  describe('menú de la cuenta', () => {
    afterEach(() => { document.body.innerHTML = '' })

    async function abrirMenu() {
      const wrapper = mount(AppLayout, {
        attachTo: document.body,
        // Transición real: el menú avisa que se abrió al terminar de entrar.
        global: { plugins: [PrimeVue], stubs: { RouterLink: true, RouterView: true, Drawer: true, transition: false } },
      })
      const boton = wrapper.get('#boton-usuario')
      expect(boton.text()).toContain('Ana')
      expect(boton.find('.avatar-usuario').text()).toBe('A')
      expect(boton.attributes('aria-haspopup')).toBe('menu')
      await boton.trigger('click')
      await nextTick()
      return wrapper
    }
    function opcion(id: string): HTMLElement {
      const elemento = document.getElementById(id)
      if (!elemento) throw new Error(`No existe #${id}`)
      return elemento
    }

    it('muestra el rol como encabezado y no deja controles de cuenta en la barra', async () => {
      const wrapper = await abrirMenu()
      expect(wrapper.find('.barra-usuario').text()).not.toContain('Cerrar sesión')
      expect(wrapper.get('#boton-usuario').attributes('aria-expanded')).toBe('true')
      const menu = document.getElementById('menu-usuario')
      expect(menu?.querySelector('.menu-usuario-cabecera')?.textContent).toContain('Gerente')
      expect(Array.from(menu?.querySelectorAll('[role="menuitem"]') ?? []).map((item) => item.textContent?.trim()))
        .toEqual(['Cambiar contraseña', 'Modo oscuro', 'Cerrar sesión'])
      wrapper.unmount()
    })

    it('abre Cambiar contraseña', async () => {
      const wrapper = await abrirMenu()
      opcion('cambiar-clave').click()
      await nextTick()
      expect(router.push).toHaveBeenCalledWith({ name: 'mi-clave' })
      wrapper.unmount()
    })

    it('alterna el modo oscuro', async () => {
      const tema = useTema()
      tema.oscuro.value = false
      const wrapper = await abrirMenu()
      opcion('alternar-tema').click()
      await nextTick()
      expect(tema.oscuro.value).toBe(true)
      await wrapper.get('#boton-usuario').trigger('click')
      await nextTick()
      expect(opcion('alternar-tema').textContent).toContain('Modo claro')
      tema.alternar()
      wrapper.unmount()
    })

    it('cierra la sesión y vuelve al login', async () => {
      sesion.cerrarSesion.mockResolvedValue(undefined)
      router.replace.mockClear()
      const wrapper = await abrirMenu()
      opcion('cerrar-sesion').click()
      await Promise.resolve()
      await nextTick()
      expect(sesion.cerrarSesion).toHaveBeenCalled()
      expect(router.replace).toHaveBeenCalledWith({ name: 'login' })
      wrapper.unmount()
    })

    it('navega al login y avisa si el servidor rechaza el cierre', async () => {
      sesion.cerrarSesion.mockRejectedValue(new Error('red'))
      const wrapper = await abrirMenu()
      opcion('cerrar-sesion').click()
      await Promise.resolve()
      await nextTick()
      expect(router.replace).toHaveBeenCalledWith({ name: 'login' })
      expect(toast.add).toHaveBeenCalledWith(expect.objectContaining({
        severity: 'warn',
        detail: 'No se pudo confirmar el cierre de sesión con el servidor. Se reintentará automáticamente.',
      }))
      wrapper.unmount()
    })
  })
})

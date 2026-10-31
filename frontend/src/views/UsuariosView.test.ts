import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { reactive } from 'vue'
import PrimeVue from 'primevue/config'
import SelectButton from 'primevue/selectbutton'
import Select from 'primevue/select'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import UsuariosView from './UsuariosView.vue'
import { ApiError } from '../api/cliente'

const api = vi.hoisted(() => ({
  listarUsuarios: vi.fn(), crearUsuario: vi.fn(), actualizarUsuario: vi.fn(), cambiarEstadoUsuario: vi.fn(), desbloquearUsuario: vi.fn(),
  asignarClave: vi.fn(), listarRolesCatalogo: vi.fn(), listarSucursales: vi.fn(), replace: vi.fn(), toast: vi.fn(), confirmar: vi.fn(),
  sesion: { rol: 'ADMINISTRADOR', usuario: { idUsuario: 1 } },
}))
const route = reactive({ query: {} as Record<string, string>, fullPath: '/usuarios' })
vi.mock('../api/usuarios', async (original) => ({ ...(await original<typeof import('../api/usuarios')>()),
  listarUsuarios: api.listarUsuarios, crearUsuario: api.crearUsuario, actualizarUsuario: api.actualizarUsuario,
  cambiarEstadoUsuario: api.cambiarEstadoUsuario, desbloquearUsuario: api.desbloquearUsuario, asignarClave: api.asignarClave,
  listarRolesCatalogo: api.listarRolesCatalogo }))
vi.mock('../api/pedidos', async (original) => ({ ...(await original<typeof import('../api/pedidos')>()), listarSucursales: api.listarSucursales }))
vi.mock('../stores/sesion', () => ({ useSesionStore: () => api.sesion }))
vi.mock('vue-router', async (original) => ({
  ...(await original<typeof import('vue-router')>()), useRoute: () => route, useRouter: () => ({ replace: api.replace }),
}))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: api.toast }) }))
// La confirmación se guarda para aceptarla desde la prueba.
vi.mock('primevue/useconfirm', () => ({ useConfirm: () => ({ require: api.confirmar }) }))

type Fila = Record<string, unknown>
function usuario(idUsuario: number, nombre: string, username: string, extra: Fila = {}): Fila {
  return { idUsuario, nombre, username, rol: 'GERENTE', idSucursal: null, nombreSucursal: null, estado: 'ACTIVO',
    fechaCreacion: '2026-10-01T08:00:00', ultimoAcceso: null, bloqueado: false, tieneClave: true, ...extra }
}
const yo = usuario(1, 'Admin General', 'admin', { rol: 'ADMINISTRADOR', ultimoAcceso: '2026-10-30T14:05:00' })
const ana = usuario(2, 'Ana López', 'ana.lopez', { rol: 'SUCURSAL', idSucursal: 2, nombreSucursal: 'Centro', bloqueado: true })
const luis = usuario(3, 'Luis Pérez', 'lperez', { tieneClave: false })
const marta = usuario(4, 'Marta Ruiz', 'mruiz', { estado: 'INACTIVO', rol: 'BODEGA' })
const vacia = { items: [], total: 0, pagina: 1, tamano: 20 }
// tamano 100 es el pedido del resumen.
function responder(lista: unknown = vacia, resumen: unknown = vacia) {
  api.listarUsuarios.mockImplementation((_filtros: unknown, _pagina: number, tamano: number) => Promise.resolve(tamano === 100 ? resumen : lista))
}
function llamadasLista() { return api.listarUsuarios.mock.calls.filter((llamada) => llamada[2] === 20) }
const montajes: VueWrapper[] = []
async function montar() {
  const wrapper = mount(UsuariosView, { attachTo: document.body, global: { plugins: [PrimeVue], stubs: {
    Dialog: { props: ['visible', 'header'], template: '<div v-if="visible" class="dialogo-stub"><h2>{{ header }}</h2><slot /></div>' },
    Menu: { props: ['model'], methods: { toggle() {} }, template: '<div class="menu-stub"><button v-for="item in model" :key="item.label" type="button" class="opcion-menu" :disabled="item.disabled" @click="item.command()">{{ item.label }}</button></div>' },
  } } })
  montajes.push(wrapper)
  await flushPromises()
  return wrapper
}
function boton(wrapper: VueWrapper, texto: string) {
  return wrapper.findAll('button').find((item) => item.text() === texto)
}
function select(wrapper: VueWrapper, id: string) {
  return wrapper.findAllComponents(Select).find((item) => item.props('inputId') === id)!
}
function clave(wrapper: VueWrapper, id: string, valor: string) {
  wrapper.findAllComponents(Password).find((item) => item.props('inputId') === id)!.vm.$emit('update:modelValue', valor)
}
async function opcionesMenu(wrapper: VueWrapper, nombre: string) {
  await wrapper.find(`[aria-label="Más acciones para ${nombre}"]`).trigger('click')
  return wrapper.findAll('.opcion-menu').map((item) => item.text())
}
async function elegir(wrapper: VueWrapper, nombre: string, opcion: string) {
  await opcionesMenu(wrapper, nombre)
  await wrapper.findAll('.opcion-menu').find((item) => item.text() === opcion)!.trigger('click')
  await flushPromises()
}
async function enviar(wrapper: VueWrapper) {
  await wrapper.find('.dialogo-stub form').trigger('submit')
  await flushPromises()
}
afterEach(() => {
  montajes.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.useRealTimers()
})
beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn(), addListener: vi.fn(), removeListener: vi.fn() }))
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })
  route.query = {}
  api.sesion.usuario = { idUsuario: 1 }
  responder()
  api.listarSucursales.mockResolvedValue([{ idSucursal: 2, nombre: 'Centro' }, { idSucursal: 5, nombre: 'Norte' }])
  api.listarRolesCatalogo.mockResolvedValue([{ idRol: 3, nombre: 'SUCURSAL', descripcion: 'Registra los pedidos de su sucursal' }])
  api.crearUsuario.mockImplementation((cuerpo: Fila) => Promise.resolve(usuario(9, cuerpo.nombre as string, cuerpo.username as string, cuerpo)))
  api.actualizarUsuario.mockImplementation((id: number, cuerpo: Fila) => Promise.resolve(usuario(id, cuerpo.nombre as string, 'x', cuerpo)))
  api.cambiarEstadoUsuario.mockImplementation((id: number, estado: string) => Promise.resolve(usuario(id, 'Cambiado', 'x', { estado })))
  api.desbloquearUsuario.mockImplementation((id: number) => Promise.resolve(usuario(id, 'Ana López', 'ana.lopez', { bloqueado: false })))
  api.asignarClave.mockResolvedValue({ mensaje: 'Contraseña asignada' })
})

describe('UsuariosView', () => {
  it('consulta los activos por defecto y muestra el resumen y las filas', async () => {
    responder({ ...vacia, total: 3, items: [yo, ana, luis] }, { ...vacia, total: 3, items: [yo, ana, luis] })
    const wrapper = await montar()
    expect(api.listarUsuarios).toHaveBeenCalledWith({ rol: undefined, estado: 'ACTIVO', idSucursal: undefined }, 1, 20)
    expect(api.listarUsuarios).toHaveBeenCalledWith({ estado: 'ACTIVO' }, 1, 100, false)
    expect(wrapper.find('h1').text()).toBe('Usuarios')
    expect(wrapper.find('.resumen-usuarios').text()).toBe('3 usuarios activos · 1 bloqueado')
    expect(boton(wrapper, 'Nuevo usuario')!.classes()).not.toContain('p-button-outlined')
    const filas = wrapper.findAll('tbody tr').map((fila) => fila.findAll('td').slice(0, 5).map((celda) => celda.text()))
    expect(filas[0]![0]).toBe('Admin General (usted)admin')
    expect(filas[0]![1]).toBe('Administrador')
    expect(filas[0]![2]).toBe('—')
    expect(filas[0]![4]).not.toBe('Nunca')
    expect(filas[1]).toEqual(['Ana Lópezana.lopez', 'Sucursal', 'Centro', 'ActivoBloqueado', 'Nunca'])
    expect(filas[2]![3]).toBe('ActivoSin contraseña')
    expect(wrapper.find('.estado-usuario--bloqueado').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('Limpiar filtros')
  })

  it('oculta el resumen si falla', async () => {
    api.listarUsuarios.mockImplementation((_f: unknown, _p: number, tamano: number) => tamano === 100 ? Promise.reject(new Error('red')) : Promise.resolve(vacia))
    const wrapper = await montar()
    expect(wrapper.find('.resumen-usuarios').exists()).toBe(false)
  })

  it('lee los filtros de la URL y los cambia con replace volviendo a la página 1', async () => {
    route.query = { rol: 'GERENTE', estado: 'INACTIVO', sucursal: '2', pagina: '2' }
    responder({ ...vacia, total: 30, items: [marta] })
    const wrapper = await montar()
    expect(api.listarUsuarios).toHaveBeenCalledWith({ rol: 'GERENTE', estado: 'INACTIVO', idSucursal: 2 }, 2, 20)
    expect(wrapper.text()).toContain('Limpiar filtros')
    select(wrapper, 'rol-filtro').vm.$emit('update:modelValue', 'BODEGA')
    expect(api.replace).toHaveBeenLastCalledWith({ name: 'usuarios', query: { rol: 'BODEGA', estado: 'INACTIVO', sucursal: '2' } })
    wrapper.findComponent(SelectButton).vm.$emit('update:modelValue', 'TODOS')
    expect(api.replace).toHaveBeenLastCalledWith({ name: 'usuarios', query: { rol: 'GERENTE', estado: 'TODOS', sucursal: '2' } })
    select(wrapper, 'sucursal-filtro').vm.$emit('update:modelValue', null)
    expect(api.replace).toHaveBeenLastCalledWith({ name: 'usuarios', query: { rol: 'GERENTE', estado: 'INACTIVO' } })
    wrapper.findComponent(SelectButton).vm.$emit('update:modelValue', 'ACTIVO')
    expect(api.replace).toHaveBeenLastCalledWith({ name: 'usuarios', query: { rol: 'GERENTE', sucursal: '2' } })
    route.query = { estado: 'TODOS' }
    await flushPromises()
    expect(api.listarUsuarios).toHaveBeenLastCalledWith({ rol: undefined, estado: undefined, idSucursal: undefined }, 1, 20)
  })

  it('busca en la página cargada sin volver a consultar', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    responder({ ...vacia, total: 3, items: [yo, ana, luis] })
    const wrapper = await montar()
    wrapper.findComponent(InputText).vm.$emit('update:modelValue', ' PEREZ ')
    vi.advanceTimersByTime(299)
    expect(api.replace).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(api.replace).toHaveBeenCalledWith({ name: 'usuarios', query: { q: 'PEREZ' } })
    route.query = { q: 'PEREZ' }
    await flushPromises()
    expect(llamadasLista()).toHaveLength(1)
    expect(wrapper.findAll('tbody tr')).toHaveLength(1)
    expect(wrapper.find('tbody').text()).toContain('Luis Pérez')
    route.query = { q: 'zz' }
    await flushPromises()
    expect(wrapper.text()).toContain('No hay usuarios con estos filtros.')
    await wrapper.find('[aria-label="Borrar búsqueda"]').trigger('click')
    expect(api.replace).toHaveBeenLastCalledWith({ name: 'usuarios', query: {} })
  })

  it('crea un usuario con el cuerpo normalizado y recarga en el lugar', async () => {
    responder({ ...vacia, total: 1, items: [yo] }, { ...vacia, total: 1, items: [yo] })
    const wrapper = await montar()
    await boton(wrapper, 'Nuevo usuario')!.trigger('click')
    const dialogo = wrapper.find('.dialogo-stub')
    expect(dialogo.find('h2').text()).toBe('Nuevo usuario')
    expect(dialogo.text()).toContain('Use minúsculas, números, punto o guion (3 a 50).')
    expect(dialogo.find('#usuario-clave').attributes('aria-describedby')).toBe('usuario-clave-ayuda')
    expect(dialogo.find('#usuario-sucursal').exists()).toBe(false)
    // Sin tocar nada no hay errores; al enviar aparecen y el foco va al primero.
    expect(dialogo.find('.error-campo').exists()).toBe(false)
    await enviar(wrapper)
    expect(api.crearUsuario).not.toHaveBeenCalled()
    expect(wrapper.find('.dialogo-stub').text()).toContain('Ingrese el nombre.')
    expect(document.activeElement).toBe(wrapper.find('#usuario-nombre').element)
    await wrapper.find('#usuario-nombre').setValue('  Rosa Gil ')
    await wrapper.find('#usuario-username').setValue(' Rosa.Gil ')
    select(wrapper, 'usuario-rol').vm.$emit('update:modelValue', 'SUCURSAL')
    await flushPromises()
    expect(wrapper.find('#usuario-sucursal').exists()).toBe(true)
    await enviar(wrapper)
    expect(wrapper.find('.dialogo-stub').text()).toContain('Elija la sucursal del usuario.')
    select(wrapper, 'usuario-sucursal').vm.$emit('update:modelValue', 5)
    clave(wrapper, 'usuario-clave', 'secreta123')
    clave(wrapper, 'usuario-confirmacion', 'otra12345')
    await enviar(wrapper)
    expect(wrapper.find('.dialogo-stub').text()).toContain('Las contraseñas no coinciden.')
    expect(document.activeElement).toBe(wrapper.find('#usuario-confirmacion').element)
    clave(wrapper, 'usuario-confirmacion', 'secreta123')
    await flushPromises()
    responder({ ...vacia, total: 2, items: [yo, usuario(9, 'Rosa Gil', 'rosa.gil')] }, { ...vacia, total: 2, items: [yo] })
    const formulario = wrapper.find('.dialogo-stub form')
    await formulario.trigger('submit')
    await formulario.trigger('submit')
    await flushPromises()
    expect(api.crearUsuario).toHaveBeenCalledTimes(1)
    expect(api.crearUsuario).toHaveBeenCalledWith({ nombre: 'Rosa Gil', username: 'rosa.gil', rol: 'SUCURSAL', idSucursal: 5, password: 'secreta123' })
    expect(api.toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', summary: 'Usuario rosa.gil creado' }))
    expect(wrapper.find('.dialogo-stub').exists()).toBe(false)
    expect(llamadasLista()).toHaveLength(2)
    expect(wrapper.find('tbody').text()).toContain('Rosa Gil')
    expect(wrapper.find('.resumen-usuarios').text()).toBe('2 usuarios activos')
  })

  it('la sucursal solo aparece para el rol Sucursal y se borra al cambiar de rol', async () => {
    const wrapper = await montar()
    await boton(wrapper, 'Nuevo usuario')!.trigger('click')
    select(wrapper, 'usuario-rol').vm.$emit('update:modelValue', 'SUCURSAL')
    await flushPromises()
    expect(wrapper.find('#usuario-sucursal').exists()).toBe(true)
    expect(select(wrapper, 'usuario-rol').props('options')).toContainEqual({ value: 'SUCURSAL', label: 'Sucursal', descripcion: 'Registra los pedidos de su sucursal' })
    expect(select(wrapper, 'usuario-rol').props('options')).toContainEqual({ value: 'PRODUCCION', label: 'Producción', descripcion: '' })
    select(wrapper, 'usuario-sucursal').vm.$emit('update:modelValue', 2)
    select(wrapper, 'usuario-rol').vm.$emit('update:modelValue', 'GERENTE')
    await flushPromises()
    expect(wrapper.find('#usuario-sucursal').exists()).toBe(false)
    await wrapper.find('#usuario-nombre').setValue('Rosa')
    await wrapper.find('#usuario-username').setValue('rosa')
    clave(wrapper, 'usuario-clave', 'secreta123')
    clave(wrapper, 'usuario-confirmacion', 'secreta123')
    await enviar(wrapper)
    expect(api.crearUsuario).toHaveBeenCalledWith(expect.objectContaining({ rol: 'GERENTE', idSucursal: null }))
  })

  it('con un 409 deja el diálogo abierto y con un 400 muestra el mensaje dentro', async () => {
    const wrapper = await montar()
    await boton(wrapper, 'Nuevo usuario')!.trigger('click')
    await wrapper.find('#usuario-nombre').setValue('Rosa')
    await wrapper.find('#usuario-username').setValue('rosa')
    select(wrapper, 'usuario-rol').vm.$emit('update:modelValue', 'BODEGA')
    clave(wrapper, 'usuario-clave', 'secreta123')
    clave(wrapper, 'usuario-confirmacion', 'secreta123')
    api.crearUsuario.mockRejectedValueOnce(new ApiError(409, 'CONFLICTO', 'El usuario ya existe'))
    await enviar(wrapper)
    const dialogo = wrapper.find('.dialogo-stub')
    expect(dialogo.exists()).toBe(true)
    expect(dialogo.text()).not.toContain('El usuario ya existe')
    expect((wrapper.find('#usuario-nombre').element as HTMLInputElement).value).toBe('Rosa')
    expect(api.toast).not.toHaveBeenCalled()
    expect(boton(wrapper, 'Crear usuario')!.attributes('disabled')).toBeUndefined()
    api.crearUsuario.mockRejectedValueOnce(new ApiError(400, 'SOLICITUD_INVALIDA', 'La sucursal no corresponde al rol'))
    await enviar(wrapper)
    expect(wrapper.find('.dialogo-stub').text()).toContain('La sucursal no corresponde al rol')
  })

  it('edita con el usuario de solo lectura y envía el cuerpo correcto', async () => {
    responder({ ...vacia, total: 2, items: [yo, ana] })
    const wrapper = await montar()
    await wrapper.find('[aria-label="Editar a Ana López"]').trigger('click')
    const dialogo = wrapper.find('.dialogo-stub')
    expect(dialogo.find('h2').text()).toBe('Editar usuario')
    expect(dialogo.find('input#usuario-username').exists()).toBe(false)
    expect(dialogo.find('#usuario-username').text()).toBe('ana.lopez')
    expect(dialogo.text()).not.toContain('Use minúsculas')
    expect(dialogo.find('#usuario-clave').exists()).toBe(false)
    expect((dialogo.find('#usuario-nombre').element as HTMLInputElement).value).toBe('Ana López')
    expect(dialogo.find('#usuario-sucursal').exists()).toBe(true)
    await wrapper.find('#usuario-nombre').setValue(' Ana María López ')
    await enviar(wrapper)
    expect(api.actualizarUsuario).toHaveBeenCalledWith(2, { nombre: 'Ana María López', rol: 'SUCURSAL', idSucursal: 2 })
    expect(api.toast).toHaveBeenCalledWith(expect.objectContaining({ summary: 'Cambios guardados' }))
    expect(wrapper.find('.dialogo-stub').exists()).toBe(false)
    expect(document.activeElement).toBe(wrapper.find('[data-editar="2"]').element)
  })

  it('al editar a otro rol quita la sucursal', async () => {
    responder({ ...vacia, total: 1, items: [ana] })
    const wrapper = await montar()
    await wrapper.find('[aria-label="Editar a Ana López"]').trigger('click')
    select(wrapper, 'usuario-rol').vm.$emit('update:modelValue', 'DESPACHO')
    await enviar(wrapper)
    expect(api.actualizarUsuario).toHaveBeenCalledWith(2, { nombre: 'Ana López', rol: 'DESPACHO', idSucursal: null })
  })

  it('asigna una contraseña validada', async () => {
    responder({ ...vacia, total: 1, items: [luis] })
    const wrapper = await montar()
    await elegir(wrapper, 'Luis Pérez', 'Asignar contraseña')
    const dialogo = wrapper.find('.dialogo-stub')
    expect(dialogo.find('h2').text()).toBe('Asignar contraseña')
    expect(dialogo.text()).toContain('lperez')
    expect(dialogo.find('#clave-nueva-usuario').attributes('aria-describedby')).toBe('clave-nueva-usuario-ayuda')
    clave(wrapper, 'clave-nueva-usuario', 'corta')
    await enviar(wrapper)
    expect(api.asignarClave).not.toHaveBeenCalled()
    expect(wrapper.find('.dialogo-stub').text()).toContain('La contraseña debe tener entre 8 y 128 caracteres.')
    expect(document.activeElement).toBe(wrapper.find('#clave-nueva-usuario').element)
    clave(wrapper, 'clave-nueva-usuario', 'nueva12345')
    clave(wrapper, 'clave-confirmar-usuario', 'nueva12345')
    await flushPromises()
    const formulario = wrapper.find('.dialogo-stub form')
    await formulario.trigger('submit')
    await formulario.trigger('submit')
    await flushPromises()
    expect(api.asignarClave).toHaveBeenCalledTimes(1)
    expect(api.asignarClave).toHaveBeenCalledWith(3, 'nueva12345')
    expect(api.toast).toHaveBeenCalledWith(expect.objectContaining({ summary: 'Contraseña asignada a lperez' }))
    expect(wrapper.find('.dialogo-stub').exists()).toBe(false)
    expect(llamadasLista()).toHaveLength(2)
  })

  it('pide confirmación para desactivar y no lo ofrece para uno mismo', async () => {
    responder({ ...vacia, total: 3, items: [yo, luis, marta] })
    const wrapper = await montar()
    expect(await opcionesMenu(wrapper, 'Admin General')).toEqual(['Asignar contraseña'])
    expect(await opcionesMenu(wrapper, 'Marta Ruiz')).toEqual(['Asignar contraseña', 'Activar'])
    expect(await opcionesMenu(wrapper, 'Luis Pérez')).toEqual(['Asignar contraseña', 'Desactivar'])
    await elegir(wrapper, 'Luis Pérez', 'Desactivar')
    expect(api.cambiarEstadoUsuario).not.toHaveBeenCalled()
    expect(api.confirmar).toHaveBeenCalledWith(expect.objectContaining({
      header: '¿Desactivar a Luis Pérez?', message: 'No podrá iniciar sesión hasta que se active de nuevo.', acceptLabel: 'Desactivar', rejectLabel: 'Cancelar',
    }))
    api.confirmar.mock.calls[0]![0].accept()
    api.confirmar.mock.calls[0]![0].accept()
    await flushPromises()
    expect(api.cambiarEstadoUsuario).toHaveBeenCalledTimes(1)
    expect(api.cambiarEstadoUsuario).toHaveBeenCalledWith(3, 'INACTIVO')
    expect(api.toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', summary: 'Usuario lperez desactivado' }))
    expect(llamadasLista()).toHaveLength(2)
  })

  it('activa directamente a un usuario inactivo', async () => {
    responder({ ...vacia, total: 1, items: [marta] })
    const wrapper = await montar()
    await elegir(wrapper, 'Marta Ruiz', 'Activar')
    expect(api.confirmar).not.toHaveBeenCalled()
    expect(api.cambiarEstadoUsuario).toHaveBeenCalledWith(4, 'ACTIVO')
    expect(api.toast).toHaveBeenCalledWith(expect.objectContaining({ summary: 'Usuario mruiz activado' }))
  })

  it('ofrece desbloquear solo a los bloqueados', async () => {
    responder({ ...vacia, total: 2, items: [ana, luis] })
    const wrapper = await montar()
    expect(await opcionesMenu(wrapper, 'Luis Pérez')).not.toContain('Desbloquear')
    expect(await opcionesMenu(wrapper, 'Ana López')).toEqual(['Asignar contraseña', 'Desbloquear', 'Desactivar'])
    // Si la recarga falla, la fila igual refleja la respuesta.
    api.listarUsuarios.mockRejectedValue(new ApiError(503, 'NO_DISPONIBLE', 'Base no disponible'))
    await elegir(wrapper, 'Ana López', 'Desbloquear')
    expect(api.desbloquearUsuario).toHaveBeenCalledWith(2)
    expect(api.toast).toHaveBeenCalledWith(expect.objectContaining({ summary: 'Usuario ana.lopez desbloqueado' }))
    expect(wrapper.find('.estado-usuario--bloqueado').exists()).toBe(false)
    expect(wrapper.find('tbody').text()).toContain('Luis Pérez')
  })

  it('muestra la salida vacía con filtros y limpia', async () => {
    const sinFiltros = await montar()
    expect(sinFiltros.text()).toContain('No hay usuarios activos.')
    route.query = { rol: 'BODEGA' }
    const filtrada = await montar()
    expect(filtrada.text()).toContain('No hay usuarios con estos filtros.')
    const limpiar = filtrada.findAll('button').filter((item) => item.text() === 'Limpiar filtros')
    expect(limpiar).toHaveLength(2)
    await limpiar[1]!.trigger('click')
    expect(api.replace).toHaveBeenCalledWith({ name: 'usuarios', query: {} })
  })

  it('avisa el error de carga, reintenta e ignora respuestas viejas', async () => {
    api.listarUsuarios.mockImplementation((_f: unknown, _p: number, tamano: number) => tamano === 100 ? Promise.resolve(vacia) : Promise.reject(new Error('red')))
    const wrapper = await montar()
    expect(wrapper.text()).toContain('No se pudieron cargar los usuarios.')
    let primera: (valor: unknown) => void = () => {}
    api.listarUsuarios.mockImplementationOnce(() => new Promise((resolve) => { primera = resolve }))
    await boton(wrapper, 'Reintentar')!.trigger('click')
    responder({ ...vacia, total: 1, items: [luis] })
    route.query = { rol: 'GERENTE' }
    await flushPromises()
    primera({ ...vacia, total: 1, items: [ana] })
    await flushPromises()
    expect(wrapper.text()).toContain('Luis Pérez')
    expect(wrapper.text()).not.toContain('Ana López')
  })
})

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import type { MenuItem } from 'primevue/menuitem'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import SelectButton from 'primevue/selectbutton'
import Select from 'primevue/select'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Menu from 'primevue/menu'
import Message from 'primevue/message'
import { ApiError } from '../api/cliente'
import { listarSucursales, type Sucursal } from '../api/pedidos'
import { etiquetaRol } from '../api/roles'
import { ROLES, type Rol } from '../api/tipos'
import {
  actualizarUsuario, asignarClave, cambiarEstadoUsuario, crearUsuario, desbloquearUsuario, listarRolesCatalogo, listarUsuarios,
  type RolCatalogo, type UsuarioAdmin,
} from '../api/usuarios'
import { useSesionStore } from '../stores/sesion'
import {
  AYUDA_USERNAME, LARGO_BUSQUEDA, cambiarFiltros, consultaUsuarios, etiquetaAcceso, filtrarUsuarios, filtrosPorDefecto,
  leerFiltrosUsuarios, normalizarBusqueda, normalizarUsuario, queryFiltrosUsuarios, textoResumenUsuarios, validarClave, validarUsuario,
  type ErroresUsuario, type FiltroEstado, type FiltrosUsuarios, type FormularioUsuario, type ModoUsuario,
} from './usuarios'

const TAMANO = 20
const route = useRoute()
const router = useRouter()
const sesion = useSesionStore()
const toast = useToast()
const confirm = useConfirm()
const filtros = computed(() => leerFiltrosUsuarios(route.query))
const filtrosActivos = computed(() => !filtrosPorDefecto(filtros.value))
const estados = [{ label: 'Activos', value: 'ACTIVO' }, { label: 'Inactivos', value: 'INACTIVO' }, { label: 'Todos', value: 'TODOS' }]
const opcionesRolFiltro = [{ label: 'Todos los roles', value: null }, ...ROLES.map((rol) => ({ label: etiquetaRol(rol), value: rol }))]
const sucursales = ref<Sucursal[]>([])
const errorSucursales = ref(false)
const opcionesSucursales = computed(() => [{ idSucursal: null, nombre: 'Todas las sucursales' }, ...sucursales.value])
const catalogoRoles = ref<RolCatalogo[]>([])
// La descripción del catálogo es un apoyo: si no carga, solo se muestra el nombre.
const opcionesRol = computed(() => ROLES.map((rol) => ({
  value: rol, label: etiquetaRol(rol), descripcion: catalogoRoles.value.find((item) => item.nombre === rol)?.descripcion ?? '',
})))
const usuarios = ref<UsuarioAdmin[]>([])
const total = ref(0)
const cargando = ref(true)
const error = ref(false)
const resumen = ref<string | null>(null)
const busqueda = ref(filtros.value.q)
const visibles = computed(() => filtrarUsuarios(usuarios.value, filtros.value.q))
const idPropio = computed(() => sesion.usuario?.idUsuario ?? null)
const seccion = ref<HTMLElement | null>(null)
const titulo = ref<HTMLElement | null>(null)
let versionLista = 0
let versionResumen = 0
let temporizador: ReturnType<typeof setTimeout> | undefined
// Selector del control que recupera el foco al cerrar un diálogo o terminar una acción.
let retornoFoco: string | null = null

// Diálogo de alta y edición.
const vacio: FormularioUsuario = { nombre: '', username: '', rol: null, idSucursal: null, password: '', confirmacion: '' }
const formularioVisible = ref(false)
const modo = ref<ModoUsuario>('crear')
const editado = ref<UsuarioAdmin | null>(null)
const form = reactive<FormularioUsuario>({ ...vacio })
const tocados = reactive<Partial<Record<keyof ErroresUsuario, boolean>>>({})
const intentado = ref(false)
const guardando = ref(false)
const errorFormulario = ref('')
const errores = computed(() => validarUsuario(form, modo.value))
const ORDEN_CAMPOS: [keyof ErroresUsuario, string][] = [
  ['nombre', 'usuario-nombre'], ['username', 'usuario-username'], ['rol', 'usuario-rol'],
  ['sucursal', 'usuario-sucursal'], ['password', 'usuario-clave'], ['confirmacion', 'usuario-confirmacion'],
]

// Diálogo de contraseña.
const claveVisible = ref(false)
const claveUsuario = ref<UsuarioAdmin | null>(null)
const clave = ref('')
const claveConfirmacion = ref('')
const claveTocados = reactive<{ password?: boolean; confirmacion?: boolean }>({})
const claveIntentado = ref(false)
const asignando = ref(false)
const errorClave = ref('')
const erroresClave = computed(() => validarClave(clave.value, claveConfirmacion.value))

// Menú de acciones de la fila y acciones directas.
const menu = ref<{ toggle: (evento: Event) => void } | null>(null)
const filaMenu = ref<UsuarioAdmin | null>(null)
const accionEnCurso = ref(false)
const opcionesMenu = computed<MenuItem[]>(() => {
  const usuario = filaMenu.value
  if (!usuario) return []
  const opciones: MenuItem[] = [{ label: 'Asignar contraseña', icon: 'pi pi-key', command: () => abrirClave(usuario) }]
  if (usuario.bloqueado) opciones.push({ label: 'Desbloquear', icon: 'pi pi-lock-open', disabled: accionEnCurso.value, command: () => { void desbloquear(usuario) } })
  if (usuario.estado === 'INACTIVO') opciones.push({ label: 'Activar', icon: 'pi pi-check', disabled: accionEnCurso.value, command: () => { void activar(usuario) } })
  // Nadie se desactiva a sí mismo.
  else if (!esPropio(usuario)) opciones.push({ label: 'Desactivar', icon: 'pi pi-ban', disabled: accionEnCurso.value, command: () => pedirDesactivar(usuario) })
  return opciones
})

function esPropio(usuario: UsuarioAdmin): boolean { return usuario.idUsuario === idPropio.value }

function navegar(nuevos: FiltrosUsuarios): void {
  void router.replace({ name: 'usuarios', query: queryFiltrosUsuarios(nuevos) })
}
function actualizar(cambio: Partial<Omit<FiltrosUsuarios, 'pagina'>>): void { navegar(cambiarFiltros(filtros.value, cambio)) }
function cambiarRolFiltro(valor: Rol | null): void { actualizar({ rol: valor }) }
function cambiarEstado(valor: FiltroEstado | null): void { if (valor) actualizar({ estado: valor }) }
function cambiarSucursal(valor: number | null): void { actualizar({ sucursal: valor }) }
function limpiar(): void {
  cancelarBusqueda()
  busqueda.value = ''
  void router.replace({ name: 'usuarios', query: {} })
}
function cambiarPagina(evento: { page: number }): void { navegar({ ...filtros.value, pagina: evento.page + 1 }) }

function cancelarBusqueda(): void {
  if (temporizador !== undefined) clearTimeout(temporizador)
  temporizador = undefined
}
function aplicarBusqueda(): void {
  cancelarBusqueda()
  const q = normalizarBusqueda(busqueda.value)
  if (q !== filtros.value.q) actualizar({ q })
}
function escribirBusqueda(valor: string | undefined): void {
  busqueda.value = valor ?? ''
  cancelarBusqueda()
  temporizador = setTimeout(aplicarBusqueda, 300)
}
function borrarBusqueda(): void {
  busqueda.value = ''
  aplicarBusqueda()
}

// Al recargar tras una acción se conservan los datos a la vista.
async function cargarLista(silencioso = false): Promise<void> {
  const version = ++versionLista
  if (!silencioso) cargando.value = true
  error.value = false
  try {
    const resultado = await listarUsuarios(consultaUsuarios(filtros.value), filtros.value.pagina, TAMANO)
    if (version !== versionLista) return
    const ultimaPagina = Math.max(1, Math.ceil(resultado.total / TAMANO))
    if (filtros.value.pagina > ultimaPagina) {
      navegar({ ...filtros.value, pagina: ultimaPagina })
      return
    }
    usuarios.value = resultado.items
    total.value = resultado.total
  } catch {
    if (version !== versionLista || (silencioso && usuarios.value.length)) return
    usuarios.value = []
    total.value = 0
    error.value = true
  } finally {
    if (version === versionLista) cargando.value = false
  }
}
// Una sola consulta de hasta 100 activos: el total es exacto, pero los bloqueados
// se cuentan entre esos 100 (tope de la API por página).
async function cargarResumen(silencioso = false): Promise<void> {
  const version = ++versionResumen
  if (!silencioso) resumen.value = null
  try {
    const activos = await listarUsuarios({ estado: 'ACTIVO' }, 1, 100, false)
    if (version !== versionResumen) return
    resumen.value = textoResumenUsuarios(activos.total, activos.items.filter((usuario) => usuario.bloqueado).length)
  } catch { if (version === versionResumen) resumen.value = null }
}
// La respuesta del servidor se aplica ya: si la recarga falla, la fila no queda vieja.
function aplicar(idUsuario: number, cambio: Partial<UsuarioAdmin>): void {
  usuarios.value = usuarios.value.map((fila) => fila.idUsuario === idUsuario ? { ...fila, ...cambio } : fila)
}
function recargar(): Promise<unknown> { return Promise.all([cargarLista(true), cargarResumen(true)]) }
async function cargarSucursales(): Promise<void> {
  try { sucursales.value = await listarSucursales(); errorSucursales.value = false }
  catch { errorSucursales.value = true }
}

function devolverFoco(): void {
  if (formularioVisible.value || claveVisible.value) return
  const destino = retornoFoco ? seccion.value?.querySelector<HTMLElement>(retornoFoco) : null
  ;(destino ?? titulo.value)?.focus()
}

function abrirNuevo(): void {
  prepararFormulario('crear', null)
  retornoFoco = '[data-nuevo-usuario]'
}
function abrirEditar(usuario: UsuarioAdmin): void {
  prepararFormulario('editar', usuario)
  retornoFoco = `[data-editar="${usuario.idUsuario}"]`
}
function prepararFormulario(nuevoModo: ModoUsuario, usuario: UsuarioAdmin | null): void {
  modo.value = nuevoModo
  editado.value = usuario
  Object.assign(form, vacio, usuario ? { nombre: usuario.nombre, username: usuario.username, rol: usuario.rol, idSucursal: usuario.idSucursal } : {})
  for (const campo of Object.keys(tocados) as (keyof ErroresUsuario)[]) delete tocados[campo]
  intentado.value = false
  errorFormulario.value = ''
  formularioVisible.value = true
}
// Solo el rol Sucursal lleva sucursal: al cambiar a otro rol se borra.
function cambiarRol(valor: Rol | null): void {
  form.rol = valor
  if (valor !== 'SUCURSAL') form.idSucursal = null
  tocados.rol = true
}
function verError(campo: keyof ErroresUsuario): boolean {
  return (intentado.value || !!tocados[campo]) && !!errores.value[campo]
}
async function guardarUsuario(): Promise<void> {
  if (guardando.value) return
  intentado.value = true
  errorFormulario.value = ''
  const primero = ORDEN_CAMPOS.find(([campo]) => errores.value[campo])
  if (primero) {
    await nextTick()
    document.getElementById(primero[1])?.focus()
    return
  }
  guardando.value = true
  try {
    if (modo.value === 'crear') {
      const solicitud = normalizarUsuario(form)
      await crearUsuario(solicitud)
      toast.add({ severity: 'success', summary: `Usuario ${solicitud.username} creado`, life: 5000 })
    } else if (editado.value) {
      retornoFoco = `[data-editar="${editado.value.idUsuario}"]`
      aplicar(editado.value.idUsuario, await actualizarUsuario(editado.value.idUsuario, normalizarUsuario(form, 'editar')))
      toast.add({ severity: 'success', summary: 'Cambios guardados', life: 5000 })
    }
    formularioVisible.value = false
    await recargar()
    devolverFoco()
  } catch (causa) {
    // 409 y 503 ya los avisó el cliente: el diálogo queda abierto con sus datos.
    if (causa instanceof ApiError && (causa.status === 400 || causa.status === 404)) errorFormulario.value = causa.message
  } finally { guardando.value = false }
}

function abrirClave(usuario: UsuarioAdmin): void {
  claveUsuario.value = usuario
  clave.value = ''
  claveConfirmacion.value = ''
  delete claveTocados.password
  delete claveTocados.confirmacion
  claveIntentado.value = false
  errorClave.value = ''
  retornoFoco = `[data-acciones="${usuario.idUsuario}"]`
  claveVisible.value = true
}
function verErrorClave(campo: 'password' | 'confirmacion'): boolean {
  return (claveIntentado.value || !!claveTocados[campo]) && !!erroresClave.value[campo]
}
async function guardarClave(): Promise<void> {
  const usuario = claveUsuario.value
  if (asignando.value || !usuario) return
  claveIntentado.value = true
  errorClave.value = ''
  const primero = erroresClave.value.password ? 'clave-nueva-usuario' : erroresClave.value.confirmacion ? 'clave-confirmar-usuario' : null
  if (primero) {
    await nextTick()
    document.getElementById(primero)?.focus()
    return
  }
  asignando.value = true
  try {
    await asignarClave(usuario.idUsuario, clave.value)
    aplicar(usuario.idUsuario, { tieneClave: true })
    claveVisible.value = false
    toast.add({ severity: 'success', summary: `Contraseña asignada a ${usuario.username}`, life: 5000 })
    await recargar()
    devolverFoco()
  } catch (causa) {
    if (causa instanceof ApiError && (causa.status === 400 || causa.status === 404)) errorClave.value = causa.message
  } finally { asignando.value = false }
}

function abrirMenu(evento: Event, usuario: UsuarioAdmin): void {
  filaMenu.value = usuario
  menu.value?.toggle(evento)
}
async function ejecutar(usuario: UsuarioAdmin, accion: () => Promise<UsuarioAdmin>, mensaje: string): Promise<void> {
  if (accionEnCurso.value) return
  accionEnCurso.value = true
  retornoFoco = `[data-acciones="${usuario.idUsuario}"]`
  try {
    aplicar(usuario.idUsuario, await accion())
    toast.add({ severity: 'success', summary: mensaje, life: 5000 })
    await recargar()
  } catch (causa) {
    // 409 y 503 ya los avisó el cliente; 400 y 404 no traen diálogo donde mostrarse.
    if (causa instanceof ApiError && (causa.status === 400 || causa.status === 404)) {
      toast.add({ severity: 'error', summary: 'No se pudo completar la acción', detail: causa.message, life: 5000 })
    }
  } finally {
    accionEnCurso.value = false
    devolverFoco()
  }
}
function desbloquear(usuario: UsuarioAdmin): Promise<void> {
  return ejecutar(usuario, () => desbloquearUsuario(usuario.idUsuario), `Usuario ${usuario.username} desbloqueado`)
}
function activar(usuario: UsuarioAdmin): Promise<void> {
  return ejecutar(usuario, () => cambiarEstadoUsuario(usuario.idUsuario, 'ACTIVO'), `Usuario ${usuario.username} activado`)
}
function pedirDesactivar(usuario: UsuarioAdmin): void {
  if (accionEnCurso.value || esPropio(usuario)) return
  confirm.require({
    header: `¿Desactivar a ${usuario.nombre}?`, message: 'No podrá iniciar sesión hasta que se active de nuevo.',
    acceptLabel: 'Desactivar', rejectLabel: 'Cancelar',
    accept: () => { void ejecutar(usuario, () => cambiarEstadoUsuario(usuario.idUsuario, 'INACTIVO'), `Usuario ${usuario.username} desactivado`) },
  })
}

// La búsqueda es local: solo los filtros del servidor y la página vuelven a consultar.
const claveConsulta = computed(() => JSON.stringify([consultaUsuarios(filtros.value), filtros.value.pagina]))
watch(claveConsulta, () => { void cargarLista() }, { immediate: true })
// Si la búsqueda cambia desde la URL (atrás, limpiar), el campo la sigue.
watch(() => filtros.value.q, (q) => {
  if (normalizarBusqueda(busqueda.value) !== q) { cancelarBusqueda(); busqueda.value = q }
})
void cargarResumen()
void cargarSucursales()
void listarRolesCatalogo().then((items) => { catalogoRoles.value = items }).catch(() => {})
onBeforeUnmount(cancelarBusqueda)
</script>

<template>
  <section ref="seccion" class="pagina pagina-pedidos pagina-usuarios">
    <div class="cabecera-pedidos">
      <div>
        <h1 ref="titulo" tabindex="-1">Usuarios</h1>
        <p v-if="resumen" class="subtitulo hallazgo-pedidos resumen-usuarios">{{ resumen }}</p>
      </div>
      <Button label="Nuevo usuario" icon="pi pi-plus" data-nuevo-usuario @click="abrirNuevo" />
    </div>
    <div class="filtros-pedidos" aria-label="Filtros de usuarios">
      <div class="filtro-select">
        <label for="rol-filtro">Rol</label>
        <Select input-id="rol-filtro" :model-value="filtros.rol" :options="opcionesRolFiltro" option-label="label" option-value="value" placeholder="Todos los roles" @update:model-value="cambiarRolFiltro" />
      </div>
      <div class="filtro-entrega">
        <label>Estado</label>
        <SelectButton :model-value="filtros.estado" :options="estados" option-label="label" option-value="value" :allow-empty="false" aria-label="Estado" @update:model-value="cambiarEstado" />
      </div>
      <div class="filtro-select">
        <label for="sucursal-filtro">Sucursal</label>
        <Select input-id="sucursal-filtro" :model-value="filtros.sucursal" :options="opcionesSucursales" option-label="nombre" option-value="idSucursal" placeholder="Todas las sucursales" @update:model-value="cambiarSucursal" />
      </div>
      <div class="filtro-select filtro-busqueda">
        <label for="buscar-usuario">Buscar por nombre o usuario</label>
        <div class="control-busqueda">
          <InputText id="buscar-usuario" :model-value="busqueda" :maxlength="LARGO_BUSQUEDA" autocomplete="off" enterkeyhint="search" :aria-describedby="total > TAMANO ? 'buscar-usuario-ayuda' : undefined" @update:model-value="escribirBusqueda" @keydown.enter.prevent="aplicarBusqueda" />
          <Button v-if="busqueda" icon="pi pi-times" text rounded aria-label="Borrar búsqueda" class="borrar-busqueda" @click="borrarBusqueda" />
        </div>
        <small v-if="total > TAMANO" id="buscar-usuario-ayuda" class="ayuda-campo">Busca en la página actual.</small>
      </div>
      <Button v-if="filtrosActivos" label="Limpiar filtros" text class="limpiar-filtros" @click="limpiar" />
    </div>
    <div class="tabla-pedidos tabla-inventario tabla-usuarios">
      <DataTable :value="visibles" lazy paginator :rows="TAMANO" :first="(filtros.pagina - 1) * TAMANO" :total-records="total" :loading="cargando" data-key="idUsuario" @page="cambiarPagina">
        <Column header="Nombre">
          <template #body="{ data }">
            <span class="nombre-usuario"><strong>{{ data.nombre }}</strong><span v-if="esPropio(data)" class="texto-suave"> (usted)</span></span>
            <span class="texto-suave username-usuario">{{ data.username }}</span>
          </template>
        </Column>
        <Column header="Rol"><template #body="{ data }">{{ etiquetaRol(data.rol) }}</template></Column>
        <Column header="Sucursal">
          <template #body="{ data }"><template v-if="data.nombreSucursal">{{ data.nombreSucursal }}</template><span v-else class="texto-suave">—</span></template>
        </Column>
        <Column header="Estado">
          <template #body="{ data }">
            <div class="estados-usuario">
              <span class="estado-usuario" :class="data.estado === 'ACTIVO' ? 'estado-usuario--activo' : 'estado-usuario--inactivo'">{{ data.estado === 'ACTIVO' ? 'Activo' : 'Inactivo' }}</span>
              <span v-if="data.bloqueado" class="estado-usuario estado-usuario--bloqueado">Bloqueado</span>
              <small v-if="!data.tieneClave" class="sin-clave">Sin contraseña</small>
            </div>
          </template>
        </Column>
        <Column header="Último acceso"><template #body="{ data }"><span :class="{ 'texto-suave': !data.ultimoAcceso }">{{ etiquetaAcceso(data.ultimoAcceso) }}</span></template></Column>
        <Column header="Acciones" body-class="celda-accion">
          <template #body="{ data }">
            <div class="acciones-inventario">
              <Button label="Editar" icon="pi pi-pencil" size="small" outlined :aria-label="`Editar a ${data.nombre}`" :data-editar="data.idUsuario" @click="abrirEditar(data)" />
              <Button icon="pi pi-ellipsis-v" size="small" outlined aria-haspopup="true" aria-controls="menu-usuario" :aria-label="`Más acciones para ${data.nombre}`" :data-acciones="data.idUsuario" @click="abrirMenu($event, data)" />
            </div>
          </template>
        </Column>
        <template #empty>
          <div v-if="!cargando" class="tabla-estado">
            <template v-if="error"><Message severity="error">No se pudieron cargar los usuarios.</Message><Button label="Reintentar" text @click="cargarLista()" /></template>
            <template v-else-if="filtrosActivos"><p>No hay usuarios con estos filtros.</p><Button label="Limpiar filtros" text @click="limpiar" /></template>
            <p v-else>No hay usuarios activos.</p>
          </div>
        </template>
      </DataTable>
    </div>
    <Menu id="menu-usuario" ref="menu" :model="opcionesMenu" popup />

    <Dialog v-model:visible="formularioVisible" modal :header="modo === 'crear' ? 'Nuevo usuario' : 'Editar usuario'" class="dialogo-produccion dialogo-usuario" :closable="!guardando" @after-hide="devolverFoco">
      <form class="formulario-dialogo" novalidate @submit.prevent="guardarUsuario">
        <Message v-if="errorFormulario" severity="error" aria-live="polite">{{ errorFormulario }}</Message>
        <div class="campo">
          <label for="usuario-nombre">Nombre</label>
          <InputText id="usuario-nombre" v-model="form.nombre" :maxlength="100" autocomplete="off" autofocus fluid :disabled="guardando" :invalid="verError('nombre')" @blur="tocados.nombre = true" />
          <small v-if="verError('nombre')" class="error-campo">{{ errores.nombre }}</small>
        </div>
        <div class="campo">
          <label for="usuario-username">Usuario</label>
          <InputText v-if="modo === 'crear'" id="usuario-username" v-model="form.username" :maxlength="50" autocomplete="off" autocapitalize="none" spellcheck="false" fluid :disabled="guardando" :invalid="verError('username')" aria-describedby="usuario-username-ayuda" @blur="tocados.username = true" />
          <span v-else id="usuario-username" class="valor-sucursal valor-username">{{ form.username }}</span>
          <small v-if="modo === 'crear'" id="usuario-username-ayuda" class="ayuda-campo">{{ AYUDA_USERNAME }}</small>
          <small v-if="verError('username')" class="error-campo">{{ errores.username }}</small>
        </div>
        <div class="campo">
          <label for="usuario-rol">Rol</label>
          <Select :model-value="form.rol" input-id="usuario-rol" :options="opcionesRol" option-label="label" option-value="value" placeholder="Elija un rol" fluid :disabled="guardando" :invalid="verError('rol')" @update:model-value="cambiarRol">
            <template #option="{ option }">
              <span class="opcion-rol"><span>{{ option.label }}</span><small v-if="option.descripcion" class="texto-suave">{{ option.descripcion }}</small></span>
            </template>
          </Select>
          <small v-if="verError('rol')" class="error-campo">{{ errores.rol }}</small>
        </div>
        <div v-if="form.rol === 'SUCURSAL'" class="campo">
          <label for="usuario-sucursal">Sucursal</label>
          <Select v-model="form.idSucursal" input-id="usuario-sucursal" :options="sucursales" option-label="nombre" option-value="idSucursal" filter placeholder="Elija una sucursal" fluid :disabled="guardando" :invalid="verError('sucursal')" @change="tocados.sucursal = true" />
          <small v-if="errorSucursales" class="error-campo">No se pudieron cargar las sucursales. <Button label="Reintentar" text size="small" type="button" @click="cargarSucursales" /></small>
          <small v-if="verError('sucursal')" class="error-campo">{{ errores.sucursal }}</small>
        </div>
        <template v-if="modo === 'crear'">
          <div class="campo">
            <label for="usuario-clave">Contraseña</label>
            <Password v-model="form.password" input-id="usuario-clave" :feedback="false" toggle-mask autocomplete="new-password" fluid :disabled="guardando" :invalid="verError('password')" :input-props="{ 'aria-describedby': 'usuario-clave-ayuda' }" @blur="tocados.password = true" />
            <small id="usuario-clave-ayuda" class="ayuda-campo">Entre 8 y 128 caracteres.</small>
            <small v-if="verError('password')" class="error-campo">{{ errores.password }}</small>
          </div>
          <div class="campo">
            <label for="usuario-confirmacion">Confirmar contraseña</label>
            <Password v-model="form.confirmacion" input-id="usuario-confirmacion" :feedback="false" toggle-mask autocomplete="new-password" fluid :disabled="guardando" :invalid="verError('confirmacion')" @blur="tocados.confirmacion = true" />
            <small v-if="verError('confirmacion')" class="error-campo">{{ errores.confirmacion }}</small>
          </div>
        </template>
        <div class="acciones-dialogo">
          <Button label="Cancelar" text type="button" :disabled="guardando" @click="formularioVisible = false" />
          <Button :label="modo === 'crear' ? 'Crear usuario' : 'Guardar cambios'" type="submit" :loading="guardando" :disabled="guardando" />
        </div>
      </form>
    </Dialog>

    <Dialog v-model:visible="claveVisible" modal header="Asignar contraseña" class="dialogo-produccion dialogo-clave" :closable="!asignando" @after-hide="devolverFoco">
      <form class="formulario-dialogo" novalidate @submit.prevent="guardarClave">
        <p v-if="claveUsuario" class="producto-movimiento">{{ claveUsuario.nombre }} · {{ claveUsuario.username }}</p>
        <Message v-if="errorClave" severity="error" aria-live="polite">{{ errorClave }}</Message>
        <div class="campo">
          <label for="clave-nueva-usuario">Nueva contraseña</label>
          <Password v-model="clave" input-id="clave-nueva-usuario" :feedback="false" toggle-mask autocomplete="new-password" fluid :disabled="asignando" :invalid="verErrorClave('password')" :input-props="{ 'aria-describedby': 'clave-nueva-usuario-ayuda', autofocus: true }" @blur="claveTocados.password = true" />
          <small id="clave-nueva-usuario-ayuda" class="ayuda-campo">Entre 8 y 128 caracteres.</small>
          <small v-if="verErrorClave('password')" class="error-campo">{{ erroresClave.password }}</small>
        </div>
        <div class="campo">
          <label for="clave-confirmar-usuario">Confirmar contraseña</label>
          <Password v-model="claveConfirmacion" input-id="clave-confirmar-usuario" :feedback="false" toggle-mask autocomplete="new-password" fluid :disabled="asignando" :invalid="verErrorClave('confirmacion')" @blur="claveTocados.confirmacion = true" />
          <small v-if="verErrorClave('confirmacion')" class="error-campo">{{ erroresClave.confirmacion }}</small>
        </div>
        <div class="acciones-dialogo">
          <Button label="Cancelar" text type="button" :disabled="asignando" @click="claveVisible = false" />
          <Button label="Asignar contraseña" type="submit" :loading="asignando" :disabled="asignando" />
        </div>
      </form>
    </Dialog>
  </section>
</template>

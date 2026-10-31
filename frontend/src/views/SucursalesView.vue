<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { ApiError } from '../api/cliente'
import { actualizarSucursal, cambiarEstadoSucursal, crearSucursal, listarTodasSucursales,
  type SolicitudSucursal, type SucursalAdmin } from '../api/sucursales'
import { normalizarSucursal, validarSucursal } from './sucursales'

const confirm = useConfirm()
const toast = useToast()
const sucursales = ref<SucursalAdmin[]>([])
const cargando = ref(true)
const error = ref(false)
const visible = ref(false)
const editada = ref<SucursalAdmin | null>(null)
const form = reactive<SolicitudSucursal>({ nombre: '', direccion: '', telefono: '' })
const intentado = ref(false)
const guardando = ref(false)
const errorFormulario = ref('')
const errores = computed(() => validarSucursal(form))
let version = 0

async function cargar(): Promise<void> {
  const actual = ++version
  cargando.value = true
  error.value = false
  try { const lista = await listarTodasSucursales(); if (actual === version) sucursales.value = lista }
  catch { if (actual === version) error.value = true }
  finally { if (actual === version) cargando.value = false }
}
void cargar()

function abrir(sucursal: SucursalAdmin | null): void {
  editada.value = sucursal
  Object.assign(form, { nombre: sucursal?.nombre ?? '', direccion: sucursal?.direccion ?? '', telefono: sucursal?.telefono ?? '' })
  intentado.value = false
  errorFormulario.value = ''
  visible.value = true
}
async function guardar(): Promise<void> {
  if (guardando.value) return
  intentado.value = true
  errorFormulario.value = ''
  if (Object.keys(errores.value).length) return
  guardando.value = true
  try {
    if (editada.value) await actualizarSucursal(editada.value.idSucursal, normalizarSucursal(form))
    else await crearSucursal(normalizarSucursal(form))
    visible.value = false
    toast.add({ severity: 'success', summary: 'Sucursal guardada', life: 4000 })
    await cargar()
  } catch (causa) {
    if (causa instanceof ApiError && causa.status === 400) errorFormulario.value = causa.message
  } finally { guardando.value = false }
}
async function cambiarEstado(sucursal: SucursalAdmin): Promise<void> {
  try {
    await cambiarEstadoSucursal(sucursal.idSucursal, sucursal.estado === 'ACTIVO' ? 'INACTIVO' : 'ACTIVO')
    toast.add({ severity: 'success', summary: 'Estado actualizado', life: 4000 })
    await cargar()
  } catch (causa) {
    if (causa instanceof ApiError && causa.status === 400) toast.add({ severity: 'error', summary: causa.message, life: 5000 })
  }
}
function pedirCambio(sucursal: SucursalAdmin): void {
  if (sucursal.estado === 'INACTIVO') { void cambiarEstado(sucursal); return }
  confirm.require({ header: `¿Desactivar ${sucursal.nombre}?`,
    message: 'La sucursal dejará de estar disponible para nuevos pedidos y usuarios.',
    acceptLabel: 'Desactivar', rejectLabel: 'Cancelar', accept: () => { void cambiarEstado(sucursal) } })
}
</script>

<template>
  <section class="pagina pagina-catalogo">
    <div class="cabecera-pedidos"><div><h1>Sucursales</h1><p class="subtitulo">Administra las tiendas que hacen pedidos.</p></div><Button label="Nueva sucursal" icon="pi pi-plus" @click="abrir(null)" /></div>
    <div class="tabla-pedidos tabla-catalogo tabla-sucursales">
      <DataTable :value="sucursales" :loading="cargando" data-key="idSucursal">
        <Column field="nombre" header="Nombre" />
        <Column header="Dirección"><template #body="{ data }">{{ data.direccion || '—' }}</template></Column>
        <Column header="Teléfono"><template #body="{ data }">{{ data.telefono || '—' }}</template></Column>
        <Column field="usuariosActivos" header="Usuarios activos" header-class="numero" body-class="numero" />
        <Column field="pedidosAbiertos" header="Pedidos abiertos" header-class="numero" body-class="numero" />
        <Column header="Estado"><template #body="{ data }"><span class="estado-usuario" :class="data.estado === 'ACTIVO' ? 'estado-usuario--activo' : 'estado-usuario--inactivo'">{{ data.estado === 'ACTIVO' ? 'Activo' : 'Inactivo' }}</span></template></Column>
        <Column header="Acciones" body-class="celda-accion"><template #body="{ data }"><div class="acciones-catalogo"><Button label="Editar" outlined size="small" :aria-label="`Editar ${data.nombre}`" @click="abrir(data)" /><Button :label="data.estado === 'ACTIVO' ? 'Desactivar' : 'Activar'" text size="small" :aria-label="`${data.estado === 'ACTIVO' ? 'Desactivar' : 'Activar'} ${data.nombre}`" @click="pedirCambio(data)" /></div></template></Column>
        <template #empty><div v-if="!cargando" class="tabla-estado"><template v-if="error"><Message severity="error">No se pudieron cargar las sucursales.</Message><Button label="Reintentar" text @click="cargar" /></template><p v-else>No hay sucursales registradas.</p></div></template>
      </DataTable>
    </div>
    <Dialog v-model:visible="visible" modal :header="editada ? 'Editar sucursal' : 'Nueva sucursal'" class="dialogo-produccion" @hide="errorFormulario = ''">
      <form class="formulario-dialogo" @submit.prevent="guardar">
        <div><label for="sucursal-nombre">Nombre</label><InputText id="sucursal-nombre" v-model="form.nombre" maxlength="100" /><small v-if="intentado && errores.nombre" class="error-campo">{{ errores.nombre }}</small></div>
        <div><label for="sucursal-direccion">Dirección</label><InputText id="sucursal-direccion" v-model="form.direccion" maxlength="200" /><small v-if="intentado && errores.direccion" class="error-campo">{{ errores.direccion }}</small></div>
        <div><label for="sucursal-telefono">Teléfono</label><InputText id="sucursal-telefono" v-model="form.telefono" maxlength="20" /><small v-if="intentado && errores.telefono" class="error-campo">{{ errores.telefono }}</small></div>
        <Message v-if="errorFormulario" severity="error">{{ errorFormulario }}</Message>
        <div class="acciones-dialogo"><Button label="Cancelar" text type="button" @click="visible = false" /><Button label="Guardar" type="submit" :loading="guardando" /></div>
      </form>
    </Dialog>
  </section>
</template>

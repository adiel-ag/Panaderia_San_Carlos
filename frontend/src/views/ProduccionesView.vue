<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import SelectButton from 'primevue/selectbutton'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import Textarea from 'primevue/textarea'
import { ApiError } from '../api/cliente'
import { formatearFechaHora } from '../api/fechas'
import { crearProduccion, listarProducciones, type EstadoProduccion as Estado, type ProduccionResumen } from '../api/producciones'
import { useSesionStore } from '../stores/sesion'
import EstadoProduccion from '../components/EstadoProduccion.vue'
import { leerFiltrosProduccion, queryFiltrosProduccion, textoResumenProduccion } from './produccion'

const route = useRoute()
const router = useRouter()
const sesion = useSesionStore()
const toast = useToast()
const filtros = computed(() => leerFiltrosProduccion(route.query))
const puedeCambiar = computed(() => sesion.rol === 'ADMINISTRADOR' || sesion.rol === 'PRODUCCION')
// "Todas" usa un valor propio: SelectButton no distingue bien null de "sin elegir".
const opcionesEstado = [
  { label: 'Todas', value: 'TODAS' }, { label: 'Planificadas', value: 'PLANIFICADA' },
  { label: 'En proceso', value: 'EN_PROCESO' }, { label: 'Finalizadas', value: 'FINALIZADA' },
  { label: 'Canceladas', value: 'CANCELADA' },
]
const producciones = ref<ProduccionResumen[]>([])
const total = ref(0)
const cargando = ref(true)
const error = ref(false)
const resumen = ref<string | null>(null)
const dialogoVisible = ref(false)
const observacion = ref('')
const creando = ref(false)
const errorCrear = ref('')
let versionLista = 0
let versionResumen = 0

function cambiarEstado(valor: string | null): void {
  const estado = valor && valor !== 'TODAS' ? valor as Estado : null
  void router.replace({ name: 'produccion', query: queryFiltrosProduccion({ estado, pagina: 1 }) })
}
function cambiarPagina(evento: { page: number }): void {
  void router.replace({ name: 'produccion', query: queryFiltrosProduccion({ ...filtros.value, pagina: evento.page + 1 }) })
}
function destino(produccion: ProduccionResumen) {
  return { name: 'produccion-detalle', params: { id: produccion.idProduccion }, query: { volver: route.fullPath } }
}
function abrirFila(evento: { data: ProduccionResumen; originalEvent: Event }): void {
  if ((evento.originalEvent.target as Element).closest('a, button')) return
  void router.push(destino(evento.data))
}
function abrirDialogo(): void {
  observacion.value = ''
  errorCrear.value = ''
  dialogoVisible.value = true
}
async function crear(): Promise<void> {
  if (creando.value) return
  creando.value = true
  errorCrear.value = ''
  try {
    const { idProduccion } = await crearProduccion(observacion.value.trim() || null)
    dialogoVisible.value = false
    toast.add({ severity: 'success', summary: `Producción #${idProduccion} creada`, life: 5000 })
    await router.push({ name: 'produccion-detalle', params: { id: idProduccion } })
  } catch (causa) {
    if (causa instanceof ApiError && causa.status === 400) errorCrear.value = causa.message
  } finally { creando.value = false }
}

async function cargarLista(): Promise<void> {
  const version = ++versionLista
  cargando.value = true
  error.value = false
  try {
    const resultado = await listarProducciones(filtros.value.estado, filtros.value.pagina, 20)
    if (version !== versionLista) return
    const ultimaPagina = Math.max(1, Math.ceil(resultado.total / 20))
    if (filtros.value.pagina > ultimaPagina) {
      void router.replace({ name: 'produccion', query: queryFiltrosProduccion({ ...filtros.value, pagina: ultimaPagina }) })
      return
    }
    producciones.value = resultado.items
    total.value = resultado.total
  } catch {
    if (version !== versionLista) return
    producciones.value = []
    total.value = 0
    error.value = true
  } finally {
    if (version === versionLista) cargando.value = false
  }
}
async function cargarResumen(): Promise<void> {
  const version = ++versionResumen
  resumen.value = null
  try {
    const [enProceso, planificadas] = await Promise.all([
      listarProducciones('EN_PROCESO', 1, 1, false),
      listarProducciones('PLANIFICADA', 1, 1, false),
    ])
    if (version !== versionResumen) return
    resumen.value = textoResumenProduccion(enProceso.total, planificadas.total)
  } catch { if (version === versionResumen) resumen.value = null }
}

watch(() => route.query, () => { void cargarLista() }, { immediate: true, deep: true })
void cargarResumen()
</script>

<template>
  <section class="pagina pagina-producciones">
    <div class="cabecera-pedidos">
      <div><h1>Producción</h1><p v-if="resumen" class="subtitulo hallazgo-pedidos">{{ resumen }}</p></div>
      <Button v-if="puedeCambiar" label="Nueva producción" icon="pi pi-plus" @click="abrirDialogo" />
    </div>
    <div class="filtros-pedidos" aria-label="Filtros de producción">
      <div class="filtro-entrega">
        <label>Estado</label>
        <SelectButton :model-value="filtros.estado ?? 'TODAS'" :options="opcionesEstado" option-label="label" option-value="value" :allow-empty="false" aria-label="Estado" @update:model-value="cambiarEstado" />
      </div>
    </div>
    <div class="tabla-pedidos tabla-producciones">
      <DataTable :value="producciones" lazy paginator :rows="20" :first="(filtros.pagina - 1) * 20" :total-records="total" :loading="cargando" data-key="idProduccion" @page="cambiarPagina" @row-click="abrirFila">
        <Column header="N.º" body-class="identificador">
          <template #body="{ data }"><RouterLink :to="destino(data)" class="enlace-pedido">#{{ data.idProduccion }}</RouterLink></template>
        </Column>
        <Column header="Fecha"><template #body="{ data }">{{ formatearFechaHora(data.fechaProduccion) }}</template></Column>
        <Column header="Estado"><template #body="{ data }"><EstadoProduccion :estado="data.estado" /></template></Column>
        <Column field="cantidadPedidos" header="Pedidos" header-class="numero" body-class="numero" />
        <Column field="nombreUsuario" header="Registró" />
        <Column header="Observación" body-class="celda-observacion">
          <template #body="{ data }"><span v-if="data.observacion" class="observacion-truncada" :title="data.observacion">{{ data.observacion }}</span><span v-else class="texto-suave">—</span></template>
        </Column>
        <template #empty>
          <div v-if="!cargando" class="tabla-estado">
            <template v-if="error"><Message severity="error">No se pudieron cargar las producciones.</Message><Button label="Reintentar" text @click="cargarLista" /></template>
            <p v-else-if="filtros.estado">No hay producciones con este filtro.</p>
            <template v-else><p>Aún no hay producciones.</p><Button v-if="puedeCambiar" label="Nueva producción" text @click="abrirDialogo" /></template>
          </div>
        </template>
      </DataTable>
    </div>
    <Dialog v-model:visible="dialogoVisible" modal header="Nueva producción" class="dialogo-produccion" :closable="!creando">
      <form class="formulario-dialogo" @submit.prevent="crear">
        <Message v-if="errorCrear" severity="error" aria-live="polite">{{ errorCrear }}</Message>
        <div class="campo">
          <label for="observacion-produccion">Observación <span class="texto-suave">(opcional)</span></label>
          <Textarea id="observacion-produccion" v-model="observacion" auto-resize :maxlength="500" rows="3" fluid />
          <small class="contador-observacion">{{ observacion.length }}/500</small>
        </div>
        <div class="acciones-dialogo">
          <Button label="Cancelar" text type="button" :disabled="creando" @click="dialogoVisible = false" />
          <Button label="Crear producción" type="submit" :loading="creando" :disabled="creando" />
        </div>
      </form>
    </Dialog>
  </section>
</template>

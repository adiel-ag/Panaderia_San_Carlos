<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import SelectButton from 'primevue/selectbutton'
import Select from 'primevue/select'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { formatearFecha, hoyLocal, sumarDias } from '../api/fechas'
import { ESTADOS, etiquetaEstado, listarPedidos, listarSucursales, type Estado, type PedidoResumen, type Sucursal } from '../api/pedidos'
import { useSesionStore } from '../stores/sesion'
import EstadoPedido from '../components/EstadoPedido.vue'
import { consultaPedidos, leerFiltros, queryFiltros, textoResumenHoy, type Entrega, type FiltrosPedidos } from './filtrosPedidos'

const route = useRoute()
const router = useRouter()
const sesion = useSesionStore()
const hoy = hoyLocal()
const manana = sumarDias(hoy, 1)
const filtros = computed(() => leerFiltros(route.query))
const esSucursal = computed(() => sesion.rol === 'SUCURSAL')
const filtrosActivos = computed(() => filtros.value.entrega !== 'desde-hoy' || filtros.value.estado !== null ||
  (!esSucursal.value && filtros.value.sucursal !== null))
const entregas = [
  { label: 'Desde hoy', value: 'desde-hoy' }, { label: 'Hoy', value: 'hoy' },
  { label: 'Mañana', value: 'manana' }, { label: 'Esta semana', value: 'esta-semana' },
  { label: 'Todas las fechas', value: 'todas' },
]
const estados = [{ label: 'Todos los estados', value: null }, ...ESTADOS.map((estado) => ({ label: etiquetaEstado(estado), value: estado }))]
const sucursales = ref<Sucursal[]>([])
const opcionesSucursales = computed(() => [{ idSucursal: null, nombre: 'Todas las sucursales' }, ...sucursales.value])
const pedidos = ref<PedidoResumen[]>([])
const total = ref(0)
const cargando = ref(true)
const error = ref(false)
const resumenHoy = ref<string | null>(null)
let versionLista = 0
let versionResumen = 0

function filtroEfectivo(): FiltrosPedidos {
  return { ...filtros.value, sucursal: esSucursal.value ? sesion.usuario?.idSucursal ?? null : filtros.value.sucursal }
}
function actualizar(cambio: Partial<FiltrosPedidos>): void {
  void router.replace({ name: 'pedidos', query: queryFiltros({ ...filtros.value, ...cambio, pagina: 1 }) })
}
function cambiarEntrega(valor: Entrega): void { actualizar({ entrega: valor }) }
function cambiarEstado(valor: Estado | null): void { actualizar({ estado: valor }) }
function cambiarSucursal(valor: number | null): void { actualizar({ sucursal: valor }) }
function limpiar(): void { void router.replace({ name: 'pedidos', query: {} }) }
function verTodas(): void { actualizar({ entrega: 'todas', estado: null, sucursal: null }) }
function cambiarPagina(evento: { page: number }): void {
  void router.replace({ name: 'pedidos', query: queryFiltros({ ...filtros.value, pagina: evento.page + 1 }) })
}
function destino(pedido: PedidoResumen) {
  return { name: 'pedido', params: { id: pedido.idPedido }, query: { volver: route.fullPath } }
}
function abrirFila(evento: { data: PedidoResumen; originalEvent: Event }): void {
  if ((evento.originalEvent.target as Element).closest('a, button')) return
  void router.push(destino(evento.data))
}
function avisoEntrega(fecha: string): string {
  return fecha === hoy ? 'Hoy' : fecha === manana ? 'Mañana' : ''
}

async function cargarLista(): Promise<void> {
  const version = ++versionLista
  cargando.value = true
  error.value = false
  try {
    const resultado = await listarPedidos(consultaPedidos(filtroEfectivo(), hoy), filtros.value.pagina, 20)
    if (version !== versionLista) return
    const ultimaPagina = Math.max(1, Math.ceil(resultado.total / 20))
    if (filtros.value.pagina > ultimaPagina) {
      void router.replace({ name: 'pedidos', query: queryFiltros({ ...filtros.value, pagina: ultimaPagina }) })
      return
    }
    pedidos.value = resultado.items
    total.value = resultado.total
  } catch {
    if (version !== versionLista) return
    pedidos.value = []
    total.value = 0
    error.value = true
  } finally {
    if (version === versionLista) cargando.value = false
  }
}
async function cargarResumen(): Promise<void> {
  const version = ++versionResumen
  resumenHoy.value = null
  const sucursal = esSucursal.value ? sesion.usuario?.idSucursal ?? undefined : filtros.value.sucursal ?? undefined
  try {
    const base = { idSucursal: sucursal, entregaDesde: hoy, entregaHasta: hoy }
    const [todos, cancelados, despachados] = await Promise.all([
      listarPedidos(base, 1, 1, false),
      listarPedidos({ ...base, estado: 'CANCELADO' }, 1, 1, false),
      listarPedidos({ ...base, estado: 'DESPACHADO' }, 1, 1, false),
    ])
    if (version !== versionResumen) return
    resumenHoy.value = textoResumenHoy(todos.total, cancelados.total, despachados.total)
  } catch { if (version === versionResumen) resumenHoy.value = null }
}

watch([() => route.query, () => sesion.rol, () => sesion.usuario?.idSucursal], () => { void cargarLista() }, { immediate: true, deep: true })
watch([() => filtros.value.sucursal, () => sesion.rol, () => sesion.usuario?.idSucursal], () => { void cargarResumen() }, { immediate: true })
watch(esSucursal, (oculta) => { if (!oculta) void listarSucursales().then((items) => { sucursales.value = items }).catch(() => {}) }, { immediate: true })
</script>

<template>
  <section class="pagina pagina-pedidos">
    <div class="cabecera-pedidos">
      <div><h1>Pedidos</h1><p v-if="resumenHoy" class="subtitulo hallazgo-pedidos">{{ resumenHoy }}</p></div>
      <RouterLink v-if="sesion.rol === 'ADMINISTRADOR' || sesion.rol === 'SUCURSAL'" :to="{ name: 'pedido-nuevo', query: { volver: route.fullPath } }" class="p-button enlace-nuevo-pedido"><i class="pi pi-plus" aria-hidden="true" />Nuevo pedido</RouterLink>
    </div>
    <div class="filtros-pedidos" aria-label="Filtros de pedidos">
      <div class="filtro-entrega">
        <label>Entrega</label>
        <SelectButton :model-value="filtros.entrega" :options="entregas" option-label="label" option-value="value" aria-label="Entrega" @update:model-value="cambiarEntrega" />
      </div>
      <div class="filtro-select">
        <label for="estado-pedidos">Estado</label>
        <Select input-id="estado-pedidos" :model-value="filtros.estado" :options="estados" option-label="label" option-value="value" placeholder="Todos los estados" @update:model-value="cambiarEstado" />
      </div>
      <div v-if="!esSucursal" class="filtro-select">
        <label for="sucursal-pedidos">Sucursal</label>
        <Select input-id="sucursal-pedidos" :model-value="filtros.sucursal" :options="opcionesSucursales" option-label="nombre" option-value="idSucursal" placeholder="Todas las sucursales" @update:model-value="cambiarSucursal" />
      </div>
      <Button v-if="filtrosActivos" label="Limpiar filtros" text class="limpiar-filtros" @click="limpiar" />
    </div>
    <div class="tabla-pedidos">
      <DataTable :value="pedidos" lazy paginator :rows="20" :first="(filtros.pagina - 1) * 20" :total-records="total" :loading="cargando" data-key="idPedido" @page="cambiarPagina" @row-click="abrirFila">
        <Column header="N.º" body-class="identificador">
          <template #body="{ data }"><RouterLink :to="destino(data)" class="enlace-pedido">#{{ data.idPedido }}</RouterLink></template>
        </Column>
        <Column v-if="!esSucursal" field="nombreSucursal" header="Sucursal" />
        <Column header="Entrega">
          <template #body="{ data }">{{ formatearFecha(data.fechaEntrega) }} <small v-if="avisoEntrega(data.fechaEntrega)" class="fecha-relativa">{{ avisoEntrega(data.fechaEntrega) }}</small></template>
        </Column>
        <Column field="cantidadProductos" header="Productos" header-class="numero" body-class="numero" />
        <Column header="Estado"><template #body="{ data }"><EstadoPedido :estado="data.estado" /></template></Column>
        <Column field="nombreUsuario" header="Registró" />
        <Column header="Observación" body-class="celda-observacion">
          <template #body="{ data }"><span v-if="data.observacion" class="observacion-truncada" :title="data.observacion">{{ data.observacion }}</span><span v-else class="texto-suave">—</span></template>
        </Column>
        <template #empty>
          <div v-if="!cargando" class="tabla-estado">
            <template v-if="error"><Message severity="error">No se pudieron cargar los pedidos.</Message><Button label="Reintentar" text @click="cargarLista" /></template>
            <template v-else-if="filtrosActivos"><p>No hay pedidos con estos filtros.</p><Button label="Limpiar filtros" text @click="limpiar" /></template>
            <template v-else><p>No hay pedidos para entregar desde hoy.</p><Button label="Ver todas las fechas" text @click="verTodas" /></template>
          </div>
        </template>
      </DataTable>
    </div>
  </section>
</template>

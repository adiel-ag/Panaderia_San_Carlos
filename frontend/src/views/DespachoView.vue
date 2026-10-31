<script setup lang="ts">
import { vEtiquetasTabla } from '../directivas/etiquetasTabla'
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import SelectButton from 'primevue/selectbutton'
import Select from 'primevue/select'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import Textarea from 'primevue/textarea'
import { ApiError } from '../api/cliente'
import { despacharPedido } from '../api/despachos'
import { formatearFecha, hoyLocal, sumarDias } from '../api/fechas'
import {
  listarPedidos, listarProductos, listarSucursales, obtenerPedido,
  type PedidoDetalle, type PedidoResumen, type Producto, type Sucursal,
} from '../api/pedidos'
import { useSesionStore } from '../stores/sesion'
import EstadoPedido from '../components/EstadoPedido.vue'
import IrPedido from '../components/IrPedido.vue'
import { abreviaturaUnidad } from './produccion'
import {
  consultaDespacho, leerFiltrosDespacho, marcaEntrega, queryFiltrosDespacho, textoResumenDespacho, validarObservacion,
  type FiltrosDespacho, type Vista,
} from './despacho'

const route = useRoute()
const router = useRouter()
const sesion = useSesionStore()
const toast = useToast()
const hoy = hoyLocal()
const filtros = computed(() => leerFiltrosDespacho(route.query))
const puedeDespachar = computed(() => sesion.rol === 'ADMINISTRADOR' || sesion.rol === 'DESPACHO')
const filtrosActivos = computed(() => filtros.value.vista !== 'listos' || filtros.value.sucursal !== null)
const vistas = [{ label: 'Listos para despachar', value: 'listos' }, { label: 'Despachados esta semana', value: 'despachados' }]
const sucursales = ref<Sucursal[]>([])
const opcionesSucursales = computed(() => [{ idSucursal: null, nombre: 'Todas las sucursales' }, ...sucursales.value])
const pedidos = ref<PedidoResumen[]>([])
const total = ref(0)
const cargando = ref(true)
const error = ref(false)
const resumen = ref<string | null>(null)
const seccion = ref<HTMLElement | null>(null)
const titulo = ref<HTMLElement | null>(null)
const dialogoVisible = ref(false)
const seleccionado = ref<PedidoResumen | null>(null)
const detalle = ref<PedidoDetalle[]>([])
const productos = ref<Producto[]>([])
const cargandoDetalle = ref(false)
const errorDetalle = ref(false)
const observacion = ref('')
const enviando = ref(false)
const errorDespacho = ref('')
const errorObservacion = computed(() => validarObservacion(observacion.value))
const formatoCantidad = new Intl.NumberFormat('es-GT', { maximumFractionDigits: 3 })
let versionLista = 0
let versionResumen = 0
let versionDetalle = 0

function actualizar(cambio: Partial<FiltrosDespacho>): void {
  void router.replace({ name: 'despacho', query: queryFiltrosDespacho({ ...filtros.value, ...cambio, pagina: 1 }) })
}
function cambiarVista(valor: Vista | null): void { if (valor) actualizar({ vista: valor }) }
function cambiarSucursal(valor: number | null): void { actualizar({ sucursal: valor }) }
function limpiar(): void { void router.replace({ name: 'despacho', query: {} }) }
function cambiarPagina(evento: { page: number }): void {
  void router.replace({ name: 'despacho', query: queryFiltrosDespacho({ ...filtros.value, pagina: evento.page + 1 }) })
}
function destino(pedido: PedidoResumen) {
  return { name: 'pedido', params: { id: pedido.idPedido }, query: { volver: route.fullPath } }
}
function abrirFila(evento: { data: PedidoResumen; originalEvent: Event }): void {
  if ((evento.originalEvent.target as Element).closest('a, button')) return
  void router.push(destino(evento.data))
}
// Los despachados ya salieron: no se marcan como atrasados.
function marca(pedido: PedidoResumen) {
  return filtros.value.vista === 'listos' ? marcaEntrega(pedido.fechaEntrega, hoy) : null
}
function unidad(idProducto: number): string {
  return abreviaturaUnidad(productos.value.find((producto) => producto.idProducto === idProducto)?.abreviaturaUnidadMedida)
}

// Al recargar tras despachar se conservan los datos a la vista.
async function cargarLista(silencioso = false): Promise<void> {
  const version = ++versionLista
  if (!silencioso) cargando.value = true
  error.value = false
  try {
    const resultado = await listarPedidos(consultaDespacho(filtros.value, hoy), filtros.value.pagina, 20)
    if (version !== versionLista) return
    const ultimaPagina = Math.max(1, Math.ceil(resultado.total / 20))
    if (filtros.value.pagina > ultimaPagina) {
      void router.replace({ name: 'despacho', query: queryFiltrosDespacho({ ...filtros.value, pagina: ultimaPagina }) })
      return
    }
    pedidos.value = resultado.items
    total.value = resultado.total
  } catch {
    if (version !== versionLista || (silencioso && pedidos.value.length)) return
    pedidos.value = []
    total.value = 0
    error.value = true
  } finally {
    if (version === versionLista) cargando.value = false
  }
}
async function cargarResumen(silencioso = false): Promise<void> {
  const version = ++versionResumen
  if (!silencioso) resumen.value = null
  const base = { estado: 'LISTO_DESPACHO' as const, idSucursal: filtros.value.sucursal ?? undefined }
  try {
    const [listos, atrasados] = await Promise.all([
      listarPedidos(base, 1, 1, false),
      listarPedidos({ ...base, entregaHasta: sumarDias(hoy, -1) }, 1, 1, false),
    ])
    if (version !== versionResumen) return
    resumen.value = textoResumenDespacho(listos.total, atrasados.total)
  } catch { if (version === versionResumen) resumen.value = null }
}

function abrirDespacho(pedido: PedidoResumen): void {
  seleccionado.value = pedido
  observacion.value = ''
  errorDespacho.value = ''
  detalle.value = []
  dialogoVisible.value = true
  void cargarDetalle()
}
async function cargarDetalle(): Promise<void> {
  const pedido = seleccionado.value
  if (!pedido) return
  const version = ++versionDetalle
  cargandoDetalle.value = true
  errorDetalle.value = false
  try {
    const [resultado, catalogo] = await Promise.all([obtenerPedido(pedido.idPedido), productos.value.length ? productos.value : listarProductos()])
    if (version !== versionDetalle) return
    detalle.value = resultado.detalles
    productos.value = catalogo
  } catch { if (version === versionDetalle) errorDetalle.value = true }
  finally { if (version === versionDetalle) cargandoDetalle.value = false }
}
async function confirmar(): Promise<void> {
  const pedido = seleccionado.value
  if (enviando.value || !pedido || cargandoDetalle.value || errorDetalle.value || errorObservacion.value) return
  enviando.value = true
  errorDespacho.value = ''
  try {
    await despacharPedido(pedido.idPedido, observacion.value.trim() || null)
    dialogoVisible.value = false
    toast.add({ severity: 'success', summary: `Pedido #${pedido.idPedido} despachado`, life: 5000 })
    await Promise.all([cargarLista(true), cargarResumen(true)])
    devolverFoco()
  } catch (causa) {
    // 409 y 503 ya los avisó el cliente: el diálogo queda abierto con sus datos.
    if (causa instanceof ApiError && (causa.status === 400 || causa.status === 404)) errorDespacho.value = causa.message
  } finally { enviando.value = false }
}
// Vuelve al botón de la fila; si el pedido ya salió de la lista, al título.
function devolverFoco(): void {
  if (dialogoVisible.value) return
  const id = seleccionado.value?.idPedido
  const boton = id ? seccion.value?.querySelector<HTMLElement>(`[data-despachar="${id}"]`) : null
  ;(boton ?? titulo.value)?.focus()
}

watch(() => route.query, () => { void cargarLista() }, { immediate: true, deep: true })
watch(() => filtros.value.sucursal, () => { void cargarResumen() }, { immediate: true })
void listarSucursales().then((items) => { sucursales.value = items }).catch(() => {})
</script>

<template>
  <section ref="seccion" class="pagina pagina-pedidos pagina-despacho">
    <div class="cabecera-pedidos">
      <div><h1 ref="titulo" tabindex="-1">Despacho</h1><p v-if="resumen" class="subtitulo hallazgo-pedidos">{{ resumen }}</p></div>
      <IrPedido />
    </div>
    <div class="filtros-pedidos" aria-label="Filtros de despacho">
      <div class="filtro-entrega">
        <label>Vista</label>
        <SelectButton :model-value="filtros.vista" :options="vistas" option-label="label" option-value="value" :allow-empty="false" aria-label="Vista" @update:model-value="cambiarVista" />
      </div>
      <div class="filtro-select">
        <label for="sucursal-despacho">Sucursal</label>
        <Select input-id="sucursal-despacho" :model-value="filtros.sucursal" :options="opcionesSucursales" option-label="nombre" option-value="idSucursal" placeholder="Todas las sucursales" @update:model-value="cambiarSucursal" />
      </div>
      <Button v-if="filtrosActivos" label="Limpiar filtros" text class="limpiar-filtros" @click="limpiar" />
    </div>
    <div v-etiquetas-tabla class="tabla-pedidos tabla-adaptable tabla-despacho">
      <DataTable :value="pedidos" lazy paginator :rows="20" :first="(filtros.pagina - 1) * 20" :total-records="total" :loading="cargando" data-key="idPedido" @page="cambiarPagina" @row-click="abrirFila">
        <Column header="N.º" body-class="identificador">
          <template #body="{ data }"><RouterLink :to="destino(data)" class="enlace-pedido">#{{ data.idPedido }}</RouterLink></template>
        </Column>
        <Column field="nombreSucursal" header="Sucursal" />
        <Column header="Entrega">
          <template #body="{ data }">{{ formatearFecha(data.fechaEntrega) }}
            <small v-if="marca(data) === 'atrasado'" class="marca-atrasado">Atrasado</small>
            <small v-else-if="marca(data)" class="fecha-relativa">{{ marca(data) === 'hoy' ? 'Hoy' : 'Mañana' }}</small>
          </template>
        </Column>
        <Column field="cantidadProductos" header="Productos" header-class="numero" body-class="numero" />
        <Column header="Observación" body-class="celda-observacion">
          <template #body="{ data }"><span v-if="data.observacion" class="observacion-truncada" :title="data.observacion">{{ data.observacion }}</span><span v-else class="texto-suave">—</span></template>
        </Column>
        <Column v-if="filtros.vista === 'listos' && puedeDespachar" header="Acción" body-class="celda-accion">
          <template #body="{ data }"><Button label="Despachar" icon="pi pi-truck" size="small" :aria-label="`Despachar pedido #${data.idPedido}`" :data-despachar="data.idPedido" @click.stop="abrirDespacho(data)" /></template>
        </Column>
        <Column v-if="filtros.vista === 'despachados'" header="Estado"><template #body="{ data }"><EstadoPedido :estado="data.estado" /></template></Column>
        <template #empty>
          <div v-if="!cargando" class="tabla-estado">
            <template v-if="error"><Message severity="error">No se pudieron cargar los pedidos.</Message><Button label="Reintentar" text @click="cargarLista()" /></template>
            <p v-else-if="filtros.vista === 'despachados'">No se despacharon pedidos esta semana.</p>
            <template v-else><p>No hay pedidos listos para despachar.</p><Button v-if="filtrosActivos" label="Limpiar filtros" text @click="limpiar" /></template>
          </div>
        </template>
      </DataTable>
    </div>

    <Dialog v-model:visible="dialogoVisible" modal :header="`Despachar pedido #${seleccionado?.idPedido ?? ''}`" class="dialogo-produccion dialogo-despacho" :closable="!enviando" @after-hide="devolverFoco">
      <form class="formulario-dialogo" @submit.prevent="confirmar">
        <Message v-if="errorDespacho" severity="error" aria-live="polite">{{ errorDespacho }}</Message>
        <dl v-if="seleccionado" class="datos-despacho">
          <div><dt>Sucursal</dt><dd>{{ seleccionado.nombreSucursal }}</dd></div>
          <div><dt>Entrega</dt><dd>{{ formatearFecha(seleccionado.fechaEntrega) }}</dd></div>
        </dl>
        <p v-if="cargandoDetalle" class="texto-suave">Cargando productos del pedido…</p>
        <div v-else-if="errorDetalle" class="aviso-unidades"><Message severity="error">No se pudieron cargar los productos del pedido.</Message><Button label="Reintentar" text type="button" @click="cargarDetalle" /></div>
        <div v-else class="tabla-detalle-contenedor"><table class="tabla-detalle tabla-celdas-fijas tabla-productos-despacho">
          <thead><tr><th>Producto</th><th class="numero">Cantidad</th></tr></thead>
          <tbody><tr v-for="linea in detalle" :key="linea.idPedidoDetalle">
            <td>{{ linea.nombreProducto }}</td>
            <td class="numero">{{ formatoCantidad.format(linea.cantidad) }}<span v-if="unidad(linea.idProducto)" class="unidad">{{ unidad(linea.idProducto) }}</span></td>
          </tr></tbody>
        </table></div>
        <p class="texto-suave">Se descontará del inventario de producto terminado.</p>
        <div class="campo">
          <label for="observacion-despacho">Observación <span class="texto-suave">(opcional)</span></label>
          <Textarea id="observacion-despacho" v-model="observacion" autofocus auto-resize :maxlength="500" rows="3" fluid />
          <small class="contador-observacion">{{ observacion.length }}/500</small>
          <small v-if="errorObservacion" class="error-campo">{{ errorObservacion }}</small>
        </div>
        <div class="acciones-dialogo">
          <Button label="Cancelar" text type="button" :disabled="enviando" @click="dialogoVisible = false" />
          <Button label="Confirmar despacho" type="submit" :loading="enviando" :disabled="enviando || cargandoDetalle || errorDetalle || errorObservacion !== null" />
        </div>
      </form>
    </Dialog>
  </section>
</template>

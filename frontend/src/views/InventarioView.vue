<script setup lang="ts">
import { vEtiquetasTabla } from '../directivas/etiquetasTabla'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import SelectButton from 'primevue/selectbutton'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Checkbox from 'primevue/checkbox'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import Textarea from 'primevue/textarea'
import { ApiError } from '../api/cliente'
import { formatearFechaHora } from '../api/fechas'
import { listarExistencias, listarMovimientos, registrarMovimiento, type ExistenciaResumen, type MovimientoInventario, type TipoMovimiento, type TipoProducto } from '../api/inventario'
import { useSesionStore } from '../stores/sesion'
import { abreviaturaUnidad } from './produccion'
import {
  LARGO_BUSQUEDA, cambiarFiltros, consultaInventario, estadoExistencia, etiquetaTipo, existenciaResultante, filtrosPorDefecto,
  formatearCantidad, leerFiltrosInventario, normalizarBusqueda, queryFiltrosInventario, textoResumenInventario, validarMovimiento,
  type FiltrosInventario,
} from './inventario'

const TAMANO = 20
const route = useRoute()
const router = useRouter()
const sesion = useSesionStore()
const toast = useToast()
const filtros = computed(() => leerFiltrosInventario(route.query))
const puedeMover = computed(() => sesion.rol === 'ADMINISTRADOR' || sesion.rol === 'BODEGA')
const puedeVerProduccion = computed(() => ['ADMINISTRADOR', 'GERENTE', 'PRODUCCION'].includes(sesion.rol ?? ''))
const puedeVerPedido = computed(() => ['ADMINISTRADOR', 'GERENTE', 'PRODUCCION', 'DESPACHO'].includes(sesion.rol ?? ''))
const filtrosActivos = computed(() => !filtrosPorDefecto(filtros.value))
const tipos = [
  { label: 'Todos', value: 'todos' }, { label: 'Insumos', value: 'INSUMO' }, { label: 'Productos finales', value: 'PRODUCTO_FINAL' },
]
const items = ref<ExistenciaResumen[]>([])
const total = ref(0)
const cargando = ref(true)
const error = ref(false)
const bajoMinimo = ref<number | null>(null)
const busqueda = ref(filtros.value.nombre)
const seccion = ref<HTMLElement | null>(null)
const titulo = ref<HTMLElement | null>(null)
let versionLista = 0
let versionResumen = 0
let temporizador: ReturnType<typeof setTimeout> | undefined

// Diálogo de movimiento.
const dialogoVisible = ref(false)
const tipoMovimiento = ref<TipoMovimiento>('entrada')
const seleccionado = ref<ExistenciaResumen | null>(null)
const cantidad = ref<number | null>(null)
const observacion = ref('')
const tocado = ref(false)
const intentado = ref(false)
const enviando = ref(false)
const errorMovimiento = ref('')
const unidadSeleccionada = computed(() => abreviaturaUnidad(seleccionado.value?.unidad))
const actual = computed(() => seleccionado.value?.existencia ?? null)
const errores = computed(() => validarMovimiento(tipoMovimiento.value, actual.value, cantidad.value, observacion.value, unidadSeleccionada.value))
const resultante = computed(() => cantidad.value !== null && cantidad.value > 0 ? existenciaResultante(tipoMovimiento.value, actual.value, cantidad.value) : null)
const excede = computed(() => resultante.value !== null && resultante.value < 0)
const verErrorCantidad = computed(() => (tocado.value || intentado.value) && errores.value.cantidad !== null)
const nombreAccion = computed(() => tipoMovimiento.value === 'entrada' ? 'Registrar entrada' : 'Registrar salida')
const historialVisible = ref(false)
const productoHistorial = ref<ExistenciaResumen | null>(null)
const movimientos = ref<MovimientoInventario[]>([])
const totalMovimientos = ref(0)
const paginaMovimientos = ref(1)
const cargandoMovimientos = ref(false)
const errorHistorial = ref(false)
let versionHistorial = 0

function etiquetaMovimiento(tipo: MovimientoInventario['tipo']): string {
  return { ENTRADA: 'Entrada', SALIDA: 'Salida', AJUSTE_POS: 'Ajuste +', AJUSTE_NEG: 'Ajuste −' }[tipo]
}
function signoMovimiento(tipo: MovimientoInventario['tipo']): string {
  return tipo === 'SALIDA' || tipo === 'AJUSTE_NEG' ? '−' : '+'
}
function etiquetaOrigen(item: MovimientoInventario): string {
  if (item.origen === 'PRODUCCION') return `Producción #${item.idReferencia}`
  if (item.origen === 'DESPACHO') return item.idPedido ? `Despacho del pedido #${item.idPedido}` : `Despacho #${item.idReferencia}`
  return { AJUSTE: 'Ajuste', INICIAL: 'Inicial', OTRO: 'Otro' }[item.origen]
}
async function cargarMovimientos(): Promise<void> {
  const producto = productoHistorial.value
  if (!producto) return
  const actual = ++versionHistorial
  cargandoMovimientos.value = true
  errorHistorial.value = false
  try {
    const resultado = await listarMovimientos(producto.idProducto, paginaMovimientos.value, TAMANO)
    if (actual !== versionHistorial) return
    movimientos.value = resultado.items
    totalMovimientos.value = resultado.total
  } catch {
    if (actual === versionHistorial) { movimientos.value = []; errorHistorial.value = true }
  } finally { if (actual === versionHistorial) cargandoMovimientos.value = false }
}
function abrirHistorial(item: ExistenciaResumen): void {
  productoHistorial.value = item
  movimientos.value = []
  totalMovimientos.value = 0
  paginaMovimientos.value = 1
  historialVisible.value = true
  void cargarMovimientos()
}
function cambiarPaginaMovimientos(evento: { page: number }): void {
  paginaMovimientos.value = evento.page + 1
  void cargarMovimientos()
}

function navegar(nuevos: FiltrosInventario): void {
  void router.replace({ name: 'inventario', query: queryFiltrosInventario(nuevos) })
}
function actualizar(cambio: Partial<Omit<FiltrosInventario, 'pagina'>>): void { navegar(cambiarFiltros(filtros.value, cambio)) }
function cambiarTipo(valor: TipoProducto | 'todos' | null): void {
  if (valor) actualizar({ tipo: valor === 'todos' ? null : valor })
}
function cambiarBajoMinimo(valor: boolean): void { actualizar({ bajoMinimo: valor }) }
function limpiar(): void {
  cancelarBusqueda()
  busqueda.value = ''
  void router.replace({ name: 'inventario', query: {} })
}
function cambiarPagina(evento: { page: number }): void { navegar({ ...filtros.value, pagina: evento.page + 1 }) }

function cancelarBusqueda(): void {
  if (temporizador !== undefined) clearTimeout(temporizador)
  temporizador = undefined
}
function aplicarBusqueda(): void {
  cancelarBusqueda()
  const nombre = normalizarBusqueda(busqueda.value)
  if (nombre !== filtros.value.nombre) actualizar({ nombre })
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

function unidad(item: ExistenciaResumen): string { return abreviaturaUnidad(item.unidad) }
function sinExistencias(item: ExistenciaResumen): boolean { return item.existencia === null || item.existencia <= 0 }

// Al recargar tras un movimiento se conservan los datos a la vista.
async function cargarLista(silencioso = false): Promise<void> {
  const version = ++versionLista
  if (!silencioso) cargando.value = true
  error.value = false
  try {
    const resultado = await listarExistencias(consultaInventario(filtros.value), filtros.value.pagina, TAMANO)
    if (version !== versionLista) return
    const ultimaPagina = Math.max(1, Math.ceil(resultado.total / TAMANO))
    if (filtros.value.pagina > ultimaPagina) {
      navegar({ ...filtros.value, pagina: ultimaPagina })
      return
    }
    items.value = resultado.items
    total.value = resultado.total
  } catch {
    if (version !== versionLista || (silencioso && items.value.length)) return
    items.value = []
    total.value = 0
    error.value = true
  } finally {
    if (version === versionLista) cargando.value = false
  }
}
async function cargarResumen(silencioso = false): Promise<void> {
  const version = ++versionResumen
  if (!silencioso) bajoMinimo.value = null
  try {
    const resultado = await listarExistencias({ tipo: filtros.value.tipo ?? undefined, bajoMinimo: true }, 1, 1, false)
    if (version === versionResumen) bajoMinimo.value = resultado.total
  } catch { if (version === versionResumen) bajoMinimo.value = null }
}

function abrirMovimiento(tipo: TipoMovimiento, item: ExistenciaResumen): void {
  tipoMovimiento.value = tipo
  seleccionado.value = item
  cantidad.value = null
  observacion.value = ''
  tocado.value = false
  intentado.value = false
  errorMovimiento.value = ''
  dialogoVisible.value = true
}
async function confirmar(): Promise<void> {
  const item = seleccionado.value
  if (enviando.value || !item) return
  intentado.value = true
  if (errores.value.cantidad || errores.value.observacion || cantidad.value === null) return
  const tipo = tipoMovimiento.value
  enviando.value = true
  errorMovimiento.value = ''
  try {
    const resultado = await registrarMovimiento(tipo, item.idProducto, cantidad.value, observacion.value.trim() || null)
    dialogoVisible.value = false
    // La existencia que devuelve el servidor se aplica ya: si la recarga falla, la fila no queda vieja.
    items.value = items.value.map((fila) => fila.idProducto === item.idProducto
      ? { ...fila, existencia: resultado.existencia, bajoMinimo: resultado.existencia < fila.stockMinimo }
      : fila)
    toast.add({
      severity: 'success', life: 5000,
      summary: `${tipo === 'entrada' ? 'Entrada registrada' : 'Salida registrada'} · existencia: ${formatearCantidad(resultado.existencia, unidad(item))}`,
    })
    await Promise.all([cargarLista(true), cargarResumen(true)])
    devolverFoco()
  } catch (causa) {
    // 409 y 503 ya los avisó el cliente: el diálogo queda abierto con sus datos.
    if (causa instanceof ApiError && (causa.status === 400 || causa.status === 404)) errorMovimiento.value = causa.message
  } finally { enviando.value = false }
}
// Vuelve al botón de la fila; si el producto ya no está en la lista, al título.
function devolverFoco(): void {
  if (dialogoVisible.value || !seleccionado.value) return
  const selector = `[data-movimiento="${tipoMovimiento.value}-${seleccionado.value.idProducto}"]`
  const boton = seccion.value?.querySelector<HTMLButtonElement>(selector)
  ;(boton && !boton.disabled ? boton : titulo.value)?.focus()
}

watch(() => route.query, () => { void cargarLista() }, { immediate: true, deep: true })
watch(() => filtros.value.tipo, () => { void cargarResumen() }, { immediate: true })
// Si la búsqueda cambia desde la URL (atrás, limpiar), el campo la sigue.
watch(() => filtros.value.nombre, (nombre) => {
  if (normalizarBusqueda(busqueda.value) !== nombre) { cancelarBusqueda(); busqueda.value = nombre }
})
onBeforeUnmount(cancelarBusqueda)
</script>

<template>
  <section ref="seccion" class="pagina pagina-pedidos pagina-inventario">
    <div class="cabecera-pedidos">
      <div>
        <h1 ref="titulo" tabindex="-1">Inventario</h1>
        <p v-if="bajoMinimo !== null" class="subtitulo hallazgo-pedidos resumen-inventario">
          <span>{{ textoResumenInventario(bajoMinimo) }}</span>
          <Button v-if="bajoMinimo > 0 && !filtros.bajoMinimo" label="Ver solo bajo el mínimo" text class="ver-bajo-minimo" @click="cambiarBajoMinimo(true)" />
        </p>
      </div>
    </div>
    <div class="filtros-pedidos" aria-label="Filtros de inventario">
      <div class="filtro-entrega">
        <label>Tipo</label>
        <SelectButton :model-value="filtros.tipo ?? 'todos'" :options="tipos" option-label="label" option-value="value" :allow-empty="false" aria-label="Tipo" @update:model-value="cambiarTipo" />
      </div>
      <div class="filtro-select filtro-busqueda">
        <label for="buscar-producto">Buscar producto</label>
        <div class="control-busqueda">
          <InputText id="buscar-producto" :model-value="busqueda" :maxlength="LARGO_BUSQUEDA" autocomplete="off" enterkeyhint="search" @update:model-value="escribirBusqueda" @keydown.enter.prevent="aplicarBusqueda" />
          <Button v-if="busqueda" icon="pi pi-times" text rounded aria-label="Borrar búsqueda" class="borrar-busqueda" @click="borrarBusqueda" />
        </div>
      </div>
      <div class="filtro-bajo-minimo">
        <Checkbox input-id="solo-bajo-minimo" :model-value="filtros.bajoMinimo" binary @update:model-value="cambiarBajoMinimo" />
        <label for="solo-bajo-minimo">Solo bajo el mínimo</label>
      </div>
      <Button v-if="filtrosActivos" label="Limpiar filtros" text class="limpiar-filtros" @click="limpiar" />
    </div>
    <div v-etiquetas-tabla class="tabla-pedidos tabla-adaptable tabla-inventario" :class="{ 'tabla-inventario--acciones': puedeMover }">
      <DataTable :value="items" lazy paginator :rows="TAMANO" :first="(filtros.pagina - 1) * TAMANO" :total-records="total" :loading="cargando" data-key="idProducto" @page="cambiarPagina">
        <Column field="nombre" header="Producto" />
        <Column header="Tipo"><template #body="{ data }">{{ etiquetaTipo(data.tipo) }}</template></Column>
        <Column header="Existencia" header-class="numero" body-class="numero">
          <template #body="{ data }">
            <span v-if="data.existencia === null" class="texto-suave"><span aria-hidden="true">— </span>Sin registro</span>
            <template v-else>{{ formatearCantidad(data.existencia) }}<span v-if="unidad(data)" class="unidad">{{ unidad(data) }}</span></template>
          </template>
        </Column>
        <Column header="Mínimo" header-class="numero" body-class="numero">
          <template #body="{ data }">{{ formatearCantidad(data.stockMinimo) }}<span v-if="unidad(data)" class="unidad">{{ unidad(data) }}</span></template>
        </Column>
        <Column header="Estado">
          <template #body="{ data }">
            <span v-if="estadoExistencia(data) === 'bajo-minimo'" class="estado-inventario estado-inventario--bajo">Bajo el mínimo</span>
            <span v-else-if="estadoExistencia(data) === 'sin-inventario'" class="estado-inventario estado-inventario--sin">Sin inventario</span>
          </template>
        </Column>
        <Column header="Movimientos" body-class="celda-accion">
          <template #body="{ data }">
            <div class="acciones-inventario">
              <Button label="Movimientos" icon="pi pi-history" text size="small" :aria-label="`Ver movimientos de ${data.nombre}`" @click="abrirHistorial(data)" />
              <Button v-if="puedeMover" label="Entrada" icon="pi pi-plus" size="small" outlined :aria-label="`Registrar entrada de ${data.nombre}`" :data-movimiento="`entrada-${data.idProducto}`" @click="abrirMovimiento('entrada', data)" />
              <span v-if="puedeMover" :title="sinExistencias(data) ? 'No hay existencias para registrar una salida' : undefined">
                <Button label="Salida" icon="pi pi-minus" size="small" outlined :disabled="sinExistencias(data)" :aria-label="`Registrar salida de ${data.nombre}`" :data-movimiento="`salida-${data.idProducto}`" @click="abrirMovimiento('salida', data)" />
              </span>
            </div>
          </template>
        </Column>
        <template #empty>
          <div v-if="!cargando" class="tabla-estado">
            <template v-if="error"><Message severity="error">No se pudo cargar el inventario.</Message><Button label="Reintentar" text @click="cargarLista()" /></template>
            <template v-else-if="filtrosActivos"><p>No hay productos con estos filtros.</p><Button label="Limpiar filtros" text @click="limpiar" /></template>
            <p v-else>No hay productos activos.</p>
          </div>
        </template>
      </DataTable>
    </div>

    <Dialog v-model:visible="dialogoVisible" modal :header="nombreAccion" class="dialogo-produccion dialogo-movimiento" :closable="!enviando" @after-hide="devolverFoco">
      <form class="formulario-dialogo" novalidate @submit.prevent="confirmar">
        <p v-if="seleccionado" class="producto-movimiento">{{ seleccionado.nombre }}</p>
        <Message v-if="errorMovimiento" severity="error" aria-live="polite">{{ errorMovimiento }}</Message>
        <p class="existencia-actual">
          <template v-if="actual !== null">Existencia actual: <strong class="cifra">{{ formatearCantidad(actual, unidadSeleccionada) }}</strong></template>
          <span v-else class="texto-suave">Sin inventario registrado</span>
        </p>
        <div class="campo">
          <label for="cantidad-movimiento">Cantidad</label>
          <InputNumber v-model="cantidad" input-id="cantidad-movimiento" locale="es-GT" :min="0.001" :max="999999999.999" :max-fraction-digits="3"
            :suffix="unidadSeleccionada ? ` ${unidadSeleccionada}` : undefined" :invalid="verErrorCantidad" :disabled="enviando" fluid
            :pt="{ pcInputText: { root: { autofocus: true, 'aria-describedby': 'quedara-movimiento' } } }" @blur="tocado = true" />
          <small v-if="verErrorCantidad" class="error-campo">{{ errores.cantidad }}</small>
          <p id="quedara-movimiento" class="quedara-movimiento" :class="{ 'quedara-movimiento--excede': excede }" aria-live="polite">
            <template v-if="resultante !== null">Quedará: {{ formatearCantidad(resultante, unidadSeleccionada) }}</template>
          </p>
        </div>
        <div class="campo">
          <label for="observacion-movimiento">Observación <span class="texto-suave">(opcional)</span></label>
          <Textarea id="observacion-movimiento" v-model="observacion" auto-resize :maxlength="500" rows="3" fluid :disabled="enviando" />
          <small class="contador-observacion">{{ observacion.length }}/500</small>
          <small v-if="errores.observacion" class="error-campo">{{ errores.observacion }}</small>
        </div>
        <div class="acciones-dialogo">
          <Button label="Cancelar" text type="button" :disabled="enviando" @click="dialogoVisible = false" />
          <Button :label="nombreAccion" type="submit" :loading="enviando" :disabled="enviando" />
        </div>
      </form>
    </Dialog>
    <Dialog v-model:visible="historialVisible" modal :header="`Movimientos · ${productoHistorial?.nombre ?? ''}`" class="dialogo-historial">
      <div v-if="errorHistorial" class="aviso-unidades"><Message severity="error">No se pudieron cargar los movimientos.</Message><Button label="Reintentar" text @click="cargarMovimientos" /></div>
      <DataTable v-else :value="movimientos" lazy paginator :rows="TAMANO" :first="(paginaMovimientos - 1) * TAMANO" :total-records="totalMovimientos" :loading="cargandoMovimientos" data-key="idMovimiento" @page="cambiarPaginaMovimientos">
        <Column header="Fecha"><template #body="{ data }">{{ formatearFechaHora(data.fecha) }}</template></Column>
        <Column header="Tipo"><template #body="{ data }"><span class="tipo-movimiento" :class="{ 'tipo-movimiento--salida': signoMovimiento(data.tipo) === '−' }">{{ etiquetaMovimiento(data.tipo) }}</span></template></Column>
        <Column header="Cantidad" header-class="numero" body-class="numero"><template #body="{ data }">{{ signoMovimiento(data.tipo) }}{{ formatearCantidad(data.cantidad) }}<span class="unidad">{{ productoHistorial ? unidad(productoHistorial) : '' }}</span></template></Column>
        <Column header="Origen"><template #body="{ data }">
          <RouterLink v-if="data.origen === 'PRODUCCION' && data.idReferencia && puedeVerProduccion" :to="{ name: 'produccion-detalle', params: { id: data.idReferencia } }">{{ etiquetaOrigen(data) }}</RouterLink>
          <RouterLink v-else-if="data.origen === 'DESPACHO' && data.idPedido && puedeVerPedido" :to="{ name: 'pedido', params: { id: data.idPedido } }">{{ etiquetaOrigen(data) }}</RouterLink>
          <span v-else>{{ etiquetaOrigen(data) }}</span>
        </template></Column>
        <Column header="Usuario"><template #body="{ data }">{{ data.usuario ?? '—' }}</template></Column>
        <Column header="Observación"><template #body="{ data }">{{ data.observacion ?? '—' }}</template></Column>
        <template #empty><p v-if="!cargandoMovimientos">Este producto aún no tiene movimientos.</p></template>
      </DataTable>
    </Dialog>
  </section>
</template>

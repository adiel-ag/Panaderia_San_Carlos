<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputNumber from 'primevue/inputnumber'
import Message from 'primevue/message'
import Select from 'primevue/select'
import { ApiError } from '../api/cliente'
import { formatearFecha, formatearFechaHora } from '../api/fechas'
import { listarPedidos, listarProductos, type PedidoResumen, type Producto } from '../api/pedidos'
import {
  asociarPedido, cancelarProduccion, etiquetaEstadoProduccion, finalizarProduccion, generarPlan, obtenerDetalleProduccion, obtenerInsumos, obtenerProduccion, registrarProducido,
  type EstadoProduccion as Estado, type InsumosProduccion, type ProduccionConPedidos, type ProduccionDetalle,
} from '../api/producciones'
import { useSesionStore } from '../stores/sesion'
import EstadoPedido from '../components/EstadoPedido.vue'
import EstadoProduccion from '../components/EstadoProduccion.vue'
import { filasPlan, pedidosDisponibles, puedeFinalizar, validarCantidad, type FilaPlan } from './produccion'

const route = useRoute()
const sesion = useSesionStore()
const toast = useToast()
const confirm = useConfirm()
const id = computed(() => Number(route.params.id))
const puedeCambiar = computed(() => sesion.rol === 'ADMINISTRADOR' || sesion.rol === 'PRODUCCION')
const volver = computed(() => {
  const valor = route.query.volver
  return typeof valor === 'string' && /^\/produccion(?:\?|$)/.test(valor) ? valor : '/produccion'
})
const ciclo: Estado[] = ['PLANIFICADA', 'EN_PROCESO', 'FINALIZADA']
const produccion = ref<ProduccionConPedidos | null>(null)
const detalle = ref<ProduccionDetalle[]>([])
const insumos = ref<InsumosProduccion | null>(null)
const cargandoInsumos = ref(false)
const errorInsumos = ref(false)
const productos = ref<Producto[]>([])
const cargando = ref(true)
const error = ref<'no-encontrado' | 'general' | null>(null)
const errorPlan = ref(false)
const accion = ref<'plan' | 'finalizar' | 'cancelar' | null>(null)
const borradores = reactive<Record<number, number | null>>({})
const guardando = reactive<Record<number, boolean>>({})
const dialogoPedido = ref(false)
const confirmados = ref<PedidoResumen[]>([])
const cargandoConfirmados = ref(false)
const errorConfirmados = ref(false)
const pedidoElegido = ref<number | null>(null)
const agregando = ref(false)
const errorAgregar = ref('')
const formatoCantidad = new Intl.NumberFormat('es-GT', { maximumFractionDigits: 3 })
let version = 0

const estado = computed(() => produccion.value?.estado ?? null)
const puedeCancelar = computed(() => puedeCambiar.value && estado.value === 'PLANIFICADA')
const editable = computed(() => puedeCambiar.value && estado.value === 'EN_PROCESO')
const conPlan = computed(() => estado.value === 'EN_PROCESO' || estado.value === 'FINALIZADA')
const mostrarInsumos = computed(() => estado.value === 'EN_PROCESO' ||
  (estado.value === 'PLANIFICADA' && !!produccion.value?.pedidos.length))
const filas = computed(() => filasPlan(detalle.value, productos.value))
const completos = computed(() => filas.value.filter((fila) => fila.completo).length)
const listoParaFinalizar = computed(() => puedeFinalizar(filas.value))
const opcionesPedido = computed(() => pedidosDisponibles(confirmados.value, produccion.value?.pedidos ?? [])
  .map((pedido) => ({ value: pedido.idPedido, label: `#${pedido.idPedido} · ${pedido.nombreSucursal} · entrega ${formatearFecha(pedido.fechaEntrega)}` })))

function formatear(valor: number): string { return formatoCantidad.format(valor) }
function sinCambios(fila: FilaPlan): boolean {
  const borrador = borradores[fila.idProducto] ?? null
  return borrador === null ? fila.producido === 0 : Math.round(borrador * 1000) === Math.round(fila.producido * 1000)
}
function puedeGuardar(fila: FilaPlan): boolean {
  return !guardando[fila.idProducto] && !sinCambios(fila) && validarCantidad(borradores[fila.idProducto] ?? null) === null
}
function prepararBorradores(items: ProduccionDetalle[]): void {
  for (const clave of Object.keys(borradores)) delete borradores[Number(clave)]
  for (const linea of items) borradores[linea.idProducto] = linea.cantidadProducida > 0 ? linea.cantidadProducida : null
}

async function cargarPlan(actual: number): Promise<void> {
  errorPlan.value = false
  try {
    const [items, catalogo] = await Promise.all([obtenerDetalleProduccion(id.value), listarProductos()])
    if (actual !== version) return
    detalle.value = items
    productos.value = catalogo
    prepararBorradores(items)
  } catch { if (actual === version) errorPlan.value = true }
}
async function cargarInsumos(actual = version): Promise<void> {
  cargandoInsumos.value = true
  errorInsumos.value = false
  try {
    const resultado = await obtenerInsumos(id.value)
    if (actual === version) insumos.value = resultado
  } catch {
    if (actual === version) { insumos.value = null; errorInsumos.value = true }
  } finally { if (actual === version) cargandoInsumos.value = false }
}
// Al recargar tras una acción se conservan los datos a la vista.
async function cargar(silencioso = false): Promise<void> {
  const actual = ++version
  if (!silencioso) {
    cargando.value = true
    produccion.value = null
    detalle.value = []
  }
  error.value = null
  try {
    const resultado = await obtenerProduccion(id.value)
    if (actual !== version) return
    produccion.value = resultado
    if (resultado.estado === 'EN_PROCESO' || resultado.estado === 'FINALIZADA') await cargarPlan(actual)
    else detalle.value = []
    if (actual === version) {
      if (resultado.estado === 'EN_PROCESO' || (resultado.estado === 'PLANIFICADA' && resultado.pedidos.length)) await cargarInsumos(actual)
      else insumos.value = null
    }
  } catch (causa) {
    if (actual !== version) return
    if (causa instanceof ApiError && (causa.status === 404 || causa.status === 400)) {
      produccion.value = null
      error.value = 'no-encontrado'
    } else if (!produccion.value) error.value = 'general'
  } finally {
    if (actual === version) cargando.value = false
  }
}

async function abrirAgregar(): Promise<void> {
  pedidoElegido.value = null
  errorAgregar.value = ''
  dialogoPedido.value = true
  await cargarConfirmados()
}
async function cargarConfirmados(): Promise<void> {
  cargandoConfirmados.value = true
  errorConfirmados.value = false
  try { confirmados.value = (await listarPedidos({ estado: 'CONFIRMADO' }, 1, 100)).items }
  catch { errorConfirmados.value = true }
  finally { cargandoConfirmados.value = false }
}
async function agregarPedido(): Promise<void> {
  if (agregando.value || pedidoElegido.value === null) return
  agregando.value = true
  errorAgregar.value = ''
  const idPedido = pedidoElegido.value
  try {
    await asociarPedido(id.value, idPedido)
    dialogoPedido.value = false
    toast.add({ severity: 'success', summary: `Pedido #${idPedido} agregado`, life: 5000 })
    await cargar(true)
  } catch (causa) {
    if (causa instanceof ApiError && (causa.status === 400 || causa.status === 404)) errorAgregar.value = causa.message
  } finally { agregando.value = false }
}

function pedirGenerarPlan(): void {
  if (accion.value) return
  confirm.require({
    header: '¿Generar el plan de producción?', message: 'Los pedidos pasarán a En producción y ya no se podrán agregar más.',
    acceptLabel: 'Generar plan', rejectLabel: 'Cancelar', accept: () => { void ejecutarGenerarPlan() },
  })
}
async function ejecutarGenerarPlan(): Promise<void> {
  if (accion.value) return
  accion.value = 'plan'
  try {
    await generarPlan(id.value)
    toast.add({ severity: 'success', summary: 'Plan de producción generado', life: 5000 })
    await cargar(true)
  } catch { /* el cliente ya avisó */ }
  finally { accion.value = null }
}

async function guardarCantidad(fila: FilaPlan): Promise<void> {
  const cantidad = borradores[fila.idProducto] ?? null
  if (!puedeGuardar(fila) || cantidad === null) return
  const actual = version
  guardando[fila.idProducto] = true
  try {
    await registrarProducido(id.value, fila.idProducto, cantidad)
    if (actual !== version) return
    detalle.value = detalle.value.map((linea) => linea.idProducto === fila.idProducto ? { ...linea, cantidadProducida: cantidad } : linea)
    await cargarInsumos(actual)
    toast.add({ severity: 'success', summary: 'Cantidad registrada', life: 4000 })
  } catch (causa) {
    if (causa instanceof ApiError && causa.status === 400) toast.add({ severity: 'warn', summary: 'Cantidad no válida', detail: causa.message, life: 5000 })
  } finally { guardando[fila.idProducto] = false }
}

function pedirFinalizar(): void {
  if (accion.value || !listoParaFinalizar.value) return
  confirm.require({
    header: '¿Finalizar la producción?', message: 'Se descontarán los insumos del inventario y los pedidos pasarán a Listo para despacho.',
    acceptLabel: 'Finalizar', rejectLabel: 'Cancelar', accept: () => { void ejecutarFinalizar() },
  })
}
async function ejecutarFinalizar(): Promise<void> {
  if (accion.value) return
  accion.value = 'finalizar'
  try {
    await finalizarProduccion(id.value)
    toast.add({ severity: 'success', summary: 'Producción finalizada', life: 5000 })
    await cargar(true)
  } catch { /* el cliente ya avisó */ }
  finally { accion.value = null }
}

// Solo una producción planificada; sus pedidos siguen confirmados y quedan libres.
function pedirCancelar(): void {
  if (accion.value) return
  confirm.require({
    header: `¿Cancelar la producción #${id.value}?`, message: 'Se cancelará y sus pedidos quedarán libres para otra producción.',
    acceptLabel: 'Cancelar producción', rejectLabel: 'Volver',
    acceptProps: { severity: 'danger', outlined: true }, rejectProps: { text: true },
    accept: () => { void ejecutarCancelar() },
  })
}
async function ejecutarCancelar(): Promise<void> {
  if (accion.value) return
  accion.value = 'cancelar'
  const idProduccion = id.value
  try {
    await cancelarProduccion(idProduccion)
    toast.add({ severity: 'success', summary: `Producción #${idProduccion} cancelada`, life: 5000 })
    await cargar(true)
  } catch { /* el cliente ya avisó */ }
  finally { accion.value = null }
}

watch(id, () => { void cargar() }, { immediate: true })
</script>

<template>
  <section class="pagina pagina-pedido-detalle pagina-produccion-detalle">
    <RouterLink :to="volver" class="volver-pedidos">← Producción</RouterLink>
    <div class="titulo-pedido"><h1>Producción #{{ id }}</h1><EstadoProduccion v-if="produccion" :estado="produccion.estado" />
      <Button v-if="puedeCancelar" label="Cancelar producción" icon="pi pi-times" severity="danger" outlined :loading="accion === 'cancelar'" :disabled="accion !== null" @click="pedirCancelar" />
    </div>
    <div v-if="cargando" class="detalle-cargando" aria-label="Cargando producción">
      <div class="esqueleto-fila" /><div class="esqueleto-fila" /><div class="esqueleto-fila" />
    </div>
    <div v-else-if="error === 'no-encontrado'" class="detalle-aviso">
      <Message severity="warn">No se encontró la producción #{{ id }}.</Message>
      <RouterLink :to="volver" class="volver-pedidos">Volver a Producción</RouterLink>
    </div>
    <div v-else-if="error" class="detalle-aviso"><Message severity="error">No se pudo cargar la producción.</Message><Button label="Reintentar" text @click="cargar()" /></div>
    <template v-else-if="produccion">
      <ol v-if="produccion.estado !== 'CANCELADA'" class="ciclo-produccion" aria-label="Avance de la producción">
        <li v-for="paso in ciclo" :key="paso" :aria-current="paso === produccion.estado ? 'step' : undefined">
          <strong v-if="paso === produccion.estado">{{ etiquetaEstadoProduccion(paso) }}</strong>
          <span v-else class="texto-suave">{{ etiquetaEstadoProduccion(paso) }}</span>
        </li>
      </ol>
      <dl class="datos-pedido">
        <div><dt>Fecha</dt><dd>{{ formatearFechaHora(produccion.fechaProduccion) }}</dd></div>
        <div><dt>Registró</dt><dd>{{ produccion.nombreUsuario }}</dd></div>
        <div><dt>Observación</dt><dd :class="{ 'texto-suave': !produccion.observacion }">{{ produccion.observacion ?? '—' }}</dd></div>
      </dl>

      <section class="seccion-pedido">
        <div class="cabecera-seccion">
          <h2>Pedidos</h2>
          <Button v-if="puedeCambiar && produccion.estado === 'PLANIFICADA'" label="Agregar pedido" icon="pi pi-plus" text @click="abrirAgregar" />
        </div>
        <p v-if="!produccion.pedidos.length" class="texto-suave">Esta producción aún no tiene pedidos.</p>
        <div v-else class="tabla-detalle-contenedor"><table class="tabla-detalle tabla-celdas-fijas">
          <thead><tr><th>N.º</th><th>Sucursal</th><th>Entrega</th><th class="numero">Productos</th><th>Estado</th></tr></thead>
          <tbody><tr v-for="pedido in produccion.pedidos" :key="pedido.idPedido">
            <td class="identificador"><RouterLink :to="{ name: 'pedido', params: { id: pedido.idPedido } }" class="enlace-pedido">#{{ pedido.idPedido }}</RouterLink></td>
            <td>{{ pedido.nombreSucursal }}</td>
            <td>{{ formatearFecha(pedido.fechaEntrega) }}</td>
            <td class="numero">{{ pedido.cantidadProductos }}</td>
            <td><EstadoPedido :estado="pedido.estado" /></td>
          </tr></tbody>
        </table></div>
        <div v-if="puedeCambiar && produccion.estado === 'PLANIFICADA'" class="acciones-produccion">
          <Button label="Generar plan" :loading="accion === 'plan'" :disabled="!produccion.pedidos.length || accion !== null" @click="pedirGenerarPlan" />
          <small v-if="!produccion.pedidos.length" class="texto-suave">Agregue al menos un pedido para generar el plan.</small>
        </div>
      </section>

      <section v-if="mostrarInsumos" class="seccion-pedido seccion-insumos">
        <h2>Insumos necesarios</h2>
        <p v-if="cargandoInsumos" class="texto-suave" role="status">Cargando insumos…</p>
        <div v-else-if="errorInsumos" class="aviso-unidades"><Message severity="error">No se pudieron cargar los insumos.</Message><Button label="Reintentar" text @click="cargarInsumos()" /></div>
        <template v-else-if="insumos">
          <Message v-if="insumos.productosSinReceta.length" severity="warn">
            Sin receta vigente: {{ insumos.productosSinReceta.map((item) => item.nombre).join(', ') }} — no se podrá generar el plan.
            <RouterLink :to="{ name: 'recetas' }">Ver Recetas</RouterLink>
          </Message>
          <Message :severity="insumos.suficiente ? 'success' : 'warn'">
            <template v-if="insumos.suficiente">Hay insumos suficientes para esta producción.</template>
            <template v-else>Faltan insumos: registre entradas en <RouterLink :to="{ name: 'inventario' }">Inventario</RouterLink> antes de finalizar.</template>
          </Message>
          <div class="tabla-detalle-contenedor"><table class="tabla-detalle tabla-celdas-fijas">
            <thead><tr><th>Insumo</th><th class="numero">Necesario</th><th class="numero">Disponible</th><th class="numero">Faltante</th></tr></thead>
            <tbody><tr v-for="item in insumos.insumos" :key="item.idInsumo">
              <td>{{ item.nombre }}</td>
              <td class="numero">{{ formatear(item.requerido) }}<span class="unidad">{{ item.unidad }}</span></td>
              <td class="numero">{{ formatear(item.disponible) }}<span class="unidad">{{ item.unidad }}</span></td>
              <td class="numero"><strong v-if="item.faltante > 0" class="insumo-faltante">{{ formatear(item.faltante) }}<span class="unidad">{{ item.unidad }}</span></strong><span v-else class="texto-suave">—</span></td>
            </tr></tbody>
          </table></div>
        </template>
      </section>

      <section v-if="conPlan" class="seccion-pedido">
        <h2>Plan de producción</h2>
        <div v-if="errorPlan" class="aviso-unidades"><Message severity="error">No se pudo cargar el plan de producción.</Message><Button label="Reintentar" text @click="cargarPlan(version)" /></div>
        <template v-else>
          <p v-if="filas.length" class="texto-suave resumen-plan">{{ completos }} de {{ filas.length }} productos completos</p>
          <div class="tabla-detalle-contenedor"><table class="tabla-detalle tabla-celdas-fijas tabla-plan">
            <thead><tr><th>Producto</th><th class="numero">Planificado</th><th class="numero">Producido</th><th class="numero">Faltante</th></tr></thead>
            <tbody><tr v-for="fila in filas" :key="fila.idProducto">
              <td>{{ fila.nombre }}</td>
              <td class="numero">{{ formatear(fila.planificado) }}<span v-if="fila.unidad" class="unidad">{{ fila.unidad }}</span></td>
              <td class="numero">
                <div v-if="editable" class="producido-control">
                  <InputNumber v-model="borradores[fila.idProducto]" locale="es-GT" :min="0.001" :max="999999999.999" :max-fraction-digits="3" :aria-label="`Cantidad producida de ${fila.nombre}`" :disabled="guardando[fila.idProducto]" />
                  <span v-if="fila.unidad" class="unidad">{{ fila.unidad }}</span>
                  <Button icon="pi pi-check" text :aria-label="`Guardar cantidad de ${fila.nombre}`" :loading="guardando[fila.idProducto]" :disabled="!puedeGuardar(fila)" @click="guardarCantidad(fila)" />
                </div>
                <template v-else>{{ formatear(fila.producido) }}<span v-if="fila.unidad" class="unidad">{{ fila.unidad }}</span></template>
              </td>
              <td class="numero"><template v-if="fila.faltante !== null">{{ formatear(fila.faltante) }}<span v-if="fila.unidad" class="unidad">{{ fila.unidad }}</span></template><span v-else class="texto-suave">—</span></td>
            </tr></tbody>
          </table></div>
          <div v-if="editable" class="acciones-produccion">
            <Button label="Finalizar producción" :loading="accion === 'finalizar'" :disabled="!listoParaFinalizar || accion !== null" @click="pedirFinalizar" />
            <small v-if="!listoParaFinalizar" class="texto-suave">Registre al menos lo planificado en cada producto.</small>
          </div>
        </template>
      </section>
    </template>

    <Dialog v-model:visible="dialogoPedido" modal header="Agregar pedido" class="dialogo-produccion" :closable="!agregando">
      <form class="formulario-dialogo" @submit.prevent="agregarPedido">
        <Message v-if="errorAgregar" severity="error" aria-live="polite">{{ errorAgregar }}</Message>
        <p v-if="cargandoConfirmados" class="texto-suave">Cargando pedidos confirmados…</p>
        <div v-else-if="errorConfirmados" class="aviso-unidades"><Message severity="error">No se pudieron cargar los pedidos confirmados.</Message><Button label="Reintentar" text type="button" @click="cargarConfirmados" /></div>
        <p v-else-if="!opcionesPedido.length" class="texto-suave">No hay pedidos confirmados para agregar.</p>
        <div v-else class="campo">
          <label for="pedido-produccion">Pedido confirmado</label>
          <Select v-model="pedidoElegido" input-id="pedido-produccion" :options="opcionesPedido" option-label="label" option-value="value" filter placeholder="Elija un pedido" fluid />
        </div>
        <div class="acciones-dialogo">
          <Button label="Cancelar" text type="button" :disabled="agregando" @click="dialogoPedido = false" />
          <Button label="Agregar pedido" type="submit" :loading="agregando" :disabled="agregando || pedidoElegido === null" />
        </div>
      </form>
    </Dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { ApiError } from '../api/cliente'
import { formatearFecha, formatearFechaHora } from '../api/fechas'
import { listarProductos, obtenerPedido, type Producto } from '../api/pedidos'
import {
  etiquetaEstadoProduccion, obtenerDetalleProduccion, obtenerInsumos, obtenerProduccion,
  type InsumosProduccion, type ProduccionConPedidos,
} from '../api/producciones'
import { filasDesdePedidos, filasDesdePlan, type FilaHojaProduccion } from './hojas'

const route = useRoute()
const id = computed(() => Number(route.params.id))
const produccion = ref<ProduccionConPedidos | null>(null)
const filas = ref<FilaHojaProduccion[]>([])
const insumos = ref<InsumosProduccion | null>(null)
const errorInsumos = ref(false)
const cargando = ref(true)
const error = ref<'no-encontrado' | 'no-disponible' | 'general' | null>(null)
const formato = new Intl.NumberFormat('es-GT', { maximumFractionDigits: 3 })
let version = 0

function formatear(valor: number): string { return formato.format(valor) }
function imprimir(): void { window.print() }

async function cargar(): Promise<void> {
  const actual = ++version
  cargando.value = true
  error.value = null
  produccion.value = null
  filas.value = []
  insumos.value = null
  errorInsumos.value = false
  try {
    const resultado = await obtenerProduccion(id.value)
    if (actual !== version) return
    const enProceso = resultado.estado === 'EN_PROCESO'
    if (!enProceso && !(resultado.estado === 'PLANIFICADA' && resultado.pedidos.length)) {
      produccion.value = resultado
      error.value = 'no-disponible'
      return
    }
    const catalogo = await listarProductos().catch((): Producto[] => [])
    const [plan, datosInsumos] = await Promise.all([
      enProceso
        ? obtenerDetalleProduccion(id.value).then((detalle) => filasDesdePlan(detalle, catalogo))
        : Promise.all(resultado.pedidos.map((pedido) => obtenerPedido(pedido.idPedido))).then((pedidos) => filasDesdePedidos(pedidos, catalogo)),
      obtenerInsumos(id.value).catch(() => null),
    ])
    if (actual !== version) return
    produccion.value = resultado
    filas.value = plan
    insumos.value = datosInsumos
    errorInsumos.value = datosInsumos === null
  } catch (causa) {
    if (actual !== version) return
    error.value = causa instanceof ApiError && (causa.status === 404 || causa.status === 400) ? 'no-encontrado' : 'general'
  } finally {
    if (actual === version) cargando.value = false
  }
}
watch(id, () => { void cargar() }, { immediate: true })
</script>

<template>
  <section class="pagina hoja-impresion hoja-produccion">
    <div class="acciones-hoja no-imprimir">
      <RouterLink :to="{ name: 'produccion-detalle', params: { id } }" class="volver-pedidos">← Volver</RouterLink>
      <Button label="Imprimir" icon="pi pi-print" :disabled="!produccion || !!error" @click="imprimir" />
    </div>
    <p v-if="cargando" role="status" class="texto-suave">Cargando hoja de producción…</p>
    <Message v-else-if="error === 'no-encontrado'" severity="warn">No se encontró la producción #{{ id }}.</Message>
    <Message v-else-if="error === 'no-disponible'" severity="info">La hoja solo está disponible para producciones en proceso o planificadas con pedidos.</Message>
    <Message v-else-if="error" severity="error">No se pudo cargar la hoja de producción.</Message>
    <article v-else-if="produccion" class="hoja">
      <header class="hoja-encabezado">
        <p class="hoja-empresa">Panadería San Carlos</p>
        <h1>Hoja de producción</h1>
        <p class="hoja-numero">Producción #{{ produccion.idProduccion }} · {{ etiquetaEstadoProduccion(produccion.estado) }}</p>
      </header>
      <dl class="hoja-datos">
        <div><dt>Fecha</dt><dd>{{ formatearFechaHora(produccion.fechaProduccion) }}</dd></div>
        <div><dt>Registró</dt><dd>{{ produccion.nombreUsuario }}</dd></div>
        <div class="hoja-datos-ancho"><dt>Pedidos</dt><dd>{{ produccion.pedidos.map((pedido) => `#${pedido.idPedido} ${pedido.nombreSucursal} (${formatearFecha(pedido.fechaEntrega)})`).join(', ') || '—' }}</dd></div>
        <div class="hoja-datos-ancho"><dt>Observación</dt><dd>{{ produccion.observacion ?? '—' }}</dd></div>
      </dl>
      <h2>Plan por producto</h2>
      <p v-if="produccion.estado === 'PLANIFICADA'" class="hoja-nota">Cantidades sumadas de los pedidos; el plan definitivo se genera al iniciar la producción.</p>
      <table class="hoja-tabla">
        <thead><tr><th>Producto</th><th class="numero">Planificado</th><th>Unidad</th><th class="hoja-columna-vacia">Producido</th></tr></thead>
        <tbody><tr v-for="fila in filas" :key="fila.idProducto">
          <td>{{ fila.nombre }}</td><td class="numero">{{ formatear(fila.planificado) }}</td><td>{{ fila.unidad }}</td><td class="hoja-columna-vacia" />
        </tr></tbody>
      </table>
      <h2>Insumos necesarios</h2>
      <p v-if="errorInsumos" class="hoja-nota">No se pudieron cargar los insumos.</p>
      <template v-else-if="insumos">
        <p v-if="insumos.productosSinReceta.length" class="hoja-nota">Sin receta vigente: {{ insumos.productosSinReceta.map((item) => item.nombre).join(', ') }}</p>
        <table class="hoja-tabla">
          <thead><tr><th>Insumo</th><th class="numero">Necesario</th><th class="numero">Disponible</th><th>Unidad</th></tr></thead>
          <tbody><tr v-for="item in insumos.insumos" :key="item.idInsumo">
            <td>{{ item.nombre }}</td><td class="numero">{{ formatear(item.requerido) }}</td><td class="numero">{{ formatear(item.disponible) }}</td><td>{{ item.unidad }}</td>
          </tr></tbody>
        </table>
      </template>
      <div class="hoja-firmas">
        <div class="hoja-firma"><span class="hoja-linea" /><span>Responsable de producción</span></div>
        <div class="hoja-firma"><span class="hoja-linea" /><span>Revisó</span></div>
        <div class="hoja-firma"><span class="hoja-linea" /><span>Fecha</span></div>
      </div>
    </article>
  </section>
</template>

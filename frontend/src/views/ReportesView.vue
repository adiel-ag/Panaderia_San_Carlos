<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import SelectButton from 'primevue/selectbutton'
import { ApiError } from '../api/cliente'
import { formatearFecha } from '../api/fechas'
import {
  reporteConsumoInsumos, reportePedidosPorSucursal, reporteProduccionPorDia,
  type ConsumoInsumoReporte, type PedidosSucursalReporte, type ProduccionDiaReporte,
} from '../api/reportes'
import {
  ETIQUETAS_REPORTE, REPORTES, anchoBarra, construirCsv, descargarCsv, filasConsumoInsumos, filasPedidosPorSucursal, filasProduccionPorDia,
  leerFiltrosReporte, nombreArchivoCsv, queryFiltrosReporte, totalProducciones, totalesPedidos, validarRango, type Reporte,
} from './reportes'

const route = useRoute()
const router = useRouter()
const filtros = computed(() => leerFiltrosReporte(route.query))
const opciones = REPORTES.map((valor) => ({ value: valor, label: ETIQUETAS_REPORTE[valor] }))
const desde = ref(filtros.value.desde)
const hasta = ref(filtros.value.hasta)
const errorRango = ref('')
const pedidos = ref<PedidosSucursalReporte[]>([])
const produccion = ref<ProduccionDiaReporte[]>([])
const consumo = ref<ConsumoInsumoReporte[]>([])
const cargando = ref(false)
const error = ref('')
const errorGeneral = ref(false)
const formato = new Intl.NumberFormat('es-GT', { maximumFractionDigits: 3 })
let version = 0

const totales = computed(() => totalesPedidos(pedidos.value))
const maximoConsumo = computed(() => Math.max(0, ...consumo.value.map((item) => item.consumido)))
const vacio = computed(() => {
  if (filtros.value.reporte === 'pedidos-por-sucursal') return !pedidos.value.length
  if (filtros.value.reporte === 'produccion-por-dia') return !produccion.value.length
  return !consumo.value.length
})

function formatear(valor: number): string { return formato.format(valor) }
function navegar(cambio: Partial<{ reporte: Reporte; desde: string; hasta: string }>): void {
  void router.replace({ name: 'reportes', query: queryFiltrosReporte({ ...filtros.value, ...cambio }) })
}
function cambiarReporte(valor: Reporte | null): void { if (valor) navegar({ reporte: valor }) }
function consultar(): void {
  const mensaje = validarRango(desde.value, hasta.value)
  errorRango.value = mensaje ?? ''
  if (mensaje) return
  if (desde.value === filtros.value.desde && hasta.value === filtros.value.hasta) void cargar()
  else navegar({ desde: desde.value, hasta: hasta.value })
}

async function cargar(): Promise<void> {
  const actual = ++version
  const { reporte, desde: inicio, hasta: fin } = filtros.value
  error.value = ''
  errorGeneral.value = false
  const mensaje = validarRango(inicio, fin)
  if (mensaje) { error.value = mensaje; cargando.value = false; return }
  cargando.value = true
  try {
    if (reporte === 'pedidos-por-sucursal') {
      const datos = await reportePedidosPorSucursal(inicio, fin)
      if (actual === version) pedidos.value = datos
    } else if (reporte === 'produccion-por-dia') {
      const datos = await reporteProduccionPorDia(inicio, fin)
      if (actual === version) produccion.value = datos
    } else {
      const datos = await reporteConsumoInsumos(inicio, fin)
      if (actual === version) consumo.value = datos
    }
  } catch (causa) {
    if (actual !== version) return
    if (causa instanceof ApiError && causa.status === 400) error.value = causa.message
    else errorGeneral.value = true
  } finally {
    if (actual === version) cargando.value = false
  }
}

function descargar(): void {
  const { reporte, desde: inicio, hasta: fin } = filtros.value
  const filas = reporte === 'pedidos-por-sucursal' ? filasPedidosPorSucursal(pedidos.value)
    : reporte === 'produccion-por-dia' ? filasProduccionPorDia(produccion.value) : filasConsumoInsumos(consumo.value)
  descargarCsv(nombreArchivoCsv(reporte, inicio, fin), construirCsv(filas))
}

watch(() => route.query, () => {
  desde.value = filtros.value.desde
  hasta.value = filtros.value.hasta
  errorRango.value = ''
  void cargar()
}, { immediate: true, deep: true })
</script>

<template>
  <section class="pagina pagina-reportes">
    <div class="cabecera-pedidos">
      <div><h1>Reportes</h1><p class="subtitulo">Pedidos, producción y consumo de insumos por rango de fechas.</p></div>
    </div>
    <form class="filtros-pedidos filtros-reportes" aria-label="Rango del reporte" @submit.prevent="consultar">
      <div class="campo">
        <label for="reporte-desde">Desde</label>
        <InputText id="reporte-desde" v-model="desde" type="date" :invalid="!!errorRango" />
      </div>
      <div class="campo">
        <label for="reporte-hasta">Hasta</label>
        <InputText id="reporte-hasta" v-model="hasta" type="date" :invalid="!!errorRango" />
      </div>
      <Button type="submit" label="Consultar" icon="pi pi-search" outlined :loading="cargando" />
      <small v-if="errorRango" class="error-campo" role="alert">{{ errorRango }}</small>
    </form>
    <div class="selector-reporte">
      <SelectButton :model-value="filtros.reporte" :options="opciones" option-label="label" option-value="value" :allow-empty="false" aria-label="Reporte" @update:model-value="cambiarReporte" />
    </div>

    <section class="seccion-pedido seccion-reporte" :aria-label="ETIQUETAS_REPORTE[filtros.reporte]">
      <div class="cabecera-seccion">
        <div>
          <h2>{{ ETIQUETAS_REPORTE[filtros.reporte] }}</h2>
          <p class="subtitulo">Del {{ formatearFecha(filtros.desde) }} al {{ formatearFecha(filtros.hasta) }}</p>
        </div>
        <Button label="Descargar CSV" icon="pi pi-download" text :disabled="cargando || !!error || errorGeneral || vacio" @click="descargar" />
      </div>
      <p v-if="cargando" role="status" class="texto-suave">Cargando reporte…</p>
      <Message v-else-if="error" severity="warn" role="alert">{{ error }}</Message>
      <div v-else-if="errorGeneral" class="aviso-unidades"><Message severity="error">No se pudo cargar el reporte.</Message><Button label="Reintentar" text @click="cargar" /></div>
      <p v-else-if="vacio" class="texto-suave">No hay datos en este rango de fechas.</p>

      <template v-else-if="filtros.reporte === 'pedidos-por-sucursal'">
        <div class="tabla-detalle-contenedor"><table class="tabla-detalle tabla-reporte">
          <thead><tr><th>Sucursal</th><th class="numero">Pedidos</th><th class="numero">Despachados</th><th class="numero">Pendientes</th><th>Productos</th></tr></thead>
          <tbody><tr v-for="fila in pedidos" :key="fila.idSucursal">
            <td>{{ fila.sucursal }}</td>
            <td class="numero">{{ fila.pedidos }}</td>
            <td class="numero">{{ fila.despachados }}</td>
            <td class="numero">{{ fila.pendientes }}</td>
            <td><ul class="lista-reporte"><li v-for="producto in fila.productos" :key="producto.idProducto">{{ producto.producto }}: {{ formatear(producto.cantidad) }}<span class="unidad">{{ producto.unidad }}</span></li></ul></td>
          </tr></tbody>
          <tfoot><tr><th scope="row">Total</th><td class="numero">{{ totales.pedidos }}</td><td class="numero">{{ totales.despachados }}</td><td class="numero">{{ totales.pendientes }}</td><td /></tr></tfoot>
        </table></div>
      </template>

      <template v-else-if="filtros.reporte === 'produccion-por-dia'">
        <div class="tabla-detalle-contenedor"><table class="tabla-detalle tabla-reporte">
          <thead><tr><th>Fecha</th><th class="numero">Producciones</th><th>Producto</th><th class="numero">Planificado</th><th class="numero">Producido</th></tr></thead>
          <tbody>
            <template v-for="dia in produccion" :key="dia.fecha">
              <tr v-for="(producto, indice) in dia.productos.length ? dia.productos : [null]" :key="`${dia.fecha}-${producto?.idProducto ?? 0}`">
                <td>{{ indice === 0 ? formatearFecha(dia.fecha) : '' }}</td>
                <td class="numero">{{ indice === 0 ? dia.producciones : '' }}</td>
                <template v-if="producto">
                  <td>{{ producto.producto }}</td>
                  <td class="numero">{{ formatear(producto.planificado) }}<span class="unidad">{{ producto.unidad }}</span></td>
                  <td class="numero">{{ formatear(producto.producido) }}<span class="unidad">{{ producto.unidad }}</span></td>
                </template>
                <template v-else><td class="texto-suave">—</td><td /><td /></template>
              </tr>
            </template>
          </tbody>
          <tfoot><tr><th scope="row">Total</th><td class="numero">{{ totalProducciones(produccion) }}</td><td colspan="3" /></tr></tfoot>
        </table></div>
      </template>

      <template v-else>
        <ul class="barras-consumo" aria-label="Consumo por insumo">
          <li v-for="item in consumo" :key="item.idInsumo">
            <span class="barras-consumo-nombre">{{ item.insumo }}</span>
            <div class="dashboard-pista" aria-hidden="true"><div class="dashboard-barra" :style="{ width: `${anchoBarra(item.consumido, maximoConsumo)}%` }" /></div>
            <strong>{{ formatear(item.consumido) }}<span class="unidad">{{ item.unidad }}</span></strong>
          </li>
        </ul>
        <div class="tabla-detalle-contenedor"><table class="tabla-detalle tabla-reporte">
          <thead><tr><th>Insumo</th><th class="numero">Consumido</th></tr></thead>
          <tbody><tr v-for="item in consumo" :key="item.idInsumo"><td>{{ item.insumo }}</td><td class="numero">{{ formatear(item.consumido) }}<span class="unidad">{{ item.unidad }}</span></td></tr></tbody>
          <tfoot><tr><th scope="row">Total</th><td class="numero">{{ consumo.length }} {{ consumo.length === 1 ? 'insumo' : 'insumos' }}</td></tr></tfoot>
        </table></div>
      </template>
    </section>
  </section>
</template>

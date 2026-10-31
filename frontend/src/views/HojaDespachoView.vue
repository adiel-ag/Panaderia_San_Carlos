<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { ApiError } from '../api/cliente'
import { formatearFecha } from '../api/fechas'
import { listarProductos, listarSucursales, obtenerPedido, type Pedido, type Producto, type Sucursal } from '../api/pedidos'

const route = useRoute()
const id = computed(() => Number(route.params.id))
// Se conserva el origen del detalle para volver al mismo lugar.
const volver = computed(() => {
  const valor = route.query.volver
  return typeof valor === 'string' && /^\/(?:pedidos|despacho)(?:\?|$)/.test(valor) ? { volver: valor } : {}
})
const pedido = ref<Pedido | null>(null)
const sucursales = ref<Sucursal[]>([])
const productos = ref<Producto[]>([])
const cargando = ref(true)
const error = ref<'no-encontrado' | 'general' | null>(null)
const formato = new Intl.NumberFormat('es-GT', { maximumFractionDigits: 3 })
let version = 0

const sucursal = computed(() => pedido.value
  ? sucursales.value.find((item) => item.idSucursal === pedido.value!.idSucursal)?.nombre ?? `#${pedido.value.idSucursal}`
  : '')
function unidad(idProducto: number): string {
  return productos.value.find((producto) => producto.idProducto === idProducto)?.abreviaturaUnidadMedida ?? ''
}
function imprimir(): void { window.print() }

async function cargar(): Promise<void> {
  const actual = ++version
  cargando.value = true
  error.value = null
  pedido.value = null
  try {
    const [resultado] = await Promise.all([
      obtenerPedido(id.value),
      // Sin nombres o unidades la hoja se imprime con el ID y la cantidad.
      listarSucursales().then((items) => { if (actual === version) sucursales.value = items }).catch(() => {}),
      listarProductos().then((items) => { if (actual === version) productos.value = items }).catch(() => {}),
    ])
    if (actual === version) pedido.value = resultado
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
  <section class="pagina hoja-impresion hoja-despacho">
    <div class="acciones-hoja no-imprimir">
      <RouterLink :to="{ name: 'pedido', params: { id }, query: volver }" class="volver-pedidos">← Volver</RouterLink>
      <Button label="Imprimir" icon="pi pi-print" :disabled="!pedido" @click="imprimir" />
    </div>
    <p v-if="cargando" role="status" class="texto-suave">Cargando hoja de despacho…</p>
    <Message v-else-if="error === 'no-encontrado'" severity="warn">No se encontró el pedido #{{ id }}.</Message>
    <Message v-else-if="error" severity="error">No se pudo cargar la hoja de despacho.</Message>
    <article v-else-if="pedido" class="hoja">
      <header class="hoja-encabezado">
        <p class="hoja-empresa">Panadería San Carlos</p>
        <h1>Hoja de despacho</h1>
        <p class="hoja-numero">Pedido #{{ pedido.idPedido }}</p>
      </header>
      <dl class="hoja-datos">
        <div><dt>Sucursal</dt><dd>{{ sucursal }}</dd></div>
        <div><dt>Fecha de entrega</dt><dd>{{ formatearFecha(pedido.fechaEntrega) }}</dd></div>
        <div class="hoja-datos-ancho"><dt>Observación</dt><dd>{{ pedido.observacion ?? '—' }}</dd></div>
      </dl>
      <table class="hoja-tabla">
        <thead><tr><th>Producto</th><th class="numero">Cantidad</th><th>Unidad</th><th class="hoja-columna-vacia">Recibido</th></tr></thead>
        <tbody><tr v-for="detalle in pedido.detalles" :key="detalle.idPedidoDetalle">
          <td>{{ detalle.nombreProducto }}</td>
          <td class="numero">{{ formato.format(detalle.cantidad) }}</td>
          <td>{{ unidad(detalle.idProducto) }}</td>
          <td class="hoja-columna-vacia" />
        </tr></tbody>
      </table>
      <div class="hoja-firmas">
        <div class="hoja-firma"><span class="hoja-linea" /><span>Entregó (nombre y firma)</span></div>
        <div class="hoja-firma"><span class="hoja-linea" /><span>Recibió (nombre y firma)</span></div>
        <div class="hoja-firma"><span class="hoja-linea" /><span>Fecha</span></div>
      </div>
    </article>
  </section>
</template>

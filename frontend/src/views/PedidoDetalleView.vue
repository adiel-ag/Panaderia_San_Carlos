<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import Message from 'primevue/message'
import Button from 'primevue/button'
import { ApiError } from '../api/cliente'
import { formatearFecha, formatearFechaHora } from '../api/fechas'
import { listarProductos, listarSucursales, obtenerDespacho, obtenerPedido, type Despacho, type Pedido, type Producto, type Sucursal } from '../api/pedidos'
import { useSesionStore } from '../stores/sesion'
import EstadoPedido from '../components/EstadoPedido.vue'

const route = useRoute()
const sesion = useSesionStore()
const id = computed(() => Number(route.params.id))
const puedeVerDespacho = computed(() => sesion.rol !== 'PRODUCCION')
const volver = computed(() => {
  const valor = route.query.volver
  return typeof valor === 'string' && /^\/(?:pedidos|despacho)(?:\?|$)/.test(valor) ? valor : '/pedidos'
})
const etiquetaVolver = computed(() => volver.value.startsWith('/despacho') ? 'Despacho' : 'Pedidos')
const pedido = ref<Pedido | null>(null)
const despacho = ref<Despacho | null>(null)
const sucursales = ref<Sucursal[]>([])
const productos = ref<Producto[]>([])
const cargando = ref(true)
const error = ref<'no-encontrado' | 'general' | null>(null)
const errorDespacho = ref(false)
const errorProductos = ref(false)
const formatoCantidad = new Intl.NumberFormat('es-GT', { maximumFractionDigits: 2 })
let version = 0

function nombreSucursal(idSucursal: number): string {
  return sucursales.value.find((sucursal) => sucursal.idSucursal === idSucursal)?.nombre ?? `#${idSucursal}`
}
// El estado del despacho llega en mayusculas desde Oracle (ENTREGADO).
function textoEstado(estado: string): string {
  const texto = estado.replaceAll('_', ' ').toLowerCase()
  return texto.charAt(0).toUpperCase() + texto.slice(1)
}
function unidad(idProducto: number): string {
  return productos.value.find((producto) => producto.idProducto === idProducto)?.abreviaturaUnidadMedida ?? ''
}
async function cargarProductos(actual: number): Promise<void> {
  errorProductos.value = false
  try {
    const items = await listarProductos()
    if (actual === version) productos.value = items
  } catch { if (actual === version) errorProductos.value = true }
}
async function cargar(): Promise<void> {
  const actual = ++version
  cargando.value = true
  error.value = null
  errorDespacho.value = false
  errorProductos.value = false
  pedido.value = null
  despacho.value = null
  try {
    const resultado = await obtenerPedido(id.value)
    if (actual !== version) return
    pedido.value = resultado
    const tareas: Promise<unknown>[] = [
      listarSucursales().then((items) => { if (actual === version) sucursales.value = items }).catch(() => {}),
      cargarProductos(actual),
    ]
    if (resultado.estado === 'DESPACHADO' && puedeVerDespacho.value) tareas.push(
      obtenerDespacho(id.value).then((item) => { if (actual === version) despacho.value = item })
        .catch(() => { if (actual === version) errorDespacho.value = true }),
    )
    await Promise.all(tareas)
  } catch (causa) {
    if (actual === version) error.value = causa instanceof ApiError && (causa.status === 404 || causa.status === 400) ? 'no-encontrado' : 'general'
  } finally {
    if (actual === version) cargando.value = false
  }
}
watch(id, () => { void cargar() }, { immediate: true })
</script>

<template>
  <section class="pagina pagina-pedido-detalle">
    <RouterLink :to="volver" class="volver-pedidos">← {{ etiquetaVolver }}</RouterLink>
    <div class="titulo-pedido"><h1>Pedido #{{ id }}</h1><EstadoPedido v-if="pedido" :estado="pedido.estado" /></div>
    <div v-if="cargando" class="detalle-cargando" aria-label="Cargando pedido">
      <div class="esqueleto-fila" /><div class="esqueleto-fila" /><div class="esqueleto-fila" />
    </div>
    <div v-else-if="error === 'no-encontrado'" class="detalle-aviso"><Message severity="warn">No se encontró el pedido #{{ id }}.</Message></div>
    <div v-else-if="error" class="detalle-aviso"><Message severity="error">No se pudo cargar el pedido.</Message></div>
    <template v-else-if="pedido">
      <dl class="datos-pedido">
        <div><dt>Sucursal</dt><dd>{{ nombreSucursal(pedido.idSucursal) }}</dd></div>
        <div><dt>Entrega</dt><dd>{{ formatearFecha(pedido.fechaEntrega) }}</dd></div>
        <div><dt>Observación</dt><dd :class="{ 'texto-suave': !pedido.observacion }">{{ pedido.observacion ?? '—' }}</dd></div>
      </dl>
      <section class="seccion-pedido"><h2>Productos</h2>
        <div v-if="errorProductos" class="aviso-unidades"><Message severity="error">No se pudieron cargar las unidades de los productos.</Message><Button label="Reintentar" text @click="cargarProductos(version)" /></div>
        <div class="tabla-detalle-contenedor"><table class="tabla-detalle"><thead><tr><th>Producto</th><th class="numero">Cantidad</th></tr></thead>
          <tbody><tr v-for="detalle in pedido.detalles" :key="detalle.idPedidoDetalle"><td>{{ detalle.nombreProducto }}</td><td class="numero">{{ formatoCantidad.format(detalle.cantidad) }}<span v-if="unidad(detalle.idProducto)" class="unidad">{{ unidad(detalle.idProducto) }}</span></td></tr></tbody>
        </table></div>
      </section>
      <section v-if="pedido.estado === 'DESPACHADO'" class="seccion-pedido"><h2>Despacho</h2>
        <p v-if="!puedeVerDespacho" class="texto-suave">No tiene permiso para consultar el despacho.</p>
        <Message v-else-if="errorDespacho" severity="error">No se pudo cargar el despacho.</Message>
        <p v-else-if="!despacho" class="texto-suave">Sin despacho registrado.</p>
        <dl v-else class="datos-pedido"><div><dt>Fecha</dt><dd>{{ formatearFechaHora(despacho.fechaDespacho) }}</dd></div><div><dt>Estado</dt><dd>{{ textoEstado(despacho.estado) }}</dd></div><div><dt>Observación</dt><dd :class="{ 'texto-suave': !despacho.observacion }">{{ despacho.observacion ?? '—' }}</dd></div></dl>
      </section>
    </template>
  </section>
</template>

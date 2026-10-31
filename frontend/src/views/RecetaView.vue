<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import { ApiError } from '../api/cliente'
import { formatearFecha } from '../api/fechas'
import { listarRecetas, listarVersiones, obtenerReceta, type RecetaDetalle, type RecetaResumen, type RecetaVersion } from '../api/recetas'
import { useSesionStore } from '../stores/sesion'
import EstadoReceta from '../components/EstadoReceta.vue'
import IlustracionPan from '../components/IlustracionPan.vue'
import { formatearCantidad } from './inventario'
import { abreviaturaUnidad } from './produccion'
import { consumoPorUnidad, estadoReceta, formatearConsumo } from './recetas'

const route = useRoute()
const router = useRouter()
const sesion = useSesionStore()
const idProducto = computed(() => Number(route.params.idProducto))
const puedeEditar = computed(() => sesion.rol === 'ADMINISTRADOR' || sesion.rol === 'PRODUCCION')
const resumen = ref<RecetaResumen | null>(null)
const receta = ref<RecetaDetalle | null>(null)
const versiones = ref<RecetaVersion[]>([])
const cargando = ref(true)
const error = ref<'no-encontrado' | 'general' | null>(null)
const errorVersiones = ref(false)
// Diálogo con los ingredientes de una versión.
const dialogoVisible = ref(false)
const versionAbierta = ref<RecetaVersion | null>(null)
const detalleVersion = ref<RecetaDetalle | null>(null)
const cargandoVersion = ref(false)
const errorVersion = ref(false)
let version = 0
let versionDialogo = 0

const estado = computed(() => resumen.value ? estadoReceta(resumen.value) : null)
const unidadProducto = computed(() => abreviaturaUnidad(resumen.value?.unidad))

function rinde(valor: number): string {
  const unidad = unidadProducto.value
  return unidad ? `${formatearCantidad(valor, unidad)} por tanda` : `${formatearCantidad(valor)} por tanda`
}
function porUnidad(cantidad: number, rendimiento: number): string {
  const valor = consumoPorUnidad(cantidad, rendimiento)
  return valor === null ? '—' : formatearConsumo(valor)
}
function nuevaVersion(): void {
  void router.push({ name: 'receta-nueva', params: { idProducto: idProducto.value } })
}

async function cargarVersiones(actual = version): Promise<void> {
  errorVersiones.value = false
  try {
    const resultado = await listarVersiones(idProducto.value)
    if (actual === version) versiones.value = resultado
  } catch { if (actual === version) errorVersiones.value = true }
}
async function cargar(): Promise<void> {
  const actual = ++version
  cargando.value = true
  error.value = null
  resumen.value = null
  receta.value = null
  versiones.value = []
  void cargarVersiones(actual)
  try {
    const lista = await listarRecetas()
    if (actual !== version) return
    const encontrado = lista.find((item) => item.idProducto === idProducto.value) ?? null
    if (!encontrado) { error.value = 'no-encontrado'; return }
    if (estadoReceta(encontrado) === 'vigente') {
      const detalle = await obtenerReceta(encontrado.idReceta!)
      if (actual !== version) return
      receta.value = detalle
    }
    resumen.value = encontrado
  } catch (causa) {
    if (actual !== version) return
    error.value = causa instanceof ApiError && causa.status === 404 ? 'no-encontrado' : 'general'
  } finally {
    if (actual === version) cargando.value = false
  }
}

async function verVersion(item: RecetaVersion): Promise<void> {
  const actual = ++versionDialogo
  versionAbierta.value = item
  detalleVersion.value = null
  errorVersion.value = false
  dialogoVisible.value = true
  cargandoVersion.value = true
  try {
    const detalle = await obtenerReceta(item.idReceta)
    if (actual === versionDialogo) detalleVersion.value = detalle
  } catch { if (actual === versionDialogo) errorVersion.value = true }
  finally { if (actual === versionDialogo) cargandoVersion.value = false }
}

watch(idProducto, () => { void cargar() }, { immediate: true })
</script>

<template>
  <section class="pagina pagina-pedido-detalle pagina-receta">
    <RouterLink :to="{ name: 'recetas' }" class="volver-pedidos">← Recetas</RouterLink>
    <div v-if="cargando" class="detalle-cargando" aria-label="Cargando receta">
      <div class="esqueleto-fila" /><div class="esqueleto-fila" /><div class="esqueleto-fila" />
    </div>
    <div v-else-if="error === 'no-encontrado'" class="detalle-aviso">
      <Message severity="warn">No se encontró el producto.</Message>
      <RouterLink :to="{ name: 'recetas' }" class="volver-pedidos">Volver a Recetas</RouterLink>
    </div>
    <div v-else-if="error" class="detalle-aviso"><Message severity="error">No se pudo cargar la receta.</Message><Button label="Reintentar" text @click="cargar" /></div>
    <template v-else-if="resumen && estado">
      <div class="titulo-pedido titulo-receta">
        <h1>{{ resumen.nombreProducto }}</h1><EstadoReceta :estado="estado" />
        <Button v-if="puedeEditar && estado === 'vigente'" label="Nueva versión" icon="pi pi-plus" class="accion-titulo" @click="nuevaVersion" />
      </div>

      <template v-if="estado === 'vigente' && receta">
        <dl class="datos-pedido">
          <div><dt>Versión</dt><dd>v{{ receta.version }}</dd></div>
          <div><dt>Rinde</dt><dd class="cifra-receta">{{ rinde(receta.rendimiento) }}</dd></div>
          <div><dt>Vigente desde</dt><dd>{{ formatearFecha(receta.fechaInicio) }}</dd></div>
        </dl>
        <section class="seccion-pedido">
          <h2>Ingredientes</h2>
          <p class="texto-suave nota-receta">Cantidades para una tanda. Al finalizar una producción se descuenta lo proporcional a lo producido.</p>
          <p v-if="!receta.ingredientes.length" class="texto-suave">Esta versión no tiene ingredientes.</p>
          <div v-else class="tabla-detalle-contenedor"><table class="tabla-detalle tabla-celdas-fijas tabla-ingredientes">
            <thead><tr><th>Insumo</th><th class="numero">Por tanda</th><th class="numero">Por unidad</th></tr></thead>
            <tbody><tr v-for="ingrediente in receta.ingredientes" :key="ingrediente.idInsumo">
              <td>{{ ingrediente.nombre }}</td>
              <td class="numero">{{ formatearCantidad(ingrediente.cantidad) }}<span v-if="abreviaturaUnidad(ingrediente.unidad)" class="unidad">{{ abreviaturaUnidad(ingrediente.unidad) }}</span></td>
              <td class="numero texto-suave">{{ porUnidad(ingrediente.cantidad, receta.rendimiento) }}<span v-if="abreviaturaUnidad(ingrediente.unidad)" class="unidad">{{ abreviaturaUnidad(ingrediente.unidad) }}</span></td>
            </tr></tbody>
          </table></div>
        </section>
      </template>

      <div v-else-if="estado === 'sin-receta'" class="receta-vacia">
        <IlustracionPan />
        <p>Este producto aún no tiene receta, por eso no se puede producir.</p>
        <Button v-if="puedeEditar" label="Crear receta" icon="pi pi-plus" @click="nuevaVersion" />
      </div>

      <div v-else-if="estado === 'ambigua'" class="aviso-receta">
        <Message severity="warn">Este producto tiene más de una receta activa, por eso no se puede producir. Guarde una versión nueva para dejar una sola vigente.</Message>
        <Button v-if="puedeEditar" label="Nueva versión" icon="pi pi-plus" @click="nuevaVersion" />
      </div>

      <section v-if="versiones.length || errorVersiones" class="seccion-pedido historial-receta">
        <h2>Historial de versiones</h2>
        <div v-if="errorVersiones" class="aviso-unidades"><Message severity="error">No se pudo cargar el historial.</Message><Button label="Reintentar" text @click="cargarVersiones()" /></div>
        <div v-else class="tabla-detalle-contenedor"><table class="tabla-detalle tabla-celdas-fijas tabla-versiones">
          <thead><tr><th>Versión</th><th>Estado</th><th class="numero">Rinde</th><th class="numero">Ingredientes</th><th>Desde</th><th>Hasta</th><th><span class="sr-only">Ingredientes de la versión</span></th></tr></thead>
          <tbody><tr v-for="item in versiones" :key="item.idReceta">
            <td class="identificador">v{{ item.version }}</td>
            <td><span v-if="item.vigente" class="estado-receta estado-receta--vigente">Vigente</span><span v-else class="texto-suave">{{ item.estado === 'ACTIVA' ? 'Activa' : 'Inactiva' }}</span></td>
            <td class="numero">{{ formatearCantidad(item.rendimiento) }}<span v-if="unidadProducto" class="unidad">{{ unidadProducto }}</span></td>
            <td class="numero">{{ item.cantidadIngredientes }}</td>
            <td>{{ formatearFecha(item.fechaInicio) }}</td>
            <td><template v-if="item.fechaFin">{{ formatearFecha(item.fechaFin) }}</template><span v-else class="texto-suave">—</span></td>
            <td class="celda-accion"><Button label="Ver" text size="small" :aria-label="`Ver ingredientes de la versión ${item.version}`" @click="verVersion(item)" /></td>
          </tr></tbody>
        </table></div>
      </section>
    </template>

    <Dialog v-model:visible="dialogoVisible" modal :header="versionAbierta ? `Versión ${versionAbierta.version}` : 'Versión'" class="dialogo-produccion dialogo-version">
      <p v-if="cargandoVersion" class="texto-suave">Cargando ingredientes…</p>
      <div v-else-if="errorVersion" class="aviso-unidades"><Message severity="error">No se pudieron cargar los ingredientes.</Message><Button v-if="versionAbierta" label="Reintentar" text @click="verVersion(versionAbierta)" /></div>
      <template v-else-if="detalleVersion">
        <p class="texto-suave rinde-version">Rinde {{ rinde(detalleVersion.rendimiento) }}</p>
        <p v-if="!detalleVersion.ingredientes.length" class="texto-suave">Esta versión no tiene ingredientes.</p>
        <div v-else class="tabla-detalle-contenedor"><table class="tabla-detalle tabla-celdas-fijas">
          <thead><tr><th>Insumo</th><th class="numero">Por tanda</th></tr></thead>
          <tbody><tr v-for="ingrediente in detalleVersion.ingredientes" :key="ingrediente.idInsumo">
            <td>{{ ingrediente.nombre }}</td>
            <td class="numero">{{ formatearCantidad(ingrediente.cantidad) }}<span v-if="abreviaturaUnidad(ingrediente.unidad)" class="unidad">{{ abreviaturaUnidad(ingrediente.unidad) }}</span></td>
          </tr></tbody>
        </table></div>
      </template>
    </Dialog>
  </section>
</template>

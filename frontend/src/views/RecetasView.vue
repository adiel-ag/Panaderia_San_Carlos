<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import SelectButton from 'primevue/selectbutton'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { formatearFecha } from '../api/fechas'
import { listarRecetas, type RecetaResumen } from '../api/recetas'
import { useSesionStore } from '../stores/sesion'
import EstadoReceta from '../components/EstadoReceta.vue'
import { formatearCantidad } from './inventario'
import { abreviaturaUnidad } from './produccion'
import { estadoReceta, filtrarRecetas, leerFiltroRecetas, queryFiltroRecetas, textoResumenRecetas, type FiltroRecetas } from './recetas'
import { vEtiquetasTabla } from '../directivas/etiquetasTabla'

const route = useRoute()
const router = useRouter()
const sesion = useSesionStore()
const puedeEditar = computed(() => sesion.rol === 'ADMINISTRADOR' || sesion.rol === 'PRODUCCION')
const filtro = computed(() => leerFiltroRecetas(route.query))
const opcionesFiltro = [
  { label: 'Todos', value: 'todos' }, { label: 'Sin receta', value: 'sin-receta' }, { label: 'Con receta', value: 'con-receta' },
]
const recetas = ref<RecetaResumen[]>([])
const cargando = ref(true)
const error = ref(false)
let version = 0

// La lista es corta: se filtra en el navegador.
const filtradas = computed(() => filtrarRecetas(recetas.value, filtro.value))
const resumen = computed(() => cargando.value || error.value ? null : textoResumenRecetas(recetas.value))

function cambiarFiltro(valor: FiltroRecetas | null): void {
  if (valor) void router.replace({ name: 'recetas', query: queryFiltroRecetas(valor) })
}
function destino(item: RecetaResumen) { return { name: 'receta', params: { idProducto: item.idProducto } } }
function abrirFila(evento: { data: RecetaResumen; originalEvent: Event }): void {
  if ((evento.originalEvent.target as Element).closest('a, button')) return
  void router.push(destino(evento.data))
}
function nuevaVersion(item: RecetaResumen): void {
  void router.push({ name: 'receta-nueva', params: { idProducto: item.idProducto } })
}

async function cargar(): Promise<void> {
  const actual = ++version
  cargando.value = true
  error.value = false
  try {
    const resultado = await listarRecetas()
    if (actual === version) recetas.value = resultado
  } catch {
    if (actual !== version) return
    recetas.value = []
    error.value = true
  } finally {
    if (actual === version) cargando.value = false
  }
}
void cargar()
</script>

<template>
  <section class="pagina pagina-recetas">
    <div class="cabecera-pedidos">
      <div><h1>Recetas</h1><p v-if="resumen" class="subtitulo hallazgo-pedidos resumen-recetas">{{ resumen }}</p></div>
    </div>
    <div class="filtros-pedidos" aria-label="Filtros de recetas">
      <div class="filtro-entrega">
        <label>Mostrar</label>
        <SelectButton :model-value="filtro" :options="opcionesFiltro" option-label="label" option-value="value" :allow-empty="false" aria-label="Mostrar" @update:model-value="cambiarFiltro" />
      </div>
    </div>
    <div v-etiquetas-tabla class="tabla-pedidos tabla-adaptable tabla-recetas" :class="{ 'tabla-recetas--acciones': puedeEditar }">
      <DataTable :value="filtradas" :loading="cargando" data-key="idProducto" @row-click="abrirFila">
        <Column header="Producto">
          <template #body="{ data }"><RouterLink :to="destino(data)" class="enlace-pedido">{{ data.nombreProducto }}</RouterLink></template>
        </Column>
        <Column header="Estado"><template #body="{ data }"><EstadoReceta :estado="estadoReceta(data)" /></template></Column>
        <Column header="Versión" header-class="numero" body-class="numero">
          <template #body="{ data }"><template v-if="estadoReceta(data) === 'vigente'">v{{ data.version }}</template><span v-else class="texto-suave">—</span></template>
        </Column>
        <Column header="Rinde" header-class="numero" body-class="numero">
          <template #body="{ data }">
            <template v-if="estadoReceta(data) === 'vigente' && data.rendimiento !== null">{{ formatearCantidad(data.rendimiento) }}<span class="unidad">{{ abreviaturaUnidad(data.unidad) }}</span></template>
            <span v-else class="texto-suave">—</span>
          </template>
        </Column>
        <Column header="Ingredientes" header-class="numero" body-class="numero">
          <template #body="{ data }"><template v-if="estadoReceta(data) === 'vigente'">{{ data.cantidadIngredientes }}</template><span v-else class="texto-suave">—</span></template>
        </Column>
        <Column header="Desde">
          <template #body="{ data }"><template v-if="estadoReceta(data) === 'vigente' && data.fechaInicio">{{ formatearFecha(data.fechaInicio) }}</template><span v-else class="texto-suave">—</span></template>
        </Column>
        <Column v-if="puedeEditar" header="Acción" body-class="celda-accion">
          <template #body="{ data }">
            <Button :label="estadoReceta(data) === 'sin-receta' ? 'Crear receta' : 'Nueva versión'" size="small" outlined class="accion-receta" :aria-label="`${estadoReceta(data) === 'sin-receta' ? 'Crear receta' : 'Nueva versión'} de ${data.nombreProducto}`" :data-accion="`receta-${data.idProducto}`" @click="nuevaVersion(data)" />
          </template>
        </Column>
        <template #empty>
          <div v-if="!cargando" class="tabla-estado">
            <template v-if="error"><Message severity="error">No se pudieron cargar las recetas.</Message><Button label="Reintentar" text @click="cargar" /></template>
            <p v-else-if="filtro !== 'todos'">No hay productos con este filtro.</p>
            <p v-else>No hay productos finales activos.</p>
          </div>
        </template>
      </DataTable>
    </div>
  </section>
</template>

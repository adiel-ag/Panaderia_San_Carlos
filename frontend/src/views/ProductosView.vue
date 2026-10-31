<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Select from 'primevue/select'
import SelectButton from 'primevue/selectbutton'
import Message from 'primevue/message'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { ApiError } from '../api/cliente'
import { actualizarProducto, cambiarEstadoProducto, crearProducto, listarProductos, listarTiposProducto, listarUnidades,
  type Producto, type TipoProducto, type UnidadCatalogo } from '../api/productos'
import { useSesionStore } from '../stores/sesion'
import { filtrarProductos, leerFiltrosProductos, normalizarProducto, queryFiltrosProductos, resumenProductos, validarProducto,
  type FiltroEstado, type FiltroTipo, type FormularioProducto } from './productos'

const route = useRoute()
const router = useRouter()
const sesion = useSesionStore()
const confirm = useConfirm()
const toast = useToast()
const puedeEditar = computed(() => sesion.rol === 'ADMINISTRADOR' || sesion.rol === 'BODEGA')
const filtros = computed(() => leerFiltrosProductos(route.query))
const productos = ref<Producto[]>([])
const unidades = ref<UnidadCatalogo[]>([])
const tipos = ref<TipoProducto[]>([])
const cargando = ref(true)
const error = ref(false)
const filtrados = computed(() => filtrarProductos(productos.value, filtros.value))
const busqueda = ref(filtros.value.q)
const tiposFiltro = [{ label: 'Todos', value: 'todos' }, { label: 'Insumos', value: 'insumos' }, { label: 'Productos finales', value: 'finales' }]
const estadosFiltro = [{ label: 'Activos', value: 'ACTIVO' }, { label: 'Inactivos', value: 'INACTIVO' }, { label: 'Todos', value: 'TODOS' }]
const visible = ref(false)
const editado = ref<Producto | null>(null)
const form = reactive<FormularioProducto>({ nombre: '', idTipoProducto: null, idUnidad: null, stockMinimo: 0 })
const intentado = ref(false)
const guardando = ref(false)
const errorFormulario = ref('')
const errores = computed(() => validarProducto(form))
const unidadElegida = computed(() => unidades.value.find((unidad) => unidad.idUnidad === form.idUnidad)?.abreviatura ?? '')
let version = 0

function navegar(cambio: Partial<ReturnType<typeof leerFiltrosProductos>>): void {
  void router.replace({ name: 'productos', query: queryFiltrosProductos({ ...filtros.value, ...cambio }) })
}
function cambiarTipo(valor: FiltroTipo | null): void { if (valor) navegar({ tipo: valor }) }
function cambiarEstadoFiltro(valor: FiltroEstado | null): void { if (valor) navegar({ estado: valor }) }
watch(() => filtros.value.q, (q) => { if (busqueda.value !== q) busqueda.value = q })
watch(busqueda, (q) => { if (q !== filtros.value.q) navegar({ q }) })

async function cargar(): Promise<void> {
  const actual = ++version
  cargando.value = true
  error.value = false
  try {
    const [lista, listaUnidades, listaTipos] = await Promise.all([listarProductos(), listarUnidades(), listarTiposProducto()])
    if (actual === version) { productos.value = lista; unidades.value = listaUnidades; tipos.value = listaTipos }
  } catch {
    if (actual === version) error.value = true
  } finally { if (actual === version) cargando.value = false }
}
void cargar()

function abrir(producto: Producto | null): void {
  editado.value = producto
  Object.assign(form, producto ? { nombre: producto.nombre, idTipoProducto: producto.idTipoProducto,
    idUnidad: producto.idUnidad, stockMinimo: producto.stockMinimo } : { nombre: '', idTipoProducto: null, idUnidad: null, stockMinimo: 0 })
  intentado.value = false
  errorFormulario.value = ''
  visible.value = true
}
async function guardar(): Promise<void> {
  if (guardando.value) return
  intentado.value = true
  errorFormulario.value = ''
  if (Object.keys(errores.value).length) return
  guardando.value = true
  try {
    if (editado.value) await actualizarProducto(editado.value.idProducto, normalizarProducto(form))
    else await crearProducto(normalizarProducto(form))
    visible.value = false
    toast.add({ severity: 'success', summary: 'Producto guardado', life: 4000 })
    await cargar()
  } catch (causa) {
    if (causa instanceof ApiError && causa.status === 400) errorFormulario.value = causa.message
  } finally { guardando.value = false }
}
async function cambiarEstado(producto: Producto): Promise<void> {
  try {
    await cambiarEstadoProducto(producto.idProducto, producto.estado === 'ACTIVO' ? 'INACTIVO' : 'ACTIVO')
    toast.add({ severity: 'success', summary: 'Estado actualizado', life: 4000 })
    await cargar()
  } catch (causa) {
    if (causa instanceof ApiError && causa.status === 400) toast.add({ severity: 'error', summary: causa.message, life: 5000 })
  }
}
function pedirCambio(producto: Producto): void {
  if (producto.estado === 'INACTIVO') { void cambiarEstado(producto); return }
  confirm.require({ header: `¿Desactivar ${producto.nombre}?`, message: 'Podrá activarlo de nuevo cuando lo necesite.',
    acceptLabel: 'Desactivar', rejectLabel: 'Cancelar', accept: () => { void cambiarEstado(producto) } })
}
</script>

<template>
  <section class="pagina pagina-catalogo">
    <div class="cabecera-pedidos">
      <div><h1>Productos</h1><p v-if="!cargando && !error" class="subtitulo resumen-catalogo">{{ resumenProductos(productos) }}</p></div>
      <Button v-if="puedeEditar" label="Nuevo producto" icon="pi pi-plus" @click="abrir(null)" />
    </div>
    <div class="filtros-pedidos filtros-catalogo" aria-label="Filtros de productos">
      <div class="filtro-entrega"><label>Tipo</label><SelectButton :model-value="filtros.tipo" :options="tiposFiltro" option-label="label" option-value="value" :allow-empty="false" aria-label="Tipo" @update:model-value="cambiarTipo" /></div>
      <div class="filtro-entrega"><label>Estado</label><SelectButton :model-value="filtros.estado" :options="estadosFiltro" option-label="label" option-value="value" :allow-empty="false" aria-label="Estado" @update:model-value="cambiarEstadoFiltro" /></div>
      <div class="filtro-busqueda"><label for="buscar-producto">Buscar por nombre</label><InputText id="buscar-producto" v-model="busqueda" placeholder="Buscar producto" /></div>
    </div>
    <div class="tabla-pedidos tabla-catalogo">
      <DataTable :value="filtrados" :loading="cargando" data-key="idProducto">
        <Column field="nombre" header="Nombre" />
        <Column header="Tipo"><template #body="{ data }">{{ data.nombreTipoProducto === 'INSUMO' ? 'Insumo' : 'Producto final' }}</template></Column>
        <Column field="abreviaturaUnidadMedida" header="Unidad" />
        <Column header="Mínimo" header-class="numero" body-class="numero"><template #body="{ data }">{{ data.stockMinimo }} <span class="unidad">{{ data.abreviaturaUnidadMedida }}</span></template></Column>
        <Column header="Estado"><template #body="{ data }"><span class="estado-usuario" :class="data.estado === 'ACTIVO' ? 'estado-usuario--activo' : 'estado-usuario--inactivo'">{{ data.estado === 'ACTIVO' ? 'Activo' : 'Inactivo' }}</span></template></Column>
        <Column v-if="puedeEditar" header="Acciones" body-class="celda-accion"><template #body="{ data }"><div class="acciones-catalogo"><Button label="Editar" outlined size="small" :aria-label="`Editar ${data.nombre}`" @click="abrir(data)" /><Button :label="data.estado === 'ACTIVO' ? 'Desactivar' : 'Activar'" text size="small" :aria-label="`${data.estado === 'ACTIVO' ? 'Desactivar' : 'Activar'} ${data.nombre}`" @click="pedirCambio(data)" /></div></template></Column>
        <template #empty><div v-if="!cargando" class="tabla-estado"><template v-if="error"><Message severity="error">No se pudieron cargar los productos.</Message><Button label="Reintentar" text @click="cargar" /></template><p v-else>No hay productos con estos filtros.</p></div></template>
      </DataTable>
    </div>
    <Dialog v-model:visible="visible" modal :header="editado ? 'Editar producto' : 'Nuevo producto'" class="dialogo-produccion" @hide="errorFormulario = ''">
      <form class="formulario-dialogo" @submit.prevent="guardar">
        <div><label for="producto-nombre">Nombre</label><InputText id="producto-nombre" v-model="form.nombre" maxlength="100" /><small v-if="intentado && errores.nombre" class="error-campo">{{ errores.nombre }}</small></div>
        <div><label for="producto-tipo">Tipo</label><Select id="producto-tipo" v-model="form.idTipoProducto" :options="tipos" option-label="nombre" option-value="idTipoProducto" :disabled="!!editado?.enUso" placeholder="Seleccione un tipo" /><small v-if="intentado && errores.idTipoProducto" class="error-campo">{{ errores.idTipoProducto }}</small></div>
        <div><label for="producto-unidad">Unidad</label><Select id="producto-unidad" v-model="form.idUnidad" :options="unidades" option-label="nombre" option-value="idUnidad" :disabled="!!editado?.enUso" placeholder="Seleccione una unidad" /><small v-if="intentado && errores.idUnidad" class="error-campo">{{ errores.idUnidad }}</small><small v-if="editado?.enUso" class="ayuda-campo">El tipo y la unidad no se pueden cambiar porque el producto ya se usó.</small></div>
        <div><label for="producto-minimo">Stock mínimo</label><InputNumber id="producto-minimo" v-model="form.stockMinimo" :min="0" :max="999999999.999" :min-fraction-digits="0" :max-fraction-digits="3" :suffix="unidadElegida ? ` ${unidadElegida}` : ''" /><small v-if="intentado && errores.stockMinimo" class="error-campo">{{ errores.stockMinimo }}</small></div>
        <Message v-if="errorFormulario" severity="error">{{ errorFormulario }}</Message>
        <div class="acciones-dialogo"><Button label="Cancelar" text type="button" @click="visible = false" /><Button label="Guardar" type="submit" :loading="guardando" /></div>
      </form>
    </Dialog>
  </section>
</template>

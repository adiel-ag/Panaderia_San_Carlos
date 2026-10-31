<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { RouterLink, onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import Message from 'primevue/message'
import Select from 'primevue/select'
import { ApiError } from '../api/cliente'
import { listarProductos, type Producto } from '../api/pedidos'
import { crearReceta, listarRecetas, listarVersiones, obtenerReceta, type RecetaResumen } from '../api/recetas'
import { abreviaturaUnidad, validarCantidad } from './produccion'
import {
  MAXIMO_INGREDIENTES, construirSolicitud, consumoPorUnidad, copiarFormulario, estadoReceta, formatearConsumo, formularioDesdeReceta,
  formularioVacio, hayCambios, hayErrores, ultimaVersion, validarReceta, type ErroresReceta, type FormularioReceta, type LineaReceta,
} from './recetas'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const confirm = useConfirm()
const idProducto = Number(route.params.idProducto)
const destinoDetalle = { name: 'receta', params: { idProducto } }
const formulario = reactive<FormularioReceta>(formularioVacio())
let inicial: FormularioReceta = formularioVacio()
const resumen = ref<RecetaResumen | null>(null)
const productos = ref<Producto[]>([])
// Última versión guardada al abrir: el servidor rechaza si otro la cambió entretanto.
const versionBase = ref<number | null>(null)
const cargando = ref(true)
const error = ref<'no-encontrado' | 'general' | null>(null)
const errorInsumos = ref(false)
const enviando = ref(false)
const guardado = ref(false)
const intentado = ref(false)
const tocados = reactive<Record<string, boolean>>({})
const errores = ref<ErroresReceta>({ lineas: {} })
const errorServidor = ref('')
const conflicto = ref('')
let siguienteClave = 2

const insumos = computed(() => productos.value
  .filter((producto) => producto.nombreTipoProducto === 'INSUMO' && producto.estado === 'ACTIVO')
  .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es')))
const activos = computed(() => new Set(insumos.value.map((insumo) => insumo.idProducto)))
const estado = computed(() => resumen.value ? estadoReceta(resumen.value) : null)
const nombre = computed(() => resumen.value?.nombreProducto ?? '')
const titulo = computed(() => estado.value === 'sin-receta' ? `Receta de ${nombre.value}` : `Nueva versión · ${nombre.value}`)
const nueva = computed(() => (versionBase.value ?? 0) + 1)
const explicacion = computed(() => {
  if (estado.value === 'vigente') return `Guardar crea la versión ${nueva.value} y reemplaza la actual. Las producciones anteriores conservan la receta con la que se hicieron.`
  if (estado.value === 'ambigua') return `Guardar crea la versión ${nueva.value} y reemplaza las recetas activas. Las producciones anteriores conservan la receta con la que se hicieron.`
  return `Guardar crea la versión ${nueva.value}; desde entonces el producto se puede producir.`
})
const unidadProducto = computed(() => abreviaturaUnidad(resumen.value?.unidad))
const sufijoRinde = computed(() => ` ${unidadProducto.value || 'u'} por tanda`)
const puedeAgregar = computed(() => formulario.lineas.length < MAXIMO_INGREDIENTES && formulario.lineas.length < insumos.value.length)

function validar(): void { errores.value = validarReceta(formulario, activos.value) }
function tocar(campo: string): void {
  tocados[campo] = true
  validar()
}
function visible(campo: string): boolean { return intentado.value || !!tocados[campo] }
function opciones(linea: LineaReceta): Producto[] {
  const elegidos = new Set(formulario.lineas.filter((otra) => otra.clave !== linea.clave).map((otra) => otra.idInsumo))
  return insumos.value.filter((insumo) => !elegidos.has(insumo.idProducto))
}
function insumo(idInsumo: number | null): Producto | undefined { return productos.value.find((item) => item.idProducto === idInsumo) }
function unidad(idInsumo: number | null): string { return abreviaturaUnidad(insumo(idInsumo)?.abreviaturaUnidadMedida) }
function sufijo(idInsumo: number | null): string | undefined {
  const valor = unidad(idInsumo)
  return valor ? ` ${valor}` : undefined
}
function vistaPrevia(linea: LineaReceta): string {
  if (validarCantidad(linea.cantidad) || validarCantidad(formulario.rendimiento)) return ''
  const valor = consumoPorUnidad(linea.cantidad, formulario.rendimiento)
  if (valor === null) return ''
  const medida = unidad(linea.idInsumo)
  return medida ? `${formatearConsumo(valor)} ${medida}` : formatearConsumo(valor)
}
async function agregarLinea(): Promise<void> {
  if (!puedeAgregar.value) return
  const clave = siguienteClave++
  formulario.lineas.push({ clave, idInsumo: null, cantidad: null })
  await nextTick()
  document.getElementById(`insumo-${clave}`)?.focus()
}
function quitarLinea(clave: number): void {
  if (formulario.lineas.length === 1) return
  formulario.lineas.splice(formulario.lineas.findIndex((linea) => linea.clave === clave), 1)
  validar()
}

async function cargar(): Promise<void> {
  cargando.value = true
  error.value = null
  try {
    const [lista, versiones, catalogo] = await Promise.all([
      listarRecetas(), listarVersiones(idProducto),
      listarProductos().then((valor) => { errorInsumos.value = false; return valor }, () => { errorInsumos.value = true; return [] as Producto[] }),
    ])
    const encontrado = lista.find((item) => item.idProducto === idProducto) ?? null
    if (!encontrado) { error.value = 'no-encontrado'; return }
    productos.value = catalogo
    // Sin versión vigente el formulario parte vacío: la base es la última guardada.
    versionBase.value = versiones.length ? ultimaVersion(versiones) : null
    // Se parte de la versión vigente: solo se cambia lo necesario.
    if (estadoReceta(encontrado) === 'vigente') {
      const detalle = await obtenerReceta(encontrado.idReceta!)
      Object.assign(formulario, formularioDesdeReceta(detalle))
      siguienteClave = formulario.lineas.length + 1
      // La base es la versión con la que se llenó el formulario: si alguien guardó
      // otra entre las consultas, el servidor rechaza en vez de aceptar datos viejos.
      versionBase.value = detalle.version
    }
    inicial = copiarFormulario(formulario)
    resumen.value = encontrado
  } catch (causa) {
    error.value = causa instanceof ApiError && causa.status === 404 ? 'no-encontrado' : 'general'
  } finally { cargando.value = false }
}
async function reintentarInsumos(): Promise<void> {
  try { productos.value = await listarProductos(); errorInsumos.value = false }
  catch { errorInsumos.value = true }
}

async function guardar(): Promise<void> {
  if (enviando.value || !resumen.value) return
  intentado.value = true
  errorServidor.value = ''
  conflicto.value = ''
  validar()
  const primerError = errores.value.rendimiento ? 'rendimiento-receta' : formulario.lineas.flatMap((linea) => {
    const error = errores.value.lineas[linea.clave]
    return error?.insumo ? [`insumo-${linea.clave}`] : error?.cantidad ? [`cantidad-${linea.clave}`] : []
  })[0]
  if (hayErrores(errores.value)) {
    await nextTick()
    if (primerError) document.getElementById(primerError)?.focus()
    return
  }
  enviando.value = true
  try {
    const receta = await crearReceta(construirSolicitud(idProducto, formulario, versionBase.value))
    guardado.value = true
    toast.add({ severity: 'success', summary: `Receta de ${nombre.value} guardada (versión ${receta.version})`, life: 5000 })
    await router.replace(destinoDetalle)
  } catch (causa) {
    if (!(causa instanceof ApiError)) return
    if (causa.status === 400) errorServidor.value = causa.message
    // El cliente ya avisó el 409; si otro guardó una versión, se ofrece revisarla sin perder lo escrito.
    else if (causa.status === 409 && /cambió mientras la editaba|Otro usuario guardó/.test(causa.message)) conflicto.value = causa.message
  } finally { enviando.value = false }
}
function cancelar(): void { void router.push(destinoDetalle) }

function conCambios(): boolean { return !guardado.value && !!resumen.value && hayCambios(formulario, inicial) }
function antesDeCerrar(evento: BeforeUnloadEvent): void {
  if (conCambios()) evento.preventDefault()
}
onMounted(() => { window.addEventListener('beforeunload', antesDeCerrar); void cargar() })
onUnmounted(() => window.removeEventListener('beforeunload', antesDeCerrar))
onBeforeRouteLeave(() => {
  if (!conCambios()) return true
  return new Promise<boolean>((resolve) => confirm.require({
    header: '¿Descartar los cambios?', message: 'Se perderán los cambios de la receta.',
    acceptLabel: 'Descartar', rejectLabel: 'Seguir editando',
    accept: () => resolve(true), reject: () => resolve(false), onHide: () => resolve(false),
  }))
})
</script>

<template>
  <section class="pagina pagina-pedido-nuevo pagina-receta-nueva">
    <RouterLink :to="destinoDetalle" class="volver-pedidos">← {{ nombre || 'Receta' }}</RouterLink>
    <div v-if="cargando" class="detalle-cargando" aria-label="Cargando receta"><div class="esqueleto-fila" /><div class="esqueleto-fila" /><div class="esqueleto-fila" /></div>
    <div v-else-if="error === 'no-encontrado'" class="detalle-aviso">
      <Message severity="warn">No se encontró el producto.</Message>
      <RouterLink :to="{ name: 'recetas' }" class="volver-pedidos">Volver a Recetas</RouterLink>
    </div>
    <div v-else-if="error" class="detalle-aviso"><Message severity="error">No se pudo cargar la receta.</Message><Button label="Reintentar" text @click="cargar" /></div>
    <template v-else-if="resumen">
      <h1>{{ titulo }}</h1>
      <p class="subtitulo pregunta-pedido">{{ explicacion }}</p>
      <form class="formulario-pedido" novalidate @submit.prevent="guardar">
        <Message v-if="errorServidor" severity="error" aria-live="polite">{{ errorServidor }}</Message>
        <Message v-if="conflicto" severity="warn" aria-live="polite" class="conflicto-receta">
          {{ conflicto }} <RouterLink :to="destinoDetalle" target="_blank" class="enlace-pedido">Ver la versión actual</RouterLink>
        </Message>
        <section class="bloque-pedido">
          <h2>Rendimiento</h2>
          <div class="campo campo-rinde">
            <label for="rendimiento-receta">Rinde</label>
            <InputNumber v-model="formulario.rendimiento" input-id="rendimiento-receta" locale="es-GT" :min="0.001" :max="999999999.999" :max-fraction-digits="3" :suffix="sufijoRinde" fluid :invalid="visible('rendimiento') && !!errores.rendimiento" @blur="tocar('rendimiento')" />
            <small class="ayuda-campo">Unidades que salen de una tanda. Los ingredientes son para esa tanda.</small>
            <small v-if="visible('rendimiento') && errores.rendimiento" class="error-campo">{{ errores.rendimiento }}</small>
          </div>
        </section>
        <section class="bloque-pedido">
          <h2>Ingredientes</h2>
          <div v-if="errorInsumos" class="aviso-unidades"><Message severity="error">No se pudieron cargar los insumos.</Message><Button label="Reintentar" text type="button" @click="reintentarInsumos" /></div>
          <p v-else-if="!insumos.length" class="texto-suave">No hay insumos activos para agregar.</p>
          <template v-else>
            <small v-if="intentado && errores.ingredientes" class="error-campo">{{ errores.ingredientes }}</small>
            <div v-for="(linea, indice) in formulario.lineas" :key="linea.clave" class="fila-producto fila-ingrediente">
              <div class="campo producto-campo">
                <label :for="`insumo-${linea.clave}`">Insumo {{ indice + 1 }}</label>
                <Select v-model="linea.idInsumo" :input-id="`insumo-${linea.clave}`" :options="opciones(linea)" option-label="nombre" option-value="idProducto" filter placeholder="Elija un insumo" fluid :invalid="visible(`insumo-${linea.clave}`) && !!errores.lineas[linea.clave]?.insumo" @change="tocar(`insumo-${linea.clave}`)" />
                <small v-if="visible(`insumo-${linea.clave}`) && errores.lineas[linea.clave]?.insumo" class="error-campo">{{ errores.lineas[linea.clave]?.insumo }}</small>
              </div>
              <div class="campo cantidad-campo">
                <label :for="`cantidad-${linea.clave}`">Por tanda</label>
                <InputNumber v-model="linea.cantidad" :input-id="`cantidad-${linea.clave}`" locale="es-GT" :min="0.001" :max-fraction-digits="3" :max="999999999.999" :suffix="sufijo(linea.idInsumo)" fluid :invalid="visible(`cantidad-${linea.clave}`) && !!errores.lineas[linea.clave]?.cantidad" @blur="tocar(`cantidad-${linea.clave}`)" />
                <small v-if="visible(`cantidad-${linea.clave}`) && errores.lineas[linea.clave]?.cantidad" class="error-campo">{{ errores.lineas[linea.clave]?.cantidad }}</small>
              </div>
              <div class="campo consumo-campo">
                <span class="etiqueta-consumo">Por unidad</span>
                <span class="consumo-linea texto-suave" :data-consumo="linea.clave">{{ vistaPrevia(linea) || '—' }}</span>
              </div>
              <Button icon="pi pi-trash" text type="button" class="quitar-linea" :aria-label="`Quitar ${insumo(linea.idInsumo)?.nombre ?? 'línea'}`" :disabled="formulario.lineas.length === 1" @click="quitarLinea(linea.clave)" />
            </div>
            <Button label="Agregar ingrediente" icon="pi pi-plus" text type="button" class="agregar-producto" :disabled="!puedeAgregar" @click="agregarLinea" />
          </template>
        </section>
        <footer class="acciones-pedido">
          <div><Button label="Guardar versión" type="submit" :loading="enviando" :disabled="enviando || errorInsumos || !insumos.length" /><Button label="Cancelar" text type="button" @click="cancelar" /></div>
        </footer>
      </form>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { RouterLink, onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import DatePicker from 'primevue/datepicker'
import InputNumber from 'primevue/inputnumber'
import Message from 'primevue/message'
import Select from 'primevue/select'
import Textarea from 'primevue/textarea'
import { ApiError } from '../api/cliente'
import { formatearFecha, hoyLocal, sumarDias } from '../api/fechas'
import { crearPedido, editarPedido, obtenerPedido, listarProductos, listarSucursales, type Producto, type Sucursal } from '../api/pedidos'
import { useSesionStore } from '../stores/sesion'
import { construirSolicitud, formularioDesdePedido, hayCambios, productosPedibles, validarPedido, type ErroresPedido, type EstadoFormularioPedido, type LineaPedido } from './formularioPedido'

const route = useRoute()
const router = useRouter()
const sesion = useSesionStore()
const toast = useToast()
const confirm = useConfirm()
const esSucursal = computed(() => sesion.rol === 'SUCURSAL')
const esEdicion = computed(() => route.name === 'pedido-editar')
const idEdicion = computed(() => Number(route.params?.id))
const hoy = hoyLocal()
const inicial = { idSucursal: esSucursal.value ? sesion.usuario?.idSucursal ?? null : null, fechaEntrega: sumarDias(hoy, 1) }
const estado = reactive<EstadoFormularioPedido>({ ...inicial, observacion: '', lineas: [{ clave: 1, idProducto: null, cantidad: null }] })
const sucursales = ref<Sucursal[]>([])
const productos = ref<Producto[]>([])
const cargando = ref(true)
const errorProductos = ref(false)
const errorSucursales = ref(false)
const enviando = ref(false)
const guardado = ref(false)
const intentado = ref(false)
const tocados = reactive<Record<string, boolean>>({})
const errores = ref<ErroresPedido>({ lineas: {} })
const errorServidor = ref('')
const errorCarga = ref('')
const original = ref<EstadoFormularioPedido | null>(null)
let siguienteClave = 2

const volver = computed(() => {
  if (esEdicion.value) return { name: 'pedido', params: { id: idEdicion.value } }
  const valor = route.query.volver
  return typeof valor === 'string' && /^\/pedidos(?:\?|$)/.test(valor) ? valor : '/pedidos'
})
const pedibles = computed(() => productosPedibles(productos.value))
const resumen = computed(() => {
  const resultado = validarPedido(estado, hoyLocal())
  if (resultado.sucursal || resultado.fechaEntrega || resultado.observacion || resultado.productos || Object.keys(resultado.lineas).length) return ''
  return `${estado.lineas.length} ${estado.lineas.length === 1 ? 'producto' : 'productos'} · entrega ${formatearFecha(estado.fechaEntrega)}`
})
const nombreSucursal = computed(() => sucursales.value.find((item) => item.idSucursal === estado.idSucursal)?.nombre ?? 'Su sucursal')
const fechaSeleccionada = computed<Date | null>({
  get: () => {
    if (!estado.fechaEntrega) return null
    const [anio, mes, dia] = estado.fechaEntrega.split('-').map(Number)
    return new Date(anio!, mes! - 1, dia!)
  },
  set: (fecha) => {
    estado.fechaEntrega = fecha ? `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}` : ''
    tocar('fechaEntrega')
  },
})

function tocar(campo: string): void {
  tocados[campo] = true
  errores.value = validarPedido(estado, hoyLocal())
}
function visible(campo: string): boolean { return intentado.value || !!tocados[campo] }
function opciones(linea: LineaPedido): Producto[] {
  const elegidos = new Set(estado.lineas.filter((otra) => otra.clave !== linea.clave).map((otra) => otra.idProducto))
  return pedibles.value.filter((producto) => !elegidos.has(producto.idProducto) || producto.idProducto === linea.idProducto)
}
function unidad(idProducto: number | null): string {
  const abreviatura = productos.value.find((item) => item.idProducto === idProducto)?.abreviaturaUnidadMedida
  return abreviatura === 'UND' ? 'u' : abreviatura === 'KG' ? 'kg' : abreviatura === 'L' ? 'L' : ''
}
async function agregarLinea(): Promise<void> {
  const clave = siguienteClave++
  estado.lineas.push({ clave, idProducto: null, cantidad: null })
  await nextTick()
  document.getElementById(`producto-${clave}`)?.focus()
}
function quitarLinea(clave: number): void {
  if (estado.lineas.length === 1) return
  estado.lineas.splice(estado.lineas.findIndex((linea) => linea.clave === clave), 1)
  delete errores.value.lineas[clave]
}
async function cargar(): Promise<void> {
  cargando.value = true
  errorCarga.value = ''
  if (esEdicion.value) {
    try {
      const pedido = await obtenerPedido(idEdicion.value)
      if (!['PENDIENTE', 'CONFIRMADO'].includes(pedido.estado) || pedido.idProduccion) {
        errorCarga.value = 'Este pedido ya no se puede editar.'
      } else {
        const datos = formularioDesdePedido(pedido)
        Object.assign(estado, datos)
        original.value = structuredClone(datos)
        siguienteClave = datos.lineas.length + 1
      }
    } catch (causa) {
      errorCarga.value = causa instanceof ApiError && causa.status === 404
        ? 'No se encontró el pedido.' : 'No se pudo cargar el pedido.'
    }
  }
  const [resultadoSucursales, resultadoProductos] = await Promise.allSettled([listarSucursales(), listarProductos()])
  if (resultadoSucursales.status === 'fulfilled') {
    sucursales.value = resultadoSucursales.value
    errorSucursales.value = false
  } else errorSucursales.value = true
  if (resultadoProductos.status === 'fulfilled') {
    productos.value = resultadoProductos.value
    errorProductos.value = false
  } else errorProductos.value = true
  cargando.value = false
}
async function reintentarProductos(): Promise<void> {
  try { productos.value = await listarProductos(); errorProductos.value = false }
  catch { errorProductos.value = true }
}
async function registrar(): Promise<void> {
  if (enviando.value) return
  intentado.value = true
  errorServidor.value = ''
  errores.value = validarPedido(estado, hoyLocal())
  const primerError = errores.value.sucursal ? 'sucursal-nueva' : errores.value.fechaEntrega ? 'fecha-entrega' :
    errores.value.observacion ? 'observacion-nueva' : estado.lineas.flatMap((linea) => {
      const error = errores.value.lineas[linea.clave]
      return error?.producto ? [`producto-${linea.clave}`] : error?.cantidad ? [`cantidad-${linea.clave}`] : []
    })[0]
  if (primerError || errores.value.productos) {
    await nextTick()
    if (primerError) document.getElementById(primerError)?.focus()
    return
  }
  enviando.value = true
  try {
    const solicitud = construirSolicitud(estado)
    const idPedido = esEdicion.value ? idEdicion.value : (await crearPedido(solicitud)).idPedido
    if (esEdicion.value) await editarPedido(idPedido, {
      fechaEntrega: solicitud.fechaEntrega, observacion: solicitud.observacion, detalles: solicitud.detalles,
    })
    guardado.value = true
    toast.add({ severity: 'success', summary: esEdicion.value ? `Pedido #${idPedido} actualizado` : `Pedido #${idPedido} registrado`, life: 5000 })
    await router.replace({ name: 'pedido', params: { id: idPedido } })
  } catch (causa) {
    if (causa instanceof ApiError && causa.status === 400) errorServidor.value = causa.message
  } finally { enviando.value = false }
}
function antesDeCerrar(evento: BeforeUnloadEvent): void {
  if (!guardado.value && hayCambios(estado, inicial, original.value ?? undefined)) evento.preventDefault()
}
onMounted(() => { window.addEventListener('beforeunload', antesDeCerrar); void cargar() })
onUnmounted(() => window.removeEventListener('beforeunload', antesDeCerrar))
onBeforeRouteLeave(() => {
  if (guardado.value || !hayCambios(estado, inicial, original.value ?? undefined)) return true
  return new Promise<boolean>((resolve) => confirm.require({
    header: '¿Descartar los cambios?', message: 'Se perderán los cambios del pedido.',
    acceptLabel: 'Descartar', rejectLabel: 'Seguir editando',
    accept: () => resolve(true), reject: () => resolve(false), onHide: () => resolve(false),
  }))
})
</script>

<template>
  <section class="pagina pagina-pedido-nuevo">
    <RouterLink :to="volver" class="volver-pedidos">← {{ esEdicion ? `Pedido #${idEdicion}` : 'Pedidos' }}</RouterLink>
    <h1>{{ esEdicion ? `Editar pedido #${idEdicion}` : 'Nuevo pedido' }}</h1>
    <p v-if="!esEdicion" class="subtitulo pregunta-pedido">El pedido queda confirmado al registrarlo.</p>
    <div v-if="cargando" class="detalle-cargando" aria-label="Cargando opciones del pedido"><div class="esqueleto-fila" /><div class="esqueleto-fila" /><div class="esqueleto-fila" /></div>
    <Message v-else-if="errorCarga" severity="error">{{ errorCarga }}</Message>
    <form v-else class="formulario-pedido" @submit.prevent="registrar">
      <Message v-if="errorServidor" severity="error" aria-live="polite">{{ errorServidor }}</Message>
      <section class="bloque-pedido">
        <h2>Entrega</h2>
        <div class="campos-entrega">
          <div class="campo">
            <label for="sucursal-nueva">Sucursal</label>
            <span v-if="esSucursal || esEdicion" id="sucursal-nueva" class="valor-sucursal">{{ nombreSucursal }}</span>
            <Select v-else v-model="estado.idSucursal" input-id="sucursal-nueva" :options="sucursales" option-label="nombre" option-value="idSucursal" filter placeholder="Elija una sucursal" fluid :invalid="visible('sucursal') && !!errores.sucursal" @change="tocar('sucursal')" />
            <small v-if="errorSucursales && !esSucursal" class="error-campo">No se pudieron cargar las sucursales. <Button label="Reintentar" text size="small" @click="cargar" /></small>
            <small v-if="visible('sucursal') && errores.sucursal" class="error-campo">{{ errores.sucursal }}</small>
          </div>
          <div class="campo">
            <label for="fecha-entrega">Fecha de entrega</label>
            <div class="fecha-controles">
              <DatePicker v-model="fechaSeleccionada" input-id="fecha-entrega" date-format="dd/mm/yy" :min-date="new Date()" show-icon fluid :invalid="visible('fechaEntrega') && !!errores.fechaEntrega" @blur="tocar('fechaEntrega')" />
              <span class="atajos-fecha">
              <Button label="Hoy" text type="button" @click="fechaSeleccionada = new Date()" />
              <Button label="Mañana" text type="button" @click="fechaSeleccionada = new Date(new Date().getFullYear(), new Date().getMonth(), new Date().getDate() + 1)" />
              </span>
            </div>
            <small v-if="visible('fechaEntrega') && errores.fechaEntrega" class="error-campo">{{ errores.fechaEntrega }}</small>
          </div>
        </div>
        <div class="campo campo-observacion">
          <label for="observacion-nueva">Observación <span class="texto-suave">(opcional)</span></label>
          <Textarea id="observacion-nueva" v-model="estado.observacion" auto-resize :maxlength="500" rows="2" fluid :invalid="visible('observacion') && !!errores.observacion" @blur="tocar('observacion')" />
          <small class="contador-observacion">{{ estado.observacion.length }}/500</small>
          <small v-if="visible('observacion') && errores.observacion" class="error-campo">{{ errores.observacion }}</small>
        </div>
      </section>
      <section class="bloque-pedido">
        <h2>Productos</h2>
        <div v-if="errorProductos" class="aviso-unidades"><Message severity="error">No se pudieron cargar los productos.</Message><Button label="Reintentar" text type="button" @click="reintentarProductos" /></div>
        <p v-else-if="!pedibles.length" class="texto-suave">No hay productos finales activos para pedir.</p>
        <template v-else>
          <small v-if="intentado && errores.productos" class="error-campo">{{ errores.productos }}</small>
          <div v-for="(linea, indice) in estado.lineas" :key="linea.clave" class="fila-producto">
            <div class="campo producto-campo">
              <label :for="`producto-${linea.clave}`">Producto {{ indice + 1 }}</label>
              <Select v-model="linea.idProducto" :input-id="`producto-${linea.clave}`" :options="opciones(linea)" option-label="nombre" option-value="idProducto" filter placeholder="Elija un producto" fluid :invalid="visible(`producto-${linea.clave}`) && !!errores.lineas[linea.clave]?.producto" @change="tocar(`producto-${linea.clave}`)" />
              <small v-if="visible(`producto-${linea.clave}`) && errores.lineas[linea.clave]?.producto" class="error-campo">{{ errores.lineas[linea.clave]?.producto }}</small>
            </div>
            <div class="campo cantidad-campo">
              <label :for="`cantidad-${linea.clave}`">Cantidad</label>
              <div class="cantidad-control"><InputNumber v-model="linea.cantidad" :input-id="`cantidad-${linea.clave}`" locale="es-GT" :min="0.001" :max-fraction-digits="3" :max="999999999.999" fluid :invalid="visible(`cantidad-${linea.clave}`) && !!errores.lineas[linea.clave]?.cantidad" @blur="tocar(`cantidad-${linea.clave}`)" /><span class="texto-suave unidad-cantidad">{{ unidad(linea.idProducto) }}</span></div>
              <small v-if="visible(`cantidad-${linea.clave}`) && errores.lineas[linea.clave]?.cantidad" class="error-campo">{{ errores.lineas[linea.clave]?.cantidad }}</small>
            </div>
            <Button icon="pi pi-trash" text type="button" class="quitar-linea" :aria-label="`Quitar ${productos.find((producto) => producto.idProducto === linea.idProducto)?.nombre ?? 'línea'}`" :disabled="estado.lineas.length === 1" @click="quitarLinea(linea.clave)" />
          </div>
          <Button label="Agregar producto" icon="pi pi-plus" text type="button" class="agregar-producto" :disabled="estado.lineas.length >= 50" @click="agregarLinea" />
        </template>
      </section>
      <footer class="acciones-pedido">
        <p class="texto-suave resumen-pedido" aria-live="polite">{{ resumen }}</p>
        <div><Button :label="esEdicion ? 'Guardar cambios' : 'Registrar pedido'" type="submit" :loading="enviando" :disabled="enviando || errorProductos || !pedibles.length" /><RouterLink :to="volver" class="cancelar-pedido">Cancelar</RouterLink></div>
      </footer>
    </form>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { etiquetaRol } from '../api/roles'
import { ESTADOS, etiquetaEstado, listarPedidos, type Estado } from '../api/pedidos'
import { listarProducciones } from '../api/producciones'
import { listarExistencias } from '../api/inventario'
import { listarRecetas } from '../api/recetas'
import { obtenerAlertas, type Alertas } from '../api/reportes'
import type { Rol } from '../api/tipos'
import { opcionesMenu } from '../router/menu'
import { useSesionStore } from '../stores/sesion'
import IlustracionPan from '../components/IlustracionPan.vue'

type Indicador = { titulo: string; valor: number; modulo: string; nota: string }
type Etapa = { estado: Estado; etiqueta: string; icono: string; total: number }
type Barra = { nombre: string; cantidad: number }
type Alerta = { clave: string; texto: string; destino: RouteLocationRaw }
const sesion = useSesionStore()
const modulos = computed(() => opcionesMenu(sesion.rol).filter((item) => item.nombre !== 'inicio'))
const indicadores = ref<Indicador[]>([])
const etapas = ref<Etapa[]>([])
// Recorrido del pedido en el día; los cancelados no pasan por el horno.
const ICONOS_ETAPA: Partial<Record<Estado, string>> = {
  PENDIENTE: 'pi pi-clock', CONFIRMADO: 'pi pi-check-circle', EN_PRODUCCION: 'pi pi-cog',
  LISTO_DESPACHO: 'pi pi-box', DESPACHADO: 'pi pi-truck',
}
function iconoModulo(modulo: string): string {
  return opcionesMenu(sesion.rol).find((opcion) => opcion.nombre === modulo)?.icono ?? 'pi pi-chart-bar'
}
// "Viernes 31 de octubre": fecha de hoy en español, sin coma y con mayúscula inicial.
function fechaLarga(fecha: Date): string {
  const partes = new Intl.DateTimeFormat('es-GT', { weekday: 'long', day: 'numeric', month: 'long' }).formatToParts(fecha)
  const parte = (tipo: Intl.DateTimeFormatPartTypes) => partes.find((item) => item.type === tipo)?.value ?? ''
  const texto = `${parte('weekday')} ${parte('day')} de ${parte('month')}`
  return texto.charAt(0).toUpperCase() + texto.slice(1)
}
// Se recalcula en cada carga junto con las cifras, para que nunca muestre un día viejo.
const fechaHoy = ref(fechaLarga(new Date()))
let temporizadorDia: ReturnType<typeof setTimeout> | undefined
const barras = ref<Barra[]>([])
const graficoTitulo = ref('')
const cargando = ref(true)
const error = ref(false)
const alertas = ref<Alertas | null>(null)
// Solo se listan los conteos mayores que cero; si la consulta falla el bloque no aparece.
const listaAlertas = computed<Alerta[]>(() => {
  const datos = alertas.value
  if (!datos) return []
  const plural = (n: number, uno: string, varios: string) => `${n} ${n === 1 ? uno : varios}`
  const lista: Alerta[] = []
  if (datos.pedidosAtrasados > 0) lista.push({ clave: 'atrasados', texto: plural(datos.pedidosAtrasados, 'pedido atrasado', 'pedidos atrasados'), destino: { name: 'pedidos', query: { entrega: 'todas' } } })
  if (datos.productosSinReceta > 0) lista.push({ clave: 'sin-receta', texto: plural(datos.productosSinReceta, 'producto sin receta', 'productos sin receta'), destino: { name: 'recetas', query: { filtro: 'sin-receta' } } })
  if (datos.insumosBajoMinimo > 0) lista.push({ clave: 'bajo-minimo', texto: plural(datos.insumosBajoMinimo, 'insumo bajo el mínimo', 'insumos bajo el mínimo'), destino: { name: 'inventario', query: { tipo: 'INSUMO', bajo: '1' } } })
  return lista
})
// Cada carga recibe un turno; solo la más reciente puede escribir en pantalla.
// Así una respuesta tardía (p. ej. de antes de medianoche) no pisa las cifras nuevas.
let turnoActual = 0
async function cargarAlertas(turno: number): Promise<void> {
  let datos: Alertas | null
  try { datos = await obtenerAlertas() } catch { datos = null }
  if (turno === turnoActual) alertas.value = datos
}
const maximo = computed(() => Math.max(1, ...barras.value.map((barra) => barra.cantidad)))

function hoyLocal(fecha: Date): string {
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`
}
function tiene(rol: Rol | null, roles: readonly Rol[]): boolean { return rol !== null && roles.includes(rol) }

// Al cambiar de día se vuelve a cargar: la fecha y las cifras "de hoy" pasan al día nuevo.
function programarCambioDeDia(desde: Date): void {
  clearTimeout(temporizadorDia)
  const manana = new Date(desde.getFullYear(), desde.getMonth(), desde.getDate() + 1)
  temporizadorDia = setTimeout(() => { void cargar() }, manana.getTime() - desde.getTime() + 1000)
}

async function cargar(): Promise<void> {
  const turno = ++turnoActual
  const ahora = new Date()
  fechaHoy.value = fechaLarga(ahora)
  programarCambioDeDia(ahora)
  void cargarAlertas(turno)
  cargando.value = true
  error.value = false
  indicadores.value = []
  etapas.value = []
  barras.value = []
  const rol = sesion.rol
  let nuevasEtapas: Etapa[] = []
  let nuevasBarras: Barra[] = []
  let nuevoTitulo = ''
  try {
    const nuevos: Indicador[] = []
    if (tiene(rol, ['ADMINISTRADOR', 'GERENTE', 'SUCURSAL', 'PRODUCCION', 'DESPACHO'])) {
      const fecha = hoyLocal(ahora)
      // Se usa el total del servidor, nunca la longitud de la primera página.
      const totales = await Promise.all(ESTADOS.map(async (estado) => ({
        estado, total: (await listarPedidos({ estado, entregaDesde: fecha, entregaHasta: fecha }, 1, 1, false)).total,
      })))
      nuevos.push({ titulo: 'Pedidos con entrega hoy', valor: totales.filter((item) => item.estado !== 'CANCELADO').reduce((suma, item) => suma + item.total, 0), modulo: 'pedidos', nota: 'Incluye los ya despachados' })
      nuevasEtapas = totales.filter((item) => item.estado !== 'CANCELADO')
        .map((item) => ({ estado: item.estado, etiqueta: etiquetaEstado(item.estado), icono: ICONOS_ETAPA[item.estado] ?? 'pi pi-circle', total: item.total }))
      nuevasBarras = totales.filter((item) => item.total > 0).map((item) => ({ nombre: etiquetaEstado(item.estado), cantidad: item.total }))
      nuevoTitulo = 'Pedidos por estado para hoy'
      if (tiene(rol, ['ADMINISTRADOR', 'GERENTE', 'DESPACHO'])) {
        const listos = await listarPedidos({ estado: 'LISTO_DESPACHO' }, 1, 1, false)
        nuevos.push({ titulo: 'Listos para despacho', valor: listos.total, modulo: rol === 'GERENTE' ? 'pedidos' : 'despacho', nota: 'Pendientes de entregar' })
      }
    }
    if (tiene(rol, ['ADMINISTRADOR', 'GERENTE', 'PRODUCCION'])) {
      const producciones = await listarProducciones('EN_PROCESO', 1, 1, false)
      nuevos.push({ titulo: 'Producciones en proceso', valor: producciones.total, modulo: 'produccion', nota: 'En curso' })
      const recetas = await listarRecetas()
      nuevos.push({ titulo: 'Productos sin receta', valor: recetas.filter((item) => item.recetasVigentes !== 1).length, modulo: 'recetas', nota: 'Necesitan receta vigente' })
    }
    if (tiene(rol, ['ADMINISTRADOR', 'GERENTE', 'PRODUCCION', 'BODEGA', 'DESPACHO'])) {
      const bajos = await listarExistencias({ tipo: 'INSUMO', bajoMinimo: true }, 1, 1, false)
      nuevos.push({ titulo: 'Insumos bajo mínimo', valor: bajos.total, modulo: 'inventario', nota: 'Revisar existencias' })
      if (rol === 'BODEGA') {
        const todos = await listarExistencias({ tipo: 'INSUMO' }, 1, 1, false)
        nuevasBarras = [{ nombre: 'Bajo mínimo', cantidad: bajos.total }, { nombre: 'Otros insumos', cantidad: Math.max(0, todos.total - bajos.total) }]
        nuevoTitulo = 'Estado de insumos'
      }
    }
    if (turno !== turnoActual) return
    indicadores.value = nuevos
    etapas.value = nuevasEtapas
    barras.value = nuevasBarras
    graficoTitulo.value = nuevoTitulo
  } catch {
    if (turno === turnoActual) error.value = true
  } finally {
    if (turno === turnoActual) cargando.value = false
  }
}
onMounted(() => { void cargar() })
onUnmounted(() => { clearTimeout(temporizadorDia); turnoActual++ })
</script>

<template>
  <section class="pagina pagina-inicio">
    <div class="saludo">
      <div>
        <p class="saludo-fecha">{{ fechaHoy }} · {{ etiquetaRol(sesion.rol) }}</p>
        <h1>Hola, {{ sesion.usuario?.nombre }}</h1>
        <p class="subtitulo">Lo que necesita atención en tu jornada.</p>
      </div>
      <IlustracionPan />
    </div>
    <section class="dashboard-seccion" aria-labelledby="titulo-operacion">
      <div class="cabecera-seccion"><div><h2 id="titulo-operacion">Operación de hoy</h2><p class="subtitulo">Cifras de los módulos a los que tienes acceso.</p></div><Button v-if="!cargando" label="Actualizar" icon="pi pi-refresh" text @click="cargar" /></div>
      <ul v-if="listaAlertas.length" class="alertas-inicio" aria-label="Alertas">
        <li v-for="alerta in listaAlertas" :key="alerta.clave" :data-alerta="alerta.clave">
          <i class="pi pi-exclamation-triangle" aria-hidden="true" /><RouterLink :to="alerta.destino">{{ alerta.texto }}</RouterLink>
        </li>
      </ul>
      <p v-if="cargando" role="status">Cargando indicadores…</p>
      <div v-else-if="error" class="dashboard-aviso"><Message severity="warn">No se pudieron cargar los indicadores.</Message><Button label="Reintentar" @click="cargar" /></div>
      <template v-else>
        <section v-if="etapas.length" class="horno" aria-labelledby="titulo-horno">
          <div class="horno-cabecera">
            <h3 id="titulo-horno">Hoy en el horno</h3>
            <p class="subtitulo">Pedidos con entrega hoy, según la etapa en que van.</p>
          </div>
          <ol class="horno-pasos">
            <li v-for="etapa in etapas" :key="etapa.estado" class="horno-paso" :data-etapa="etapa.estado">
              <RouterLink :to="{ name: 'pedidos', query: { entrega: 'hoy', estado: etapa.estado } }" class="horno-enlace" :aria-label="`${etapa.total} ${etapa.etiqueta}: ${etapa.total === 1 ? 'pedido' : 'pedidos'} con entrega hoy`">
                <span class="horno-icono" aria-hidden="true"><i :class="etapa.icono" /></span>
                <span class="horno-texto"><strong class="horno-valor">{{ etapa.total }}</strong><span class="horno-etiqueta">{{ etapa.etiqueta }}</span></span>
              </RouterLink>
            </li>
          </ol>
        </section>
        <div class="dashboard-indicadores">
          <RouterLink v-for="dato in indicadores" :key="dato.titulo" :to="{ name: dato.modulo }" class="dashboard-indicador">
            <span class="dashboard-indicador-titulo"><i :class="iconoModulo(dato.modulo)" aria-hidden="true" />{{ dato.titulo }}</span><strong class="dashboard-indicador-valor">{{ dato.valor }}</strong>
            <span class="dashboard-indicador-nota">{{ dato.nota }} <i class="pi pi-arrow-right" aria-hidden="true" /></span>
          </RouterLink>
        </div>
        <section v-if="graficoTitulo" class="dashboard-grafico" :aria-label="graficoTitulo">
          <h3>{{ graficoTitulo }}</h3>
          <p class="subtitulo">{{ graficoTitulo.includes('hoy') ? 'Fecha de entrega: hoy' : 'Situación actual' }}</p>
          <p v-if="!barras.length">No hay registros para mostrar.</p>
          <ul v-else class="dashboard-barras">
            <li v-for="barra in barras" :key="barra.nombre">
              <span>{{ barra.nombre }}</span><div class="dashboard-pista" aria-hidden="true"><div class="dashboard-barra" :style="{ width: `${barra.cantidad / maximo * 100}%` }" /></div><strong>{{ barra.cantidad }}</strong>
            </li>
          </ul>
        </section>
      </template>
    </section>
    <section class="dashboard-seccion" aria-labelledby="titulo-modulos">
      <h2 id="titulo-modulos">Módulos disponibles</h2>
      <nav class="lista-modulos" aria-label="Módulos disponibles">
        <RouterLink v-for="opcion in modulos" :key="opcion.nombre" :to="{ name: opcion.nombre }" class="fila-modulo">
          <i :class="opcion.icono" aria-hidden="true" /><span class="fila-modulo-texto"><strong>{{ opcion.etiqueta }}</strong><span>{{ opcion.descripcion }}</span></span><i class="pi pi-arrow-right" aria-hidden="true" />
        </RouterLink>
      </nav>
    </section>
  </section>
</template>

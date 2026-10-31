import type { LocationQuery, LocationQueryRaw } from 'vue-router'
import type { RecetaDetalle, RecetaResumen, RecetaVersion, SolicitudReceta } from '../api/recetas'
import { validarCantidad } from './produccion'

export type EstadoReceta = 'vigente' | 'sin-receta' | 'ambigua'
export type FiltroRecetas = 'todos' | 'sin-receta' | 'con-receta'

export interface LineaReceta {
  clave: number
  idInsumo: number | null
  cantidad: number | null
}
export interface FormularioReceta {
  rendimiento: number | null
  lineas: LineaReceta[]
}
export interface ErroresReceta {
  rendimiento?: string
  ingredientes?: string
  lineas: Record<number, { insumo?: string; cantidad?: string }>
}

export const MAXIMO_INGREDIENTES = 50

export function estadoReceta(resumen: Pick<RecetaResumen, 'recetasVigentes' | 'idReceta'>): EstadoReceta {
  if (resumen.recetasVigentes > 1) return 'ambigua'
  return resumen.recetasVigentes === 1 && resumen.idReceta !== null ? 'vigente' : 'sin-receta'
}

export function leerFiltroRecetas(query: LocationQuery): FiltroRecetas {
  const valor = query.filtro
  return valor === 'sin-receta' || valor === 'con-receta' ? valor : 'todos'
}
export function queryFiltroRecetas(filtro: FiltroRecetas): LocationQueryRaw {
  return filtro === 'todos' ? {} : { filtro }
}
// "Sin receta" reúne todo lo que no se puede producir, incluidas las recetas duplicadas.
export function filtrarRecetas(lista: RecetaResumen[], filtro: FiltroRecetas): RecetaResumen[] {
  if (filtro === 'todos') return lista
  return lista.filter((item) => (estadoReceta(item) === 'vigente') === (filtro === 'con-receta'))
}

export function textoResumenRecetas(lista: RecetaResumen[]): string {
  if (!lista.length) return 'No hay productos finales activos'
  const listos = lista.filter((item) => estadoReceta(item) === 'vigente').length
  if (listos === lista.length) return 'Todos los productos tienen receta'
  const sinReceta = lista.filter((item) => estadoReceta(item) === 'sin-receta').length
  const duplicadas = lista.length - listos - sinReceta
  let texto = `${listos} de ${lista.length} ${lista.length === 1 ? 'producto listo' : 'productos listos'} para producir`
  if (sinReceta) texto += ` · ${sinReceta} sin receta`
  if (duplicadas) texto += ` · ${duplicadas} con recetas duplicadas`
  return texto
}

// Lo que se descuenta de un insumo por cada unidad producida.
export function consumoPorUnidad(cantidad: number | null, rendimiento: number | null): number | null {
  if (cantidad === null || rendimiento === null || !Number.isFinite(cantidad) || !Number.isFinite(rendimiento) || cantidad <= 0 || rendimiento <= 0) return null
  return Math.round((cantidad / rendimiento) * 1e6) / 1e6
}
const formatoConsumo = new Intl.NumberFormat('es-GT', { maximumFractionDigits: 4 })
const formatoConsumoFino = new Intl.NumberFormat('es-GT', { maximumFractionDigits: 6 })
// Los consumos muy pequeños usan más decimales para no mostrarse como 0.
export function formatearConsumo(valor: number): string {
  return valor > 0 && valor < 0.0001 ? formatoConsumoFino.format(valor) : formatoConsumo.format(valor)
}

export function siguienteVersion(versiones: RecetaVersion[]): number {
  return ultimaVersion(versiones) + 1
}
export function ultimaVersion(versiones: RecetaVersion[]): number {
  return versiones.reduce((mayor, item) => Math.max(mayor, item.version), 0)
}

export function formularioVacio(): FormularioReceta {
  return { rendimiento: null, lineas: [{ clave: 1, idInsumo: null, cantidad: null }] }
}
export function formularioDesdeReceta(detalle: Pick<RecetaDetalle, 'rendimiento' | 'ingredientes'>): FormularioReceta {
  if (!detalle.ingredientes.length) return { ...formularioVacio(), rendimiento: detalle.rendimiento }
  return {
    rendimiento: detalle.rendimiento,
    lineas: detalle.ingredientes.map((item, indice) => ({ clave: indice + 1, idInsumo: item.idInsumo, cantidad: item.cantidad })),
  }
}
export function copiarFormulario(formulario: FormularioReceta): FormularioReceta {
  return { rendimiento: formulario.rendimiento, lineas: formulario.lineas.map((linea) => ({ ...linea })) }
}

// Mismas reglas que el servidor; `activos` marca los insumos que ya no se pueden usar.
export function validarReceta(formulario: FormularioReceta, activos?: ReadonlySet<number>): ErroresReceta {
  const errores: ErroresReceta = { lineas: {} }
  if (validarCantidad(formulario.rendimiento)) {
    errores.rendimiento = 'Ingrese un rendimiento mayor que 0, con hasta 3 decimales y máximo 999999999.999.'
  }
  if (!formulario.lineas.length) errores.ingredientes = 'Agregue al menos un ingrediente.'
  else if (formulario.lineas.length > MAXIMO_INGREDIENTES) errores.ingredientes = `Una receta admite como máximo ${MAXIMO_INGREDIENTES} ingredientes.`
  const vistos = new Set<number>()
  for (const linea of formulario.lineas) {
    const error: { insumo?: string; cantidad?: string } = {}
    if (linea.idInsumo === null) error.insumo = 'Elija un insumo.'
    else if (vistos.has(linea.idInsumo)) error.insumo = 'Insumo repetido.'
    else if (activos && !activos.has(linea.idInsumo)) error.insumo = 'Este insumo ya no está activo; elija otro.'
    if (linea.idInsumo !== null) vistos.add(linea.idInsumo)
    const errorCantidad = validarCantidad(linea.cantidad)
    if (errorCantidad) error.cantidad = errorCantidad
    if (Object.keys(error).length) errores.lineas[linea.clave] = error
  }
  return errores
}
export function hayErrores(errores: ErroresReceta): boolean {
  return !!errores.rendimiento || !!errores.ingredientes || Object.keys(errores.lineas).length > 0
}

export function construirSolicitud(idProducto: number, formulario: FormularioReceta, versionBase: number | null): SolicitudReceta {
  return {
    idProducto, rendimiento: formulario.rendimiento!, versionBase,
    ingredientes: formulario.lineas.map((linea) => ({ idInsumo: linea.idInsumo!, cantidad: linea.cantidad! })),
  }
}

// Compara en milésimas para no marcar cambios por errores de punto flotante.
function igual(a: number | null, b: number | null): boolean {
  return a === null || b === null ? a === b : Math.round(a * 1000) === Math.round(b * 1000)
}
export function hayCambios(formulario: FormularioReceta, inicial: FormularioReceta): boolean {
  if (!igual(formulario.rendimiento, inicial.rendimiento) || formulario.lineas.length !== inicial.lineas.length) return true
  return formulario.lineas.some((linea, indice) => {
    const otra = inicial.lineas[indice]!
    return linea.idInsumo !== otra.idInsumo || !igual(linea.cantidad, otra.cantidad)
  })
}

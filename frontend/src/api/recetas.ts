import { api } from './cliente'

export type UnidadProducto = 'UND' | 'KG' | 'L'
export type EstadoRecetaApi = 'ACTIVA' | 'INACTIVA'

// Una fila por producto final activo; recetasVigentes distingue sin receta (0), lista (1) y duplicada (>1).
export interface RecetaResumen {
  idProducto: number
  nombreProducto: string
  unidad: UnidadProducto
  idReceta: number | null
  version: number | null
  rendimiento: number | null
  cantidadIngredientes: number
  fechaInicio: string | null
  recetasVigentes: number
}
export interface IngredienteReceta {
  idInsumo: number
  nombre: string
  unidad: string
  cantidad: number
}
export interface RecetaDetalle {
  idReceta: number
  idProducto: number
  nombreProducto: string
  unidad: UnidadProducto
  version: number
  rendimiento: number
  estado: EstadoRecetaApi
  fechaInicio: string
  fechaFin: string | null
  vigente: boolean
  ingredientes: IngredienteReceta[]
}
export interface RecetaVersion {
  idReceta: number
  version: number
  rendimiento: number
  estado: EstadoRecetaApi
  fechaInicio: string
  fechaFin: string | null
  vigente: boolean
  cantidadIngredientes: number
}
export interface SolicitudReceta {
  idProducto: number
  rendimiento: number
  ingredientes: { idInsumo: number; cantidad: number }[]
  // Última versión que se vio al editar (null si no había ninguna); el servidor rechaza con 409 si cambió.
  versionBase: number | null
}

export function listarRecetas(): Promise<RecetaResumen[]> { return api('recetas') }
export function obtenerReceta(idReceta: number): Promise<RecetaDetalle> { return api(`recetas/${idReceta}`) }
export function listarVersiones(idProducto: number): Promise<RecetaVersion[]> { return api(`recetas/productos/${idProducto}/versiones`) }
// Cada cambio guarda una versión nueva: las producciones anteriores conservan la receta que usaron.
export function crearReceta(solicitud: SolicitudReceta): Promise<RecetaDetalle> {
  return api('recetas', { metodo: 'POST', cuerpo: solicitud, avisar: true })
}

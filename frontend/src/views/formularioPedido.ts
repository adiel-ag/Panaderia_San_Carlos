import type { Producto, SolicitudPedido } from '../api/pedidos'

export interface LineaPedido {
  clave: number
  idProducto: number | null
  cantidad: number | null
}
export interface EstadoFormularioPedido {
  idSucursal: number | null
  fechaEntrega: string
  observacion: string
  lineas: LineaPedido[]
}
export interface ErroresPedido {
  sucursal?: string
  fechaEntrega?: string
  observacion?: string
  productos?: string
  lineas: Record<number, { producto?: string; cantidad?: string }>
}

export function productosPedibles(productos: Producto[]): Producto[] {
  return productos.filter((producto) => producto.nombreTipoProducto === 'PRODUCTO_FINAL' && producto.estado === 'ACTIVO')
    .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))
}

// Rechaza fechas que no existen en el calendario, como 2026-02-30.
function fechaValida(fecha: string): boolean {
  const partes = /^(\d{4})-(\d{2})-(\d{2})$/.exec(fecha)
  if (!partes) return false
  const [anio, mes, dia] = partes.slice(1).map(Number) as [number, number, number]
  const local = new Date(anio, mes - 1, dia)
  return local.getFullYear() === anio && local.getMonth() === mes - 1 && local.getDate() === dia
}

export function validarPedido(estado: EstadoFormularioPedido, hoy: string): ErroresPedido {
  const errores: ErroresPedido = { lineas: {} }
  if (estado.idSucursal === null || estado.idSucursal <= 0) errores.sucursal = 'Elija una sucursal.'
  if (!estado.fechaEntrega) errores.fechaEntrega = 'Elija una fecha de entrega.'
  else if (!fechaValida(estado.fechaEntrega)) errores.fechaEntrega = 'La fecha de entrega no es válida.'
  else if (estado.fechaEntrega < hoy) errores.fechaEntrega = 'La fecha de entrega no puede estar en el pasado.'
  if (estado.observacion.length > 500) errores.observacion = 'La observación no puede superar 500 caracteres.'
  if (!estado.lineas.length) errores.productos = 'Agregue al menos un producto.'
  const vistos = new Set<number>()
  for (const linea of estado.lineas) {
    const error: { producto?: string; cantidad?: string } = {}
    if (linea.idProducto === null) error.producto = 'Elija un producto.'
    else if (vistos.has(linea.idProducto)) error.producto = 'Producto repetido.'
    else vistos.add(linea.idProducto)
    const cantidad = linea.cantidad
    if (cantidad === null || !Number.isFinite(cantidad) || cantidad <= 0 || cantidad > 999999999.999 || Math.abs(cantidad * 1000 - Math.round(cantidad * 1000)) > 0.00001) {
      error.cantidad = 'Ingrese una cantidad mayor que 0, con hasta 3 decimales y máximo 999999999.999.'
    }
    if (Object.keys(error).length) errores.lineas[linea.clave] = error
  }
  return errores
}

export function construirSolicitud(estado: EstadoFormularioPedido): SolicitudPedido {
  const observacion = estado.observacion.trim()
  return {
    idSucursal: estado.idSucursal!, fechaEntrega: estado.fechaEntrega, observacion: observacion || null,
    detalles: estado.lineas.map((linea) => ({ idProducto: linea.idProducto!, cantidad: linea.cantidad! })),
  }
}

export function hayCambios(estado: EstadoFormularioPedido, inicial?: Pick<EstadoFormularioPedido, 'idSucursal' | 'fechaEntrega'>): boolean {
  if (inicial && (estado.idSucursal !== inicial.idSucursal || estado.fechaEntrega !== inicial.fechaEntrega)) return true
  return (!inicial && estado.idSucursal !== null) || estado.observacion.length > 0 || estado.lineas.length !== 1 ||
    estado.lineas.some((linea) => linea.idProducto !== null || linea.cantidad !== null)
}

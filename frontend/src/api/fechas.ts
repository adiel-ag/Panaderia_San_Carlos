function fechaLocal(fecha: string): Date {
  const [anio = 0, mes = 1, dia = 1] = fecha.split('-').map(Number)
  return new Date(anio, mes - 1, dia)
}

function claveFecha(fecha: Date): string {
  const anio = fecha.getFullYear()
  const mes = String(fecha.getMonth() + 1).padStart(2, '0')
  const dia = String(fecha.getDate()).padStart(2, '0')
  return `${anio}-${mes}-${dia}`
}

export function hoyLocal(): string { return claveFecha(new Date()) }

export function sumarDias(fecha: string, dias: number): string {
  const local = fechaLocal(fecha)
  local.setDate(local.getDate() + dias)
  return claveFecha(local)
}

export function finDeSemana(fecha: string): string {
  const dia = fechaLocal(fecha).getDay()
  return sumarDias(fecha, (7 - dia) % 7)
}

const formatoFecha = new Intl.DateTimeFormat('es-GT', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })
// Sin año cuando es el año en curso: la fecha cabe en una línea en el celular.
const formatoFechaCorta = new Intl.DateTimeFormat('es-GT', { weekday: 'short', day: 'numeric', month: 'short' })
const formatoFechaHora = new Intl.DateTimeFormat('es-GT', {
  day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
})

export function formatearFecha(fecha: string): string {
  const local = fechaLocal(fecha)
  const formato = local.getFullYear() === new Date().getFullYear() ? formatoFechaCorta : formatoFecha
  return formato.format(local).replaceAll('.', '').replace(',', '')
}

export function formatearFechaHora(iso: string): string {
  return formatoFechaHora.format(new Date(iso)).replaceAll('.', '')
}

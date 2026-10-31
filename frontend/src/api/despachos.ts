import { api } from './cliente'
import type { Despacho } from './pedidos'

export function despacharPedido(idPedido: number, observacion: string | null): Promise<Despacho> {
  return api(`pedidos/${idPedido}/despacho`, { metodo: 'POST', cuerpo: { observacion }, avisar: true })
}

import type { Directive } from 'vue'

/**
 * Copia el texto de cada encabezado de columna al atributo data-label de las
 * celdas de esa columna. En el celular la tabla se apila como tarjetas y el CSS
 * muestra esa etiqueta delante de cada valor (`td::before`).
 */
const observadores = new WeakMap<HTMLElement, MutationObserver>()

function textoEncabezado(celda: Element): string {
  const titulo = celda.querySelector('.p-datatable-column-title')
  return (titulo ?? celda).textContent?.replace(/\s+/g, ' ').trim() ?? ''
}

export function etiquetarTablas(raiz: HTMLElement): void {
  raiz.querySelectorAll('table').forEach((tabla) => {
    const encabezados = tabla.querySelector(':scope > thead > tr')
    if (!encabezados) return
    const textos = Array.from(encabezados.children).map(textoEncabezado)
    tabla.querySelectorAll(':scope > tbody > tr').forEach((fila) => {
      const celdas = Array.from(fila.children)
      // La fila de "sin resultados" ocupa todas las columnas: no lleva etiqueta.
      if (celdas.length === 1 && (celdas[0] as HTMLTableCellElement).colSpan > 1) {
        celdas[0]?.removeAttribute('data-label')
        return
      }
      celdas.forEach((celda, indice) => {
        const etiqueta = textos[indice] ?? ''
        if (celda.getAttribute('data-label') !== etiqueta) celda.setAttribute('data-label', etiqueta)
      })
    })
  })
}

export const vEtiquetasTabla: Directive<HTMLElement> = {
  mounted(elemento) {
    etiquetarTablas(elemento)
    if (typeof MutationObserver === 'undefined') return
    // Solo se observan filas y columnas nuevas; cambiar atributos no vuelve a disparar.
    const observador = new MutationObserver(() => { etiquetarTablas(elemento) })
    observador.observe(elemento, { childList: true, subtree: true, characterData: true })
    observadores.set(elemento, observador)
  },
  updated(elemento) { etiquetarTablas(elemento) },
  beforeUnmount(elemento) {
    observadores.get(elemento)?.disconnect()
    observadores.delete(elemento)
  },
}

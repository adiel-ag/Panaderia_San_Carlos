import { ref } from 'vue'

const CLAVE = 'panaderia.tema'
const oscuro = ref(false)
let iniciado = false
let eleccionManual = false

function aplicarTema(valor: boolean): void {
  oscuro.value = valor
  document.documentElement.classList.toggle('tema-oscuro', valor)
  document.documentElement.style.colorScheme = valor ? 'dark' : 'light'
  let meta = document.querySelector<HTMLMetaElement>('meta[name="color-scheme"]')
  if (!meta) {
    meta = document.createElement('meta')
    meta.name = 'color-scheme'
    document.head.append(meta)
  }
  meta.content = valor ? 'dark' : 'light'
}

export function iniciarTema(): void {
  if (iniciado) return
  iniciado = true
  let guardado: string | null = null
  try { guardado = localStorage.getItem(CLAVE) } catch { /* Almacenamiento bloqueado. */ }
  eleccionManual = guardado === 'oscuro' || guardado === 'claro'
  let media: MediaQueryList | undefined
  try { media = window.matchMedia('(prefers-color-scheme: dark)') } catch { /* Preferencia no disponible. */ }
  aplicarTema(eleccionManual ? guardado === 'oscuro' : (media?.matches ?? false))
  try {
    media?.addEventListener('change', (event) => {
      if (!eleccionManual) aplicarTema(event.matches)
    })
  } catch { /* Escucha no disponible. */ }
}

export function useTema() {
  function alternar(): void {
    aplicarTema(!oscuro.value)
    eleccionManual = true
    try { localStorage.setItem(CLAVE, oscuro.value ? 'oscuro' : 'claro') } catch { /* Almacenamiento bloqueado. */ }
  }
  return { oscuro, alternar }
}

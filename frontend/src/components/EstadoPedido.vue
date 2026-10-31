<script setup lang="ts">
import { computed } from 'vue'
import { etiquetaEstado, type Estado } from '../api/pedidos'

const props = defineProps<{ estado: Estado }>()
const nombre = computed(() => ({
  PENDIENTE: 'pendiente', CONFIRMADO: 'confirmado', EN_PRODUCCION: 'produccion',
  LISTO_DESPACHO: 'listo', DESPACHADO: 'despachado', CANCELADO: 'cancelado',
})[props.estado])
</script>

<template>
  <span class="estado-pedido" :class="`estado-pedido--${nombre}`">{{ etiquetaEstado(estado) }}</span>
</template>

<style scoped>
.estado-pedido { display: inline-flex; align-items: center; width: max-content; padding: .25rem .65rem; border-radius: 999px; font-size: .83rem; font-weight: 700; line-height: 1.3; white-space: nowrap; }
.estado-pedido--pendiente { background: var(--psc-estado-pendiente-fondo); color: var(--psc-estado-pendiente-texto); }
.estado-pedido--confirmado { background: var(--psc-estado-confirmado-fondo); color: var(--psc-estado-confirmado-texto); }
.estado-pedido--produccion { background: var(--psc-estado-produccion-fondo); color: var(--psc-estado-produccion-texto); }
.estado-pedido--listo { background: var(--psc-estado-listo-fondo); color: var(--psc-estado-listo-texto); }
.estado-pedido--despachado { background: var(--psc-estado-despachado-fondo); color: var(--psc-estado-despachado-texto); }
.estado-pedido--cancelado { background: var(--psc-estado-cancelado-fondo); color: var(--psc-estado-cancelado-texto); }
</style>

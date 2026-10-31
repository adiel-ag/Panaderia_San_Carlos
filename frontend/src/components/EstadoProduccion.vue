<script setup lang="ts">
import { computed } from 'vue'
import { etiquetaEstadoProduccion, type EstadoProduccion } from '../api/producciones'

const props = defineProps<{ estado: EstadoProduccion }>()
// Reutiliza los pares de color de los pedidos.
const nombre = computed(() => ({
  PLANIFICADA: 'confirmado', EN_PROCESO: 'produccion', FINALIZADA: 'listo', CANCELADA: 'cancelado',
})[props.estado])
</script>

<template>
  <span class="estado-produccion" :class="`estado-produccion--${nombre}`">{{ etiquetaEstadoProduccion(estado) }}</span>
</template>

<style scoped>
.estado-produccion { display: inline-flex; align-items: center; width: max-content; padding: .25rem .65rem; border-radius: 999px; font-size: .83rem; font-weight: 700; line-height: 1.3; white-space: nowrap; }
.estado-produccion--confirmado { background: var(--psc-estado-confirmado-fondo); color: var(--psc-estado-confirmado-texto); }
.estado-produccion--produccion { background: var(--psc-estado-produccion-fondo); color: var(--psc-estado-produccion-texto); }
.estado-produccion--listo { background: var(--psc-estado-listo-fondo); color: var(--psc-estado-listo-texto); }
.estado-produccion--cancelado { background: var(--psc-estado-cancelado-fondo); color: var(--psc-estado-cancelado-texto); }
</style>

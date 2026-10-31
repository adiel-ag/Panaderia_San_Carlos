<script setup lang="ts">
import { computed } from 'vue'
import type { EstadoReceta } from '../views/recetas'

const props = defineProps<{ estado: EstadoReceta }>()
const etiqueta = computed(() => ({ vigente: 'Vigente', 'sin-receta': 'Sin receta', ambigua: 'Duplicada' })[props.estado])
</script>

<template>
  <span class="estado-receta" :class="`estado-receta--${estado}`" :title="estado === 'ambigua' ? 'Hay más de una receta activa; guarde una versión nueva para corregirlo' : undefined">{{ etiqueta }}</span>
</template>

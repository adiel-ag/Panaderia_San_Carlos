<script setup lang="ts">
import { onMounted, ref } from 'vue'

const estado = ref('Consultando…')

onMounted(async () => {
  try {
    const respuesta = await fetch('api/health')
    if (!respuesta.ok) throw new Error('Error de la API')

    const datos: unknown = await respuesta.json()
    estado.value =
      typeof datos === 'object' &&
      datos !== null &&
      'status' in datos &&
      typeof datos.status === 'string'
        ? datos.status
        : 'Respuesta sin estado'
  } catch {
    estado.value = 'No se pudo consultar la API'
  }
})
</script>

<template>
  <main class="inicio">
    <h1>Panadería San Carlos</h1>
    <p>Estado de la API: <span aria-live="polite">{{ estado }}</span></p>
  </main>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import Button from 'primevue/button'

const router = useRouter()
</script>

<template>
  <section class="pagina">
    <h1>Sin permiso</h1>
    <p>No tiene permiso para acceder a esta pantalla.</p>
    <Button label="Volver a Inicio" @click="router.push({ name: 'inicio' })" />
  </section>
</template>

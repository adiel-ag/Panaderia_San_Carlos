<script setup lang="ts">
import { useTema } from '../tema/useTema'

const { oscuro, alternar } = useTema()
</script>

<template>
  <button class="boton-tema" type="button" aria-label="Modo oscuro" :title="oscuro ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'" :aria-pressed="oscuro" @click="alternar">
    <i :class="oscuro ? 'pi pi-sun' : 'pi pi-moon'" aria-hidden="true" />
  </button>
</template>

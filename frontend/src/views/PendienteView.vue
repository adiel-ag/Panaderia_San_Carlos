<script setup lang="ts">
import { useRoute } from 'vue-router'
import IlustracionPan from '../components/IlustracionPan.vue'

const route = useRoute()
</script>

<template>
  <section class="pagina estado-vacio">
    <IlustracionPan />
    <h1>{{ route.meta.titulo }}</h1>
    <p>Esta pantalla se construirá en una fase siguiente.</p>
  </section>
</template>

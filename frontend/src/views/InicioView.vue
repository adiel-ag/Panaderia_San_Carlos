<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { etiquetaRol } from '../api/roles'
import { opcionesMenu } from '../router/menu'
import { useSesionStore } from '../stores/sesion'
import IlustracionPan from '../components/IlustracionPan.vue'

const sesion = useSesionStore()
const modulos = computed(() => opcionesMenu(sesion.rol).filter((item) => item.nombre !== 'inicio'))
</script>

<template>
  <section class="pagina pagina-inicio">
    <div class="saludo">
      <div>
        <h1>Hola, {{ sesion.usuario?.nombre }}</h1>
        <p class="subtitulo">{{ etiquetaRol(sesion.rol) }} · Panadería San Carlos</p>
      </div>
      <IlustracionPan />
    </div>
    <h2>Módulos disponibles</h2>
    <nav class="lista-modulos" aria-label="Módulos disponibles">
      <RouterLink v-for="opcion in modulos" :key="opcion.nombre" :to="{ name: opcion.nombre }" class="fila-modulo">
        <i :class="opcion.icono" aria-hidden="true" />
        <span class="fila-modulo-texto"><strong>{{ opcion.etiqueta }}</strong><span>{{ opcion.descripcion }}</span></span>
        <i class="pi pi-arrow-right" aria-hidden="true" />
      </RouterLink>
    </nav>
  </section>
</template>

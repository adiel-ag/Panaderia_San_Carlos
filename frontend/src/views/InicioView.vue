<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { etiquetaRol } from '../api/roles'
import { opcionesMenu } from '../router/menu'
import { useSesionStore } from '../stores/sesion'

const sesion = useSesionStore()
</script>

<template>
  <section class="pagina">
    <h1>Hola, {{ sesion.usuario?.nombre }}</h1>
    <p class="subtitulo">{{ etiquetaRol(sesion.rol) }}</p>
    <h2>Módulos disponibles</h2>
    <nav class="accesos" aria-label="Accesos rápidos">
      <RouterLink v-for="opcion in opcionesMenu(sesion.rol).filter((item) => item.nombre !== 'inicio')" :key="opcion.nombre" :to="{ name: opcion.nombre }" class="acceso-rapido">
        <i :class="opcion.icono" aria-hidden="true" /><span>{{ opcion.etiqueta }}</span><i class="pi pi-arrow-right" aria-hidden="true" />
      </RouterLink>
    </nav>
  </section>
</template>

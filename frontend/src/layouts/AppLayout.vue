<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Drawer from 'primevue/drawer'
import { useToast } from 'primevue/usetoast'
import { etiquetaRol } from '../api/roles'
import { opcionesMenu } from '../router/menu'
import { useSesionStore } from '../stores/sesion'
import BotonTema from '../components/BotonTema.vue'
import LogoPanaderia from '../components/LogoPanaderia.vue'

const sesion = useSesionStore()
const router = useRouter()
const route = useRoute()
const toast = useToast()
const menuAbierto = ref(false)
const cerrando = ref(false)

async function cerrarSesion(): Promise<void> {
  if (cerrando.value) return
  cerrando.value = true
  try {
    await sesion.cerrarSesion()
  } catch {
    toast.add({
      severity: 'warn', summary: 'Cierre pendiente',
      detail: 'No se pudo confirmar el cierre de sesión con el servidor. Se reintentará automáticamente.',
      life: 6000,
    })
  } finally {
    await router.replace({ name: 'login' })
    cerrando.value = false
  }
}
</script>

<template>
  <div class="app-layout">
    <header class="barra-superior">
      <Button class="boton-menu" icon="pi pi-bars" aria-label="Abrir menú" text @click="menuAbierto = true" />
      <RouterLink class="marca" :to="{ name: 'inicio' }"><LogoPanaderia /></RouterLink>
      <div class="barra-usuario">
        <span class="identidad"><strong>{{ sesion.usuario?.nombre }}</strong><small>{{ etiquetaRol(sesion.rol) }}</small></span>
        <RouterLink class="enlace-clave" :to="{ name: 'mi-clave' }">Cambiar contraseña</RouterLink>
        <BotonTema />
        <Button id="cerrar-sesion" label="Cerrar sesión" text :loading="cerrando" @click="cerrarSesion" />
      </div>
    </header>
    <div class="app-cuerpo">
      <aside class="navegacion-lateral" aria-label="Módulos">
        <nav>
          <RouterLink v-for="opcion in opcionesMenu(sesion.rol)" :key="opcion.nombre" class="enlace-menu" :class="{ 'router-link-active': opcion.nombre === 'pedidos' && route.name === 'pedido' }" :data-modulo="opcion.nombre" :to="{ name: opcion.nombre }">
            <i :class="opcion.icono" aria-hidden="true" />{{ opcion.etiqueta }}
          </RouterLink>
        </nav>
      </aside>
      <main class="contenido"><RouterView /></main>
    </div>
    <Drawer v-model:visible="menuAbierto" header="Panadería San Carlos" class="cajon-menu">
      <nav aria-label="Módulos">
        <RouterLink v-for="opcion in opcionesMenu(sesion.rol)" :key="opcion.nombre" class="enlace-menu" :class="{ 'router-link-active': opcion.nombre === 'pedidos' && route.name === 'pedido' }" :data-modulo="opcion.nombre" :to="{ name: opcion.nombre }" @click="menuAbierto = false">
          <i :class="opcion.icono" aria-hidden="true" />{{ opcion.etiqueta }}
        </RouterLink>
      </nav>
      <RouterLink class="enlace-menu" :to="{ name: 'mi-clave' }" @click="menuAbierto = false">Cambiar contraseña</RouterLink>
    </Drawer>
  </div>
</template>

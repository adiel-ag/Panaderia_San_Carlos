<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Drawer from 'primevue/drawer'
import Menu from 'primevue/menu'
import type { MenuItem } from 'primevue/menuitem'
import { useToast } from 'primevue/usetoast'
import { etiquetaRol } from '../api/roles'
import { seccionesMenu } from '../router/menu'
import { useSesionStore } from '../stores/sesion'
import { useTema } from '../tema/useTema'
import LogoPanaderia from '../components/LogoPanaderia.vue'
import PieInstitucional from '../components/PieInstitucional.vue'

const sesion = useSesionStore()
const router = useRouter()
const route = useRoute()
const toast = useToast()
const menuAbierto = ref(false)
const cerrando = ref(false)
const menuUsuario = ref<InstanceType<typeof Menu> | null>(null)
const menuUsuarioAbierto = ref(false)
const { oscuro, alternar: alternarTema } = useTema()
const secciones = computed(() => seccionesMenu(sesion.rol))

// Dos iniciales del nombre para el avatar; si no hay nombre, el del usuario.
const iniciales = computed(() => {
  const nombre = (sesion.usuario?.nombre ?? sesion.usuario?.username ?? '').trim()
  const partes = nombre.split(/\s+/).filter(Boolean)
  return ((partes[0]?.[0] ?? '') + (partes.length > 1 ? partes[partes.length - 1]?.[0] ?? '' : '')).toUpperCase() || '?'
})
// El rol encabeza el menú como etiqueta no interactiva.
const opcionesUsuario = computed<MenuItem[]>(() => [{
  label: etiquetaRol(sesion.rol),
  items: [
    { key: 'clave', idElemento: 'cambiar-clave', label: 'Cambiar contraseña', icon: 'pi pi-key', command: () => { void router.push({ name: 'mi-clave' }) } },
    { key: 'tema', idElemento: 'alternar-tema', label: oscuro.value ? 'Modo claro' : 'Modo oscuro', icon: oscuro.value ? 'pi pi-sun' : 'pi pi-moon', command: alternarTema },
    { key: 'salir', idElemento: 'cerrar-sesion', label: cerrando.value ? 'Cerrando sesión…' : 'Cerrar sesión', icon: 'pi pi-sign-out', disabled: cerrando.value, command: () => { void cerrarSesion() } },
  ],
}])
// aria-expanded cambia al instante; @show/@hide lo corrigen si el menú se cierra por fuera (clic fuera, Escape).
function alternarMenuUsuario(evento: Event): void {
  menuUsuarioAbierto.value = !menuUsuarioAbierto.value
  menuUsuario.value?.toggle(evento)
}

// Las rutas hijas (detalle, nuevo) mantienen resaltado su módulo.
function activoPorHija(modulo: string): boolean {
  // El detalle abierto desde Despacho resalta Despacho.
  const desdeDespacho = (route.name === 'pedido' || route.name === 'hoja-despacho') && /^\/despacho(?:\?|$)/.test(String(route.query?.volver ?? ''))
  if (modulo === 'pedidos') return ((route.name === 'pedido' || route.name === 'hoja-despacho') && !desdeDespacho) || route.name === 'pedido-nuevo'
  if (modulo === 'produccion') return route.name === 'produccion-detalle' || route.name === 'hoja-produccion'
  if (modulo === 'recetas') return route.name === 'receta' || route.name === 'receta-nueva'
  if (modulo === 'despacho') return desdeDespacho
  return false
}

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
    <a class="enlace-saltar" href="#contenido-principal">Saltar al contenido</a>
    <header class="barra-superior">
      <Button class="boton-menu" icon="pi pi-bars" aria-label="Abrir menú" text @click="menuAbierto = true" />
      <RouterLink class="marca" :to="{ name: 'inicio' }" aria-label="Panadería San Carlos, ir a Inicio"><LogoPanaderia /></RouterLink>
      <div class="barra-usuario">
        <button
          id="boton-usuario" class="boton-usuario" type="button" aria-haspopup="menu" aria-controls="menu-usuario"
          :aria-expanded="menuUsuarioAbierto" :aria-label="`Cuenta de ${sesion.usuario?.nombre ?? ''}, ${etiquetaRol(sesion.rol)}`" @click="alternarMenuUsuario"
        >
          <span class="avatar-usuario" aria-hidden="true">{{ iniciales }}</span>
          <span class="nombre-cuenta">{{ sesion.usuario?.nombre }}</span>
          <i class="pi pi-angle-down" aria-hidden="true" />
        </button>
        <Menu
          id="menu-usuario" ref="menuUsuario" :model="opcionesUsuario" popup class="menu-usuario" aria-label="Opciones de la cuenta"
          @show="menuUsuarioAbierto = true" @hide="menuUsuarioAbierto = false"
        >
          <template #submenulabel="{ item }">
            <span class="menu-usuario-cabecera"><strong>{{ sesion.usuario?.nombre }}</strong><small>{{ item.label }}</small></span>
          </template>
          <template #item="{ item, props }">
            <a :id="item.idElemento" class="menu-usuario-opcion" v-bind="props.action">
              <i :class="item.icon" aria-hidden="true" /><span>{{ item.label }}</span>
            </a>
          </template>
        </Menu>
      </div>
    </header>
    <div class="app-cuerpo">
      <aside class="navegacion-lateral" aria-label="Módulos">
        <nav>
          <div v-for="seccion in secciones" :key="seccion.grupo" class="grupo-menu" :data-grupo="seccion.grupo">
            <p :id="`grupo-lateral-${seccion.grupo}`" class="grupo-menu-titulo">{{ seccion.etiqueta }}</p>
            <ul :aria-labelledby="`grupo-lateral-${seccion.grupo}`">
              <li v-for="opcion in seccion.opciones" :key="opcion.nombre">
                <RouterLink class="enlace-menu" :class="{ 'router-link-active': activoPorHija(opcion.nombre) }" :data-modulo="opcion.nombre" :to="{ name: opcion.nombre }">
                  <i :class="opcion.icono" aria-hidden="true" />{{ opcion.etiqueta }}
                </RouterLink>
              </li>
            </ul>
          </div>
        </nav>
      </aside>
      <main id="contenido-principal" class="contenido" tabindex="-1"><RouterView /></main>
    </div>
    <PieInstitucional />
    <Drawer v-model:visible="menuAbierto" header="Panadería San Carlos" class="cajon-menu">
      <nav aria-label="Módulos">
        <div v-for="seccion in secciones" :key="seccion.grupo" class="grupo-menu" :data-grupo="seccion.grupo">
          <p :id="`grupo-cajon-${seccion.grupo}`" class="grupo-menu-titulo">{{ seccion.etiqueta }}</p>
          <ul :aria-labelledby="`grupo-cajon-${seccion.grupo}`">
            <li v-for="opcion in seccion.opciones" :key="opcion.nombre">
              <RouterLink class="enlace-menu" :class="{ 'router-link-active': activoPorHija(opcion.nombre) }" :data-modulo="opcion.nombre" :to="{ name: opcion.nombre }" @click="menuAbierto = false">
                <i :class="opcion.icono" aria-hidden="true" />{{ opcion.etiqueta }}
              </RouterLink>
            </li>
          </ul>
        </div>
      </nav>
    </Drawer>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { ApiError } from '../api/cliente'
import { rutaSegura } from '../router/guardia'
import { useSesionStore } from '../stores/sesion'
import BotonTema from '../components/BotonTema.vue'
import LogoPanaderia from '../components/LogoPanaderia.vue'
import IlustracionPan from '../components/IlustracionPan.vue'
import PieInstitucional from '../components/PieInstitucional.vue'

const route = useRoute()
const router = useRouter()
const sesion = useSesionStore()
const usuario = ref('')
const contrasena = ref('')
const enviando = ref(false)
const error = ref('')
const mostrarErrores = ref(false)

async function ingresar(): Promise<void> {
  if (enviando.value) return
  mostrarErrores.value = true
  if (!usuario.value.trim() || !contrasena.value) {
    await nextTick()
    document.getElementById(!usuario.value.trim() ? 'usuario' : 'contrasena')?.focus()
    return
  }
  enviando.value = true
  error.value = ''
  try {
    await sesion.iniciarSesion(usuario.value, contrasena.value)
    await router.replace(rutaSegura(route.query.redirect))
  } catch (causa) {
    error.value = causa instanceof ApiError
      ? causa.status === 0 ? 'No se pudo conectar con el servidor.' : causa.message
      : 'No se pudo iniciar sesión.'
    contrasena.value = ''
    await nextTick()
    document.getElementById('acceso-error')?.focus()
  } finally {
    enviando.value = false
  }
}
</script>

<template>
  <main class="acceso">
    <BotonTema class="acceso-tema" />
    <Card class="acceso-card">
      <template #title><IlustracionPan class="acceso-ilustracion" /><h1 class="acceso-titulo"><LogoPanaderia tamano="grande" /></h1></template>
      <template #subtitle>Sistema de gestión · Iniciar sesión</template>
      <template #content>
        <Message v-if="route.query.aviso === 'sin-conexion'" severity="warn">
          No se pudo comprobar la sesión. Verifique la conexión e intente de nuevo.
        </Message>
        <Message v-if="error" id="acceso-error" severity="error" role="alert" tabindex="-1">{{ error }}</Message>
        <form class="formulario" @submit.prevent="ingresar">
          <div class="campo">
            <label for="usuario">Usuario</label>
            <InputText id="usuario" v-model="usuario" autocomplete="username" autofocus fluid :invalid="mostrarErrores && !usuario.trim()" :aria-invalid="mostrarErrores && !usuario.trim()" :aria-describedby="mostrarErrores && !usuario.trim() ? 'usuario-error' : undefined" />
            <small v-if="mostrarErrores && !usuario.trim()" id="usuario-error" class="error-campo">Ingrese su usuario.</small>
          </div>
          <div class="campo">
            <label for="contrasena">Contraseña</label>
            <Password input-id="contrasena" v-model="contrasena" :feedback="false" toggle-mask autocomplete="current-password" fluid :invalid="mostrarErrores && !contrasena" :input-props="{ 'aria-invalid': mostrarErrores && !contrasena, 'aria-describedby': mostrarErrores && !contrasena ? 'contrasena-error' : undefined }" />
            <small v-if="mostrarErrores && !contrasena" id="contrasena-error" class="error-campo">Ingrese su contraseña.</small>
          </div>
          <Button type="submit" label="Iniciar sesión" :loading="enviando" :disabled="enviando" fluid />
        </form>
      </template>
    </Card>
    <PieInstitucional />
  </main>
</template>

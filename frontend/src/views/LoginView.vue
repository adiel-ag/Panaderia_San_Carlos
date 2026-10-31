<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { ApiError } from '../api/cliente'
import { rutaSegura } from '../router/guardia'
import { useSesionStore } from '../stores/sesion'

const route = useRoute()
const router = useRouter()
const sesion = useSesionStore()
const usuario = ref('')
const contrasena = ref('')
const enviando = ref(false)
const error = ref('')

async function ingresar(): Promise<void> {
  if (!usuario.value || !contrasena.value || enviando.value) return
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
  } finally {
    enviando.value = false
  }
}
</script>

<template>
  <main class="acceso">
    <Card class="acceso-card">
      <template #title>Panadería San Carlos</template>
      <template #subtitle>Iniciar sesión</template>
      <template #content>
        <Message v-if="route.query.aviso === 'sin-conexion'" severity="warn">
          No se pudo comprobar la sesión. Verifique la conexión e intente de nuevo.
        </Message>
        <Message v-if="error" severity="error" aria-live="polite">{{ error }}</Message>
        <form class="formulario" @submit.prevent="ingresar">
          <div class="campo">
            <label for="usuario">Usuario</label>
            <InputText id="usuario" v-model="usuario" autocomplete="username" autofocus fluid />
          </div>
          <div class="campo">
            <label for="contrasena">Contraseña</label>
            <Password input-id="contrasena" v-model="contrasena" :feedback="false" toggle-mask autocomplete="current-password" fluid />
          </div>
          <Button type="submit" label="Iniciar sesión" :loading="enviando" :disabled="!usuario || !contrasena || enviando" fluid />
        </form>
      </template>
    </Card>
  </main>
</template>

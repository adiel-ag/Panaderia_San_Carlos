<script setup lang="ts">
import { ref } from 'vue'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { useToast } from 'primevue/usetoast'
import { ApiError, api } from '../api/cliente'
import { validarCambioClave, type ErroresCambioClave } from './validarCambioClave'

const toast = useToast()
const actual = ref('')
const nueva = ref('')
const confirmar = ref('')
const errores = ref<ErroresCambioClave>({})
const errorServidor = ref('')
const enviando = ref(false)

async function guardar(): Promise<void> {
  errores.value = validarCambioClave(actual.value, nueva.value, confirmar.value)
  errorServidor.value = ''
  if (Object.keys(errores.value).length || enviando.value) return
  enviando.value = true
  try {
    await api<{ mensaje: string }>('auth/clave', {
      metodo: 'PUT', cuerpo: { claveActual: actual.value, claveNueva: nueva.value },
    })
    toast.add({ severity: 'success', summary: 'Contraseña actualizada', life: 5000 })
    actual.value = ''
    nueva.value = ''
    confirmar.value = ''
    errores.value = {}
  } catch (causa) {
    if (causa instanceof ApiError && causa.status === 400) errorServidor.value = causa.message
  } finally {
    enviando.value = false
  }
}
</script>

<template>
  <section class="pagina pagina-formulario">
    <h1>Cambiar contraseña</h1>
    <Message v-if="errorServidor" severity="error" aria-live="polite">{{ errorServidor }}</Message>
    <form class="formulario" @submit.prevent="guardar">
      <div class="campo">
        <label for="clave-actual">Contraseña actual</label>
        <Password input-id="clave-actual" v-model="actual" :feedback="false" toggle-mask autocomplete="current-password" fluid :invalid="!!errores.actual" />
        <small v-if="errores.actual" class="error-campo">{{ errores.actual }}</small>
      </div>
      <div class="campo">
        <label for="clave-nueva">Contraseña nueva</label>
        <Password input-id="clave-nueva" v-model="nueva" toggle-mask autocomplete="new-password" fluid :invalid="!!errores.nueva" />
        <small v-if="errores.nueva" class="error-campo">{{ errores.nueva }}</small>
      </div>
      <div class="campo">
        <label for="clave-confirmar">Confirmar contraseña nueva</label>
        <Password input-id="clave-confirmar" v-model="confirmar" :feedback="false" toggle-mask autocomplete="new-password" fluid :invalid="!!errores.confirmar" />
        <small v-if="errores.confirmar" class="error-campo">{{ errores.confirmar }}</small>
      </div>
      <Button type="submit" label="Actualizar contraseña" :loading="enviando" :disabled="enviando" />
    </form>
  </section>
</template>

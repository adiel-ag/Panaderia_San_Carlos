<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'

const route = useRoute()
const router = useRouter()
const numero = ref('')
const error = ref('')

function ir(): void {
  const valor = numero.value.trim().replace(/^#/, '')
  if (!/^[1-9]\d*$/.test(valor) || !Number.isSafeInteger(Number(valor))) {
    error.value = 'Escriba un número de pedido válido'
    return
  }
  error.value = ''
  void router.push({ name: 'pedido', params: { id: Number(valor) }, query: { volver: route.fullPath } })
}
</script>

<template>
  <form class="ir-pedido" @submit.prevent="ir">
    <label for="numero-pedido">Ir al pedido</label>
    <div class="ir-pedido-controles">
      <InputText id="numero-pedido" v-model="numero" inputmode="numeric" placeholder="#Número" aria-label="Número de pedido" @input="error = ''" />
      <Button label="Ir" type="submit" outlined />
    </div>
    <small v-if="error" class="error-campo" role="alert">{{ error }}</small>
  </form>
</template>

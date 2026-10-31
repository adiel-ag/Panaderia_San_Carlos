<template>
  <RouterView />
  <Toast />
</template>

<script setup lang="ts">
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import { RouterView } from 'vue-router'
import { configurarCliente } from './api/cliente'
import router from './router'
import { useSesionStore } from './stores/sesion'

const toast = useToast()
const sesion = useSesionStore()

configurarCliente({
  alAviso: ({ severidad, titulo, detalle }) => {
    toast.add({ severity: severidad, summary: titulo, detail: detalle, life: 5000 })
  },
  alNoAutenticado: () => {
    sesion.limpiar()
    if (router.currentRoute.value.name !== 'login') {
      void router.push({
        name: 'login',
        query: { redirect: router.currentRoute.value.fullPath },
      })
    }
  },
})
</script>

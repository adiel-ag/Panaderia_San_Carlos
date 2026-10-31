<script setup lang="ts">
withDefaults(defineProps<{ tamano?: 'normal' | 'grande' }>(), { tamano: 'normal' })
</script>

<template>
  <span class="logo-panaderia" :class="`logo-panaderia--${tamano}`">
    <svg viewBox="0 0 28 28" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M4 22 C4 11 9 6 14 6 C19 6 24 11 24 22 Z" />
      <path d="M10 12 l2 6 M15 10 l1.5 7" />
    </svg>
    <span>Panadería San Carlos</span>
  </span>
</template>

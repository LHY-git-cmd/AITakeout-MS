<script setup lang="ts">
import { ArrowLeft } from '@lucide/vue'
</script>
<template>
  <main class="not-found">
    <strong>404</strong>
    <h1>页面不存在</h1>
    <RouterLink to="/"><ArrowLeft :size="18" /> 返回点餐</RouterLink>
  </main>
</template>

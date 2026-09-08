<script setup lang="ts">
import { ref } from 'vue'
import { Utensils } from '@lucide/vue'

defineProps<{ src?: string | null; alt: string }>()
const failed = ref(false)
</script>

<template>
  <div class="product-image">
    <img v-if="src && !failed" :src="src" :alt="alt" loading="lazy" @error="failed = true" />
    <div v-else class="product-image__fallback" role="img" :aria-label="alt">
      <Utensils :size="30" aria-hidden="true" />
    </div>
  </div>
</template>

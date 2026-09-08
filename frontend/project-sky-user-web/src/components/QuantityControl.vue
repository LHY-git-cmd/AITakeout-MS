<script setup lang="ts">
import { Minus, Plus } from '@lucide/vue'

defineProps<{ count: number; busy?: boolean }>()
defineEmits<{ increment: []; decrement: [] }>()
</script>

<template>
  <div class="quantity-control" :aria-label="`数量 ${count}`">
    <button type="button" aria-label="减少数量" :disabled="busy" @click="$emit('decrement')">
      <Minus :size="16" aria-hidden="true" />
    </button>
    <span>{{ count }}</span>
    <button type="button" aria-label="增加数量" :disabled="busy" @click="$emit('increment')">
      <Plus :size="16" aria-hidden="true" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { Clock3, Trash2 } from '@lucide/vue'

defineProps<{ items: string[] }>()
defineEmits<{ select: [keyword: string]; clear: [] }>()
</script>

<template>
  <section v-if="items.length" class="search-history" aria-labelledby="search-history-title">
    <header>
      <h2 id="search-history-title"><Clock3 :size="17" aria-hidden="true" />最近搜索</h2>
      <button type="button" aria-label="清空搜索历史" @click="$emit('clear')"><Trash2 :size="16" aria-hidden="true" />清空</button>
    </header>
    <div class="search-history__items">
      <button v-for="item in items" :key="item" type="button" @click="$emit('select', item)">{{ item }}</button>
    </div>
  </section>
</template>

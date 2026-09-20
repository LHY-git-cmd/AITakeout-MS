<script setup lang="ts">
import { CheckCircle2, Clock3 } from '@lucide/vue'
import type { TimelineItem } from '@/api/aftersale'

defineProps<{ items: TimelineItem[]; loading?: boolean }>()
</script>

<template>
  <section class="order-timeline" aria-labelledby="timeline-title">
    <header><Clock3 :size="19" aria-hidden="true" /><h2 id="timeline-title">订单进度</h2></header>
    <p v-if="loading" role="status">正在加载订单进度...</p>
    <ol v-else-if="items.length">
      <li v-for="item in items" :key="item.id">
        <CheckCircle2 :size="18" aria-hidden="true" />
        <div><strong>{{ item.message }}</strong><time>{{ item.eventTime }}</time></div>
      </li>
    </ol>
    <p v-else>暂无进度记录</p>
  </section>
</template>

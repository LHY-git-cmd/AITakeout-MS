<script setup lang="ts">
import { onMounted } from 'vue'
import { Bell, CheckCheck, RefreshCw } from '@lucide/vue'
import { useRouter } from 'vue-router'
import PageScaffold from '@/components/PageScaffold.vue'
import { useNotificationStore } from '@/stores/notification'

const store = useNotificationStore()
const router = useRouter()

async function open(item: typeof store.items[number]) {
  await store.markRead(item)
  if (item.orderId) await router.push(`/orders/${item.orderId}`)
}

onMounted(() => { void store.refresh() })
</script>

<template>
  <PageScaffold title="通知中心" description="订单、退款和售后进度都会保存在这里">
    <template #action><button v-if="store.unreadCount" class="text-action" type="button" @click="store.markAllRead"><CheckCheck :size="18" />全部已读</button></template>
    <p v-if="store.error" class="page-error" role="alert">{{ store.error }}</p>
    <div v-if="store.loading" class="content-empty"><RefreshCw class="spin" :size="28" />正在加载通知...</div>
    <div v-else-if="!store.items.length" class="content-empty"><Bell :size="34" /><strong>暂无通知</strong><span>订单状态变化后会在这里提醒你</span></div>
    <ol v-else class="notification-list">
      <li v-for="item in store.items" :key="item.id" :class="{ 'is-unread': !item.read }">
        <button type="button" @click="open(item)">
          <span class="notification-list__dot" aria-hidden="true" />
          <span><strong>{{ item.title }}</strong><small>{{ item.content }}</small><time>{{ item.createTime }}</time></span>
        </button>
      </li>
    </ol>
    <button v-if="store.hasMore && store.items.length" class="load-more" type="button" :disabled="store.loadingMore" @click="store.loadMore">
      {{ store.loadingMore ? '加载中...' : '加载更早通知' }}
    </button>
  </PageScaffold>
</template>

<script setup lang="ts">
/** 提供可拖拽调整宽度的 AI 聊天占位栏，聊天内容将在后续需求中补充。 */
import { onBeforeUnmount } from 'vue'
import { Sparkles } from '@lucide/vue'
import { clampAiPanelWidth } from '@/utils/menuLayout'

const props = defineProps<{ width: number }>()
const emit = defineEmits<{ 'update:width': [width: number] }>()

const MIN_WIDTH = 260
const MAX_WIDTH = 520
let startX = 0
let startWidth = 0

function updateWidth(clientX: number) {
  emit('update:width', clampAiPanelWidth(startWidth + startX - clientX, MIN_WIDTH, MAX_WIDTH))
}

function stopResize() {
  window.removeEventListener('pointermove', handlePointerMove)
  window.removeEventListener('pointerup', stopResize)
}

function handlePointerMove(event: PointerEvent) {
  updateWidth(event.clientX)
}

function startResize(event: PointerEvent) {
  startX = event.clientX
  startWidth = props.width
  window.addEventListener('pointermove', handlePointerMove)
  window.addEventListener('pointerup', stopResize, { once: true })
}

function handleResizeKey(event: KeyboardEvent) {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
  event.preventDefault()
  const delta = event.key === 'ArrowLeft' ? 20 : -20
  emit('update:width', clampAiPanelWidth(props.width + delta, MIN_WIDTH, MAX_WIDTH))
}

onBeforeUnmount(stopResize)
</script>

<template>
  <aside class="ai-chat-panel" aria-label="AI 聊天栏">
    <div
      class="ai-chat-panel__resize"
      role="separator"
      aria-label="调整 AI 聊天栏宽度"
      aria-orientation="vertical"
      :aria-valuenow="width"
      :aria-valuemin="MIN_WIDTH"
      :aria-valuemax="MAX_WIDTH"
      tabindex="0"
      @pointerdown.prevent="startResize"
      @keydown="handleResizeKey"
    />
    <header><Sparkles :size="20" aria-hidden="true" /><strong>AI 助手</strong></header>
    <div class="ai-chat-panel__content">
      <p>不知道吃什么？问饱饱</p>
    </div>
  </aside>
</template>

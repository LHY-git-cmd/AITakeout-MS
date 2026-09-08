<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { LoaderCircle, Plus, X } from '@lucide/vue'
import ProductImage from './ProductImage.vue'
import { getSetmealDetail, type Dish, type MenuProduct, type SetmealDetail } from '@/api/menu'
import { useCartStore } from '@/stores/cart'

const props = defineProps<{ product: MenuProduct | null; disabled?: boolean }>()
const emit = defineEmits<{ close: [] }>()
const cartStore = useCartStore()
const selections = ref<Record<string, string>>({})
const detail = ref<SetmealDetail | null>(null)
const loading = ref(false)
const adding = ref(false)
const error = ref('')

const flavorGroups = computed(() => {
  if (props.product?.productType !== 'dish') return []
  return (props.product as Dish).flavors.map((flavor) => {
    try {
      return { name: flavor.name, options: JSON.parse(flavor.value) as string[] }
    } catch {
      return { name: flavor.name, options: [] }
    }
  })
})

const canAdd = computed(() => !props.disabled && flavorGroups.value.every((group) => Boolean(selections.value[group.name])))
const serializedFlavor = computed(() => flavorGroups.value.map((group) => selections.value[group.name]).join(','))

watch(() => props.product, async (product) => {
  selections.value = {}
  detail.value = null
  error.value = ''
  if (product?.productType === 'setmeal') {
    loading.value = true
    try {
      detail.value = await getSetmealDetail(product.id)
    } catch {
      error.value = '套餐内容加载失败'
    } finally {
      loading.value = false
    }
  }
}, { immediate: true })

async function addToCart() {
  if (!props.product || !canAdd.value) return
  adding.value = true
  error.value = ''
  try {
    await cartStore.add(props.product, serializedFlavor.value || undefined)
    emit('close')
  } catch {
    error.value = '加入购物车失败，请稍后重试'
  } finally {
    adding.value = false
  }
}
</script>

<template>
  <Transition name="dialog">
    <div v-if="product" class="dialog-layer" @click.self="$emit('close')">
      <section class="product-dialog" role="dialog" aria-modal="true" :aria-label="product.name">
        <button class="product-dialog__close" type="button" aria-label="关闭商品详情" @click="$emit('close')">
          <X :size="21" />
        </button>
        <ProductImage :src="product.image" :alt="product.name" />
        <div class="product-dialog__content">
          <h2>{{ product.name }}</h2>
          <p>{{ product.description || '新鲜制作，欢迎品尝' }}</p>

          <div v-if="product.productType === 'dish'" class="flavor-groups">
            <fieldset v-for="group in flavorGroups" :key="group.name">
              <legend>{{ group.name }}</legend>
              <div>
                <label v-for="option in group.options" :key="option" :class="{ 'is-selected': selections[group.name] === option }">
                  <input v-model="selections[group.name]" type="radio" :name="group.name" :value="option" />
                  {{ option }}
                </label>
              </div>
            </fieldset>
          </div>

          <div v-else class="setmeal-detail">
            <div v-if="loading" class="inline-loading"><LoaderCircle class="spin" :size="18" /> 加载套餐内容</div>
            <ul v-else-if="detail?.setmealDishes?.length">
              <li v-for="dish in detail.setmealDishes" :key="dish.id">
                <span>{{ dish.name }}</span><b>x{{ dish.copies }}</b>
              </li>
            </ul>
          </div>

          <p v-if="error" class="product-dialog__error" role="alert">{{ error }}</p>
          <div class="product-dialog__footer">
            <strong><small>¥</small>{{ Number(product.price).toFixed(2) }}</strong>
            <button type="button" :disabled="!canAdd || adding || loading" @click="addToCart">
              <LoaderCircle v-if="adding" class="spin" :size="18" />
              <Plus v-else :size="18" />
              {{ disabled ? '门店已打烊' : flavorGroups.length && !canAdd ? '请选择规格' : '加入购物车' }}
            </button>
          </div>
        </div>
      </section>
    </div>
  </Transition>
</template>

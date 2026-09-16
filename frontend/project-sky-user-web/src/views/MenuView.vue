<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { LoaderCircle, PackageOpen, Plus, RefreshCw, Search } from '@lucide/vue'
import AiChatPanel from '@/components/AiChatPanel.vue'
import ProductDialog from '@/components/ProductDialog.vue'
import ProductImage from '@/components/ProductImage.vue'
import { getCategories, getProducts, getShopStatus, type Category, type MenuProduct } from '@/api/menu'
import { useCartStore } from '@/stores/cart'
import { filterMenuProducts } from '@/utils/menuLayout'

const cartStore = useCartStore()
const categories = ref<Category[]>([])
const activeCategoryId = ref<number | null>(null)
const productCache = ref<Record<number, MenuProduct[]>>({})
const loadingCategories = ref(true)
const loadingProducts = ref(false)
const error = ref('')
const shopStatus = ref(1)
const selectedProduct = ref<MenuProduct | null>(null)
const addingId = ref<number | null>(null)
const searchKeyword = ref('')
const aiPanelWidth = ref(320)

const products = computed(() => activeCategoryId.value ? productCache.value[activeCategoryId.value] ?? [] : [])
const visibleProducts = computed(() => filterMenuProducts(products.value, searchKeyword.value))
const isOpen = computed(() => shopStatus.value === 1)

async function loadMenu() {
  loadingCategories.value = true
  error.value = ''
  try {
    const [status, categoryList] = await Promise.all([getShopStatus(), getCategories()])
    shopStatus.value = status
    categories.value = categoryList
    activeCategoryId.value = categoryList[0]?.id ?? null
    if (categoryList[0]) await selectCategory(categoryList[0])
  } catch {
    error.value = '菜单加载失败，请检查后端服务后重试'
  } finally {
    loadingCategories.value = false
  }
}

async function selectCategory(category: Category) {
  activeCategoryId.value = category.id
  if (productCache.value[category.id]) return
  loadingProducts.value = true
  error.value = ''
  try {
    productCache.value[category.id] = await getProducts(category)
  } catch {
    error.value = '商品加载失败，请稍后重试'
  } finally {
    loadingProducts.value = false
  }
}

async function handleProduct(product: MenuProduct) {
  if (!isOpen.value) return
  if (product.productType === 'setmeal' || product.flavors.length) {
    selectedProduct.value = product
    return
  }
  addingId.value = product.id
  try {
    await cartStore.add(product)
  } catch {
    error.value = '加入购物车失败，请稍后重试'
  } finally {
    addingId.value = null
  }
}

onMounted(loadMenu)
</script>

<template>
  <section class="menu-page">
    <div v-if="error && !categories.length" class="menu-state menu-state--error">
      <RefreshCw :size="30" />
      <strong>{{ error }}</strong>
      <button type="button" @click="loadMenu">重新加载</button>
    </div>

    <div v-else class="menu-workspace" :style="{ '--ai-panel-width': `${aiPanelWidth}px` }">
      <div class="menu-searchbar">
        <Search :size="21" aria-hidden="true" />
        <input v-model="searchKeyword" type="search" placeholder="搜索菜品" aria-label="搜索菜品" />
      </div>

      <aside class="category-list" aria-label="商品分类">
        <template v-if="loadingCategories">
          <span v-for="index in 6" :key="index" class="category-list__skeleton" />
        </template>
        <template v-else>
          <button
            v-for="category in categories"
            :key="category.id"
            type="button"
            :class="{ 'is-active': category.id === activeCategoryId }"
            @click="selectCategory(category)"
          >
            {{ category.name }}
          </button>
        </template>
      </aside>

      <div class="product-area">
        <div v-if="loadingProducts" class="product-loading">
          <LoaderCircle class="spin" :size="24" /> 正在加载商品
        </div>
        <div v-else-if="!visibleProducts.length" class="menu-state">
          <PackageOpen :size="34" />
          <strong>{{ searchKeyword.trim() ? '没有找到相关商品' : '该分类暂无可售商品' }}</strong>
        </div>
        <div v-else class="product-grid">
          <article v-for="product in visibleProducts" :key="`${product.productType}-${product.id}`" class="product-card">
            <button class="product-card__image" type="button" :aria-label="`查看${product.name}`" @click="selectedProduct = product">
              <ProductImage :src="product.image" :alt="product.name" />
            </button>
            <div class="product-card__body">
              <h3>{{ product.name }}</h3>
              <p>{{ product.description || (product.productType === 'setmeal' ? '搭配丰富，优惠组合' : '新鲜制作，欢迎品尝') }}</p>
              <div class="product-card__footer">
                <strong><small>¥</small>{{ Number(product.price).toFixed(2) }}</strong>
                <button
                  type="button"
                  :disabled="!isOpen || addingId === product.id"
                  :aria-label="product.productType === 'dish' && product.flavors.length ? `选择${product.name}规格` : `添加${product.name}`"
                  @click="handleProduct(product)"
                >
                  <LoaderCircle v-if="addingId === product.id" class="spin" :size="17" />
                  <Plus v-else :size="18" />
                  <span v-if="product.productType === 'dish' && product.flavors.length">选规格</span>
                </button>
              </div>
            </div>
          </article>
        </div>
      </div>

      <AiChatPanel v-model:width="aiPanelWidth" />
    </div>

    <div v-if="!isOpen && categories.length" class="closed-shop-banner">门店已打烊，暂时无法加购</div>
    <ProductDialog :product="selectedProduct" :disabled="!isOpen" @close="selectedProduct = null" />
  </section>
</template>

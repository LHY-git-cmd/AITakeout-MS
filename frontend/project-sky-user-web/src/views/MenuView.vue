<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { LoaderCircle, PackageOpen, Plus, RefreshCw, Search } from '@lucide/vue'
import AiChatPanel from '@/components/AiChatPanel.vue'
import ProductDialog from '@/components/ProductDialog.vue'
import ProductImage from '@/components/ProductImage.vue'
import SearchHistory from '@/components/SearchHistory.vue'
import SearchResultList from '@/components/SearchResultList.vue'
import { getCategories, getProducts, getShopStatus, type Category, type MenuProduct } from '@/api/menu'
import type { ProductSearchItem } from '@/api/search'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { useSearchStore } from '@/stores/search'

const cartStore = useCartStore()
const authStore = useAuthStore()
const searchStore = useSearchStore()
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

async function resolveSearchProduct(item: ProductSearchItem): Promise<MenuProduct> {
  if (item.productType === 'dish' && item.hasFlavor) {
    const category: Category = { id: item.categoryId, type: 1, name: item.categoryName, sort: 0 }
    const detailed = (await getProducts(category)).find((product) => product.id === item.id)
    if (detailed) return detailed
  }
  return item.productType === 'dish'
    ? { id: item.id, categoryId: item.categoryId, name: item.name, price: item.price, image: item.image,
        description: item.description, flavors: [], productType: 'dish' }
    : { id: item.id, categoryId: item.categoryId, name: item.name, price: item.price, image: item.image,
        description: item.description, productType: 'setmeal' }
}

async function selectSearchProduct(item: ProductSearchItem) {
  error.value = ''
  try {
    selectedProduct.value = await resolveSearchProduct(item)
  } catch {
    error.value = '商品详情加载失败，请稍后重试'
  }
}

async function addSearchProduct(item: ProductSearchItem) {
  error.value = ''
  try {
    await handleProduct(await resolveSearchProduct(item))
  } catch {
    error.value = '加入购物车失败，请稍后重试'
  }
}

async function submitSearch(keyword = searchKeyword.value) {
  searchKeyword.value = keyword
  await searchStore.search(keyword, authStore.isAuthenticated)
}

function handleSearchInput() {
  if (!searchKeyword.value.trim()) searchStore.reset()
}

async function clearHistory() {
  if (!window.confirm('确定清空全部搜索历史吗？')) return
  await searchStore.clearHistory()
}

onMounted(async () => {
  const scrollY = searchStore.restore('menu')
  searchKeyword.value = searchStore.keyword
  await loadMenu()
  if (authStore.isAuthenticated) await searchStore.loadHistory()
  if (scrollY !== null) {
    await nextTick()
    window.scrollTo({ top: scrollY })
  }
})
onBeforeUnmount(() => searchStore.save('menu', window.scrollY))
watch(() => authStore.isAuthenticated, (authenticated) => {
  if (authenticated) void searchStore.loadHistory()
  else searchStore.history = []
})
</script>

<template>
  <section class="menu-page">
    <div v-if="error && !categories.length" class="menu-state menu-state--error">
      <RefreshCw :size="30" />
      <strong>{{ error }}</strong>
      <button type="button" @click="loadMenu">重新加载</button>
    </div>

    <div v-else class="menu-workspace" :class="{ 'is-searching': searchStore.searched }" :style="{ '--ai-panel-width': aiPanelWidth + 'px' }">
      <form class="menu-searchbar" role="search" @submit.prevent="submitSearch()">
        <Search :size="21" aria-hidden="true" />
        <label class="sr-only" for="menu-product-search">跨分类搜索菜品和套餐</label>
        <input id="menu-product-search" v-model="searchKeyword" type="search" maxlength="64"
          placeholder="搜索全部菜品、套餐或分类" autocomplete="off" @input="handleSearchInput" />
        <button type="submit" :disabled="!searchKeyword.trim() || searchStore.loading">搜索</button>
      </form>
      <SearchHistory v-if="authStore.isAuthenticated && !searchStore.searched" :items="searchStore.history"
        @select="submitSearch" @clear="clearHistory" />

      <aside v-if="!searchStore.searched" class="category-list" aria-label="商品分类">
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

      <div class="product-area" :class="{ 'product-area--search': searchStore.searched }">
        <SearchResultList v-if="searchStore.searched"
          :items="searchStore.items"
          :loading="searchStore.loading"
          :loading-more="searchStore.loadingMore"
          :has-more="searchStore.hasMore"
          :error="searchStore.error"
          :adding-id="addingId"
          :disabled="!isOpen"
          @load-more="searchStore.loadMore"
          @retry="submitSearch()"
          @select="selectSearchProduct"
          @add="addSearchProduct"
        />
        <div v-else-if="loadingProducts" class="product-loading">
          <LoaderCircle class="spin" :size="24" /> 正在加载商品
        </div>
        <div v-else-if="!products.length" class="menu-state">
          <PackageOpen :size="34" />
          <strong>该分类暂无可售商品</strong>
        </div>
        <div v-else class="product-grid">
          <article v-for="product in products" :key="product.productType + '-' + product.id" class="product-card">
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

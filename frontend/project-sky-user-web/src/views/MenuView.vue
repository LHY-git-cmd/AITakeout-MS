<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Clock3, LoaderCircle, MapPin, PackageOpen, Plus, RefreshCw, Store, UtensilsCrossed } from '@lucide/vue'
import CartPanel from '@/components/CartPanel.vue'
import ProductDialog from '@/components/ProductDialog.vue'
import ProductImage from '@/components/ProductImage.vue'
import { getCategories, getProducts, getShopStatus, type Category, type MenuProduct } from '@/api/menu'
import { useCartStore } from '@/stores/cart'

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

const activeCategory = computed(() => categories.value.find((item) => item.id === activeCategoryId.value) ?? null)
const products = computed(() => activeCategoryId.value ? productCache.value[activeCategoryId.value] ?? [] : [])
const isOpen = computed(() => shopStatus.value === 1)

function productCount(product: MenuProduct) {
  return cartStore.items
    .filter((item) => product.productType === 'dish' ? item.dishId === product.id : item.setmealId === product.id)
    .reduce((total, item) => total + item.number, 0)
}

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
    <div class="shop-summary">
      <div>
        <div :class="['shop-summary__status', { 'is-closed': !isOpen }]">
          <span /> {{ isOpen ? '营业中' : '已打烊' }}
        </div>
        <h1>今天想吃点什么？</h1>
        <div class="shop-summary__meta">
          <span><Clock3 :size="16" /> 约 30 分钟送达</span>
          <span><MapPin :size="16" /> 配送范围内起送</span>
        </div>
      </div>
      <div class="shop-summary__visual" aria-label="配送服务信息">
        <div class="shop-summary__visual-icon"><UtensilsCrossed :size="28" /></div>
        <div>
          <strong>一口热饭，准时到家</strong>
          <span>现点现做 · 新鲜配送</span>
        </div>
        <b>30<span> min</span></b>
      </div>
    </div>

    <div v-if="error && !categories.length" class="menu-state menu-state--error">
      <RefreshCw :size="30" />
      <strong>{{ error }}</strong>
      <button type="button" @click="loadMenu">重新加载</button>
    </div>

    <div v-else class="menu-workspace">
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
        <div class="product-area__heading">
          <div>
            <h2>{{ activeCategory?.name || '菜单' }}</h2>
            <span>{{ products.length }} 件商品</span>
          </div>
          <p v-if="error">{{ error }}</p>
        </div>

        <div v-if="loadingProducts" class="product-loading">
          <LoaderCircle class="spin" :size="24" /> 正在加载商品
        </div>
        <div v-else-if="!products.length" class="menu-state">
          <PackageOpen :size="34" />
          <strong>该分类暂无可售商品</strong>
        </div>
        <div v-else class="product-grid">
          <article v-for="product in products" :key="`${product.productType}-${product.id}`" class="product-card">
            <button class="product-card__image" type="button" :aria-label="`查看${product.name}`" @click="selectedProduct = product">
              <ProductImage :src="product.image" :alt="product.name" />
            </button>
            <div class="product-card__body">
              <h3>{{ product.name }}</h3>
              <p>{{ product.description || (product.productType === 'setmeal' ? '搭配丰富，优惠组合' : '新鲜制作，欢迎品尝') }}</p>
              <div class="product-card__footer">
                <strong><small>¥</small>{{ Number(product.price).toFixed(2) }}</strong>
                <span v-if="productCount(product)" class="product-card__count">{{ productCount(product) }}</span>
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

      <aside class="desktop-cart-panel">
        <div class="desktop-cart-panel__title"><Store :size="20" /> 已选商品</div>
        <CartPanel compact />
      </aside>
    </div>

    <div v-if="!isOpen && categories.length" class="closed-shop-banner">门店已打烊，暂时无法加购</div>
    <ProductDialog :product="selectedProduct" :disabled="!isOpen" @close="selectedProduct = null" />
  </section>
</template>

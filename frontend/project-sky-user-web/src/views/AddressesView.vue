<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { Check, Edit3, MapPin, Plus, Trash2 } from '@lucide/vue'
import PageScaffold from '@/components/PageScaffold.vue'
import AddressDialog from '@/components/AddressDialog.vue'
import { createAddress, deleteAddress, fullAddress, getAddresses, setDefaultAddress, updateAddress, type Address, type AddressPayload } from '@/api/address'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

const authStore = useAuthStore()
const uiStore = useUiStore()
const addresses = ref<Address[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const dialogOpen = ref(false)
const editing = ref<Address | null>(null)

async function loadAddresses() {
  if (!authStore.isAuthenticated) return
  loading.value = true
  error.value = ''
  try {
    addresses.value = await getAddresses() ?? []
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '地址加载失败'
  } finally {
    loading.value = false
  }
}

function openForm(address: Address | null = null) {
  editing.value = address
  dialogOpen.value = true
  error.value = ''
}

async function save(payload: AddressPayload) {
  saving.value = true
  error.value = ''
  const wasEmpty = addresses.value.length === 0
  try {
    if (payload.id) await updateAddress(payload)
    else await createAddress(payload)
    dialogOpen.value = false
    await loadAddresses()
    if (wasEmpty && addresses.value[0]) {
      await setDefaultAddress(addresses.value[0].id)
      await loadAddresses()
    }
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '地址保存失败'
  } finally {
    saving.value = false
  }
}

async function setDefault(address: Address) {
  error.value = ''
  try {
    await setDefaultAddress(address.id)
    await loadAddresses()
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '默认地址设置失败'
  }
}

async function remove(address: Address) {
  if (!window.confirm(`确定删除“${fullAddress(address)}”吗？`)) return
  error.value = ''
  try {
    await deleteAddress(address.id)
    await loadAddresses()
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '地址删除失败'
  }
}

onMounted(() => {
  if (authStore.isAuthenticated) void loadAddresses()
  else uiStore.openLogin()
})

watch(() => authStore.isAuthenticated, (authenticated) => {
  if (authenticated) void loadAddresses()
})
</script>

<template>
  <PageScaffold title="收货地址" description="管理配送联系人和地址">
    <template #action>
      <button v-if="authStore.isAuthenticated" class="primary-action" type="button" @click="openForm()"><Plus :size="18" /> 新增地址</button>
    </template>

    <div v-if="!authStore.isAuthenticated" class="content-empty">
      <MapPin :size="34" /><strong>登录后管理收货地址</strong>
      <button type="button" @click="uiStore.openLogin">登录</button>
    </div>
    <div v-else-if="loading" class="content-empty">正在加载地址...</div>
    <div v-else-if="!addresses.length" class="content-empty">
      <MapPin :size="34" /><strong>还没有收货地址</strong>
      <button type="button" @click="openForm()">新增地址</button>
    </div>
    <div v-else class="address-list">
      <article v-for="address in addresses" :key="address.id" class="address-card">
        <div class="address-card__main">
          <div class="address-card__contact">
            <strong>{{ address.consignee }} {{ address.sex === '1' ? '先生' : '女士' }}</strong>
            <span>{{ address.phone }}</span>
            <b v-if="address.label">{{ address.label }}</b>
            <b v-if="address.isDefault" class="is-default"><Check :size="13" /> 默认</b>
          </div>
          <p>{{ fullAddress(address) }}</p>
        </div>
        <div class="address-card__actions">
          <button v-if="!address.isDefault" type="button" @click="setDefault(address)">设为默认</button>
          <button type="button" aria-label="编辑地址" title="编辑" @click="openForm(address)"><Edit3 :size="18" /></button>
          <button type="button" aria-label="删除地址" title="删除" @click="remove(address)"><Trash2 :size="18" /></button>
        </div>
      </article>
    </div>
    <p v-if="error && !dialogOpen" class="page-error" role="alert">{{ error }}</p>
    <AddressDialog :open="dialogOpen" :address="editing" :saving="saving" :error="error" @close="dialogOpen = false" @save="save" />
  </PageScaffold>
</template>

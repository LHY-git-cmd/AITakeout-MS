<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { Camera, Check, LoaderCircle, UserRound } from '@lucide/vue'
import { useRouter } from 'vue-router'
import PageScaffold from '@/components/PageScaffold.vue'
import { ApiError } from '@/api/http'
import { updateProfile, uploadAvatar } from '@/api/profile'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

const authStore = useAuthStore()
const uiStore = useUiStore()
const router = useRouter()
const name = ref(authStore.user?.name || '')
const avatarFile = ref<File | null>(null)
const previewUrl = ref('')
const savingName = ref(false)
const savingAvatar = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const avatarSource = computed(() => previewUrl.value || authStore.user?.avatar || '')

function requireLogin() {
  if (authStore.isAuthenticated) return true
  uiStore.openLogin()
  void router.replace('/profile')
  return false
}

function selectAvatar(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  errorMessage.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    errorMessage.value = '请选择 JPEG、PNG 或 WebP 图片'
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    errorMessage.value = '头像不能超过 2 MiB'
    return
  }
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  avatarFile.value = file
  previewUrl.value = URL.createObjectURL(file)
}

async function saveName() {
  if (!requireLogin()) return
  const value = name.value.trim()
  if (!value) {
    errorMessage.value = '昵称不能为空'
    return
  }
  savingName.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    authStore.updateUser(await updateProfile(value))
    successMessage.value = '昵称已更新'
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '昵称保存失败，请稍后重试'
  } finally {
    savingName.value = false
  }
}

async function saveAvatar() {
  if (!requireLogin() || !avatarFile.value) return
  savingAvatar.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    authStore.updateUser(await uploadAvatar(avatarFile.value))
    avatarFile.value = null
    successMessage.value = '头像已更新'
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '头像上传失败，请稍后重试'
  } finally {
    savingAvatar.value = false
  }
}

onBeforeUnmount(() => {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})
</script>

<template>
  <PageScaffold title="编辑个人资料">
    <section v-if="authStore.isAuthenticated" class="settings-card" aria-labelledby="profile-edit-title">
      <header>
        <UserRound :size="22" aria-hidden="true" />
        <div><h2 id="profile-edit-title">公开资料</h2><p>更新点餐账户显示的昵称与头像</p></div>
      </header>

      <div class="avatar-editor">
        <div class="avatar-editor__preview">
          <img v-if="avatarSource" :src="avatarSource" alt="当前头像预览" />
          <UserRound v-else :size="36" aria-hidden="true" />
        </div>
        <div>
          <label class="file-picker" for="profile-avatar"><Camera :size="18" aria-hidden="true" />选择图片</label>
          <input id="profile-avatar" class="sr-only" type="file" accept="image/jpeg,image/png,image/webp" @change="selectAvatar" />
          <small>JPEG、PNG 或 WebP，最大 2 MiB</small>
        </div>
        <button type="button" :disabled="!avatarFile || savingAvatar" @click="saveAvatar">
          <LoaderCircle v-if="savingAvatar" class="spin" :size="18" aria-hidden="true" />
          <Check v-else :size="18" aria-hidden="true" />保存头像
        </button>
      </div>

      <form class="settings-form" @submit.prevent="saveName">
        <label for="profile-name">昵称</label>
        <input id="profile-name" v-model="name" type="text" maxlength="32" autocomplete="name" />
        <button type="submit" :disabled="savingName || !name.trim()">
          <LoaderCircle v-if="savingName" class="spin" :size="18" aria-hidden="true" />
          <Check v-else :size="18" aria-hidden="true" />保存昵称
        </button>
      </form>
      <p v-if="errorMessage" class="settings-message is-error" role="alert">{{ errorMessage }}</p>
      <p v-if="successMessage" class="settings-message is-success" role="status">{{ successMessage }}</p>
    </section>
    <section v-else class="settings-empty">
      <UserRound :size="32" aria-hidden="true" /><p>登录后才能编辑个人资料</p>
      <button type="button" @click="uiStore.openLogin">立即登录</button>
    </section>
  </PageScaffold>
</template>

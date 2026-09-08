<script setup lang="ts">
import { computed, ref } from 'vue'
import { LoaderCircle, LogIn, UserPlus, X } from '@lucide/vue'
import { ApiError } from '@/api/http'
import type { RegisterPayload } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

type AuthMode = 'login' | 'register'

const authStore = useAuthStore()
const uiStore = useUiStore()
const mode = ref<AuthMode>('login')
const phone = ref('13800138000')
const password = ref('123456')
const registration = ref<RegisterPayload>({ name: '', phone: '', sex: '', idNumber: '', avatar: '' })
const submitting = ref(false)
const errorMessage = ref('')

const validPhone = (value: string) => /^1[3-9]\d{9}$/.test(value)
const canSubmit = computed(() => mode.value === 'login'
  ? validPhone(phone.value) && password.value.length > 0
  : registration.value.name.trim().length > 0 && validPhone(registration.value.phone))

function selectMode(value: AuthMode) {
  mode.value = value
  errorMessage.value = ''
}

async function submit() {
  errorMessage.value = ''
  if (!canSubmit.value) {
    errorMessage.value = mode.value === 'login' ? '请输入正确的手机号和密码' : '请填写姓名和正确的手机号'
    return
  }
  submitting.value = true
  try {
    if (mode.value === 'login') {
      await authStore.login(phone.value, password.value)
    } else {
      await authStore.register({ ...registration.value, name: registration.value.name.trim() })
    }
    uiStore.closeLogin()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : `${mode.value === 'login' ? '登录' : '注册'}失败，请稍后重试`
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Transition name="dialog">
    <div v-if="uiStore.loginOpen" class="dialog-layer" @click.self="uiStore.closeLogin">
      <section class="login-dialog" :class="{ 'login-dialog--register': mode === 'register' }" role="dialog" aria-modal="true" aria-labelledby="login-dialog-title">
        <button class="login-dialog__close" type="button" aria-label="关闭" title="关闭" @click="uiStore.closeLogin">
          <X :size="20" aria-hidden="true" />
        </button>
        <div class="login-dialog__icon">
          <LogIn v-if="mode === 'login'" :size="24" aria-hidden="true" />
          <UserPlus v-else :size="24" aria-hidden="true" />
        </div>
        <h2 id="login-dialog-title">{{ mode === 'login' ? '登录苍穹外卖' : '注册新用户' }}</h2>
        <p>{{ mode === 'login' ? '使用手机号和密码继续点餐' : '注册后将使用默认密码 123456 自动登录' }}</p>

        <div class="auth-mode-switch" role="tablist" aria-label="账号入口">
          <button type="button" role="tab" :aria-selected="mode === 'login'" :class="{ 'is-active': mode === 'login' }" @click="selectMode('login')">
            <LogIn :size="17" aria-hidden="true" />登录
          </button>
          <button type="button" role="tab" :aria-selected="mode === 'register'" :class="{ 'is-active': mode === 'register' }" @click="selectMode('register')">
            <UserPlus :size="17" aria-hidden="true" />注册
          </button>
        </div>

        <form @submit.prevent="submit">
          <template v-if="mode === 'login'">
            <label for="login-phone">手机号</label>
            <input id="login-phone" v-model.trim="phone" type="tel" inputmode="numeric" maxlength="11" autocomplete="username" />
            <label for="login-password">密码</label>
            <input id="login-password" v-model="password" type="password" autocomplete="current-password" />
          </template>

          <div v-else class="register-grid">
            <div class="register-field">
              <label for="register-name">姓名</label>
              <input id="register-name" v-model.trim="registration.name" type="text" maxlength="32" autocomplete="name" />
            </div>
            <div class="register-field">
              <label for="register-phone">手机号</label>
              <input id="register-phone" v-model.trim="registration.phone" type="tel" inputmode="numeric" maxlength="11" autocomplete="tel" />
            </div>
            <fieldset class="register-field register-field--full sex-control">
              <legend>性别 <span>选填</span></legend>
              <div>
                <button type="button" :class="{ 'is-active': registration.sex === '' }" @click="registration.sex = ''">未设置</button>
                <button type="button" :class="{ 'is-active': registration.sex === '1' }" @click="registration.sex = '1'">男</button>
                <button type="button" :class="{ 'is-active': registration.sex === '0' }" @click="registration.sex = '0'">女</button>
              </div>
            </fieldset>
            <div class="register-field register-field--full">
              <label for="register-id-number">身份证号 <span>选填</span></label>
              <input id="register-id-number" v-model.trim="registration.idNumber" type="text" maxlength="18" autocomplete="off" />
            </div>
            <div class="register-field register-field--full">
              <label for="register-avatar">头像地址 <span>选填</span></label>
              <input id="register-avatar" v-model.trim="registration.avatar" type="url" maxlength="500" autocomplete="url" placeholder="https://example.com/avatar.jpg" />
            </div>
          </div>

          <p v-if="errorMessage" class="login-dialog__error" role="alert">{{ errorMessage }}</p>
          <button class="login-dialog__submit" type="submit" :disabled="submitting || !canSubmit">
            <LoaderCircle v-if="submitting" class="spin" :size="19" aria-hidden="true" />
            <LogIn v-else-if="mode === 'login'" :size="19" aria-hidden="true" />
            <UserPlus v-else :size="19" aria-hidden="true" />
            {{ submitting ? (mode === 'login' ? '登录中' : '注册中') : (mode === 'login' ? '登录' : '注册并登录') }}
          </button>
        </form>
      </section>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { LoaderCircle, LogIn, MessageSquareText, UserPlus, X } from '@lucide/vue'
import { sendRegistrationCode, type RegisterPayload } from '@/api/auth'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

type AuthMode = 'login' | 'register'

const authStore = useAuthStore()
const uiStore = useUiStore()
const mode = ref<AuthMode>('login')
const phone = ref('')
const password = ref('')
const registration = ref<RegisterPayload>({ name: '', phone: '', code: '', password: '' })
const confirmPassword = ref('')
const submitting = ref(false)
const sendingCode = ref(false)
const cooldown = ref(0)
const errorMessage = ref('')
const devSmsHint = import.meta.env.DEV
let cooldownTimer: number | undefined

const validPhone = (value: string) => /^1[3-9]\d{9}$/.test(value)
const canSubmit = computed(() => mode.value === 'login'
  ? validPhone(phone.value) && password.value.length > 0
  : registration.value.name.trim().length > 0
    && validPhone(registration.value.phone)
    && /^\d{6}$/.test(registration.value.code)
    && registration.value.password.length >= 8
    && registration.value.password.length <= 72
    && registration.value.password === confirmPassword.value)

function selectMode(value: AuthMode) {
  mode.value = value
  errorMessage.value = ''
}

function startCooldown() {
  cooldown.value = 60
  if (cooldownTimer !== undefined) window.clearInterval(cooldownTimer)
  cooldownTimer = window.setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0 && cooldownTimer !== undefined) {
      window.clearInterval(cooldownTimer)
      cooldownTimer = undefined
    }
  }, 1_000)
}

async function sendCode() {
  errorMessage.value = ''
  if (!validPhone(registration.value.phone)) {
    errorMessage.value = '请先填写正确的手机号'
    return
  }
  sendingCode.value = true
  try {
    await sendRegistrationCode(registration.value.phone)
    startCooldown()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '验证码发送失败，请稍后重试'
  } finally {
    sendingCode.value = false
  }
}

async function submit() {
  errorMessage.value = ''
  if (!canSubmit.value) {
    errorMessage.value = mode.value === 'login'
      ? '请输入正确的手机号和密码'
      : '请完整填写注册信息，密码至少 8 位且两次输入一致'
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

onBeforeUnmount(() => {
  if (cooldownTimer !== undefined) window.clearInterval(cooldownTimer)
})
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
        <h2 id="login-dialog-title">{{ mode === 'login' ? '登录苍穹外卖' : '注册正式账号' }}</h2>
        <p>{{ mode === 'login' ? '使用手机号和密码继续点餐' : '验证码确认手机号，由你设置安全密码' }}</p>

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
            <div class="register-field register-field--full">
              <label for="register-name">姓名</label>
              <input id="register-name" v-model.trim="registration.name" type="text" maxlength="32" autocomplete="name" />
            </div>
            <div class="register-field register-field--full">
              <label for="register-phone">手机号</label>
              <input id="register-phone" v-model.trim="registration.phone" type="tel" inputmode="numeric" maxlength="11" autocomplete="tel" />
            </div>
            <div class="register-field register-field--full">
              <label for="register-code">短信验证码</label>
              <div class="verification-field">
                <input id="register-code" v-model.trim="registration.code" type="text" inputmode="numeric" maxlength="6" autocomplete="one-time-code" />
                <button type="button" :disabled="sendingCode || cooldown > 0" @click="sendCode">
                  <LoaderCircle v-if="sendingCode" class="spin" :size="16" aria-hidden="true" />
                  <MessageSquareText v-else :size="16" aria-hidden="true" />
                  {{ cooldown > 0 ? `${cooldown} 秒后重发` : '获取验证码' }}
                </button>
              </div>
              <small v-if="devSmsHint" class="verification-hint">开发环境模拟验证码：246810</small>
            </div>
            <div class="register-field">
              <label for="register-password">设置密码</label>
              <input id="register-password" v-model="registration.password" type="password" minlength="8" maxlength="72" autocomplete="new-password" />
            </div>
            <div class="register-field">
              <label for="register-password-confirm">确认密码</label>
              <input id="register-password-confirm" v-model="confirmPassword" type="password" minlength="8" maxlength="72" autocomplete="new-password" />
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

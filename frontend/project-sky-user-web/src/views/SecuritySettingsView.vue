<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { KeyRound, LoaderCircle, MessageSquareText, ShieldCheck, Smartphone } from '@lucide/vue'
import { useRouter } from 'vue-router'
import PageScaffold from '@/components/PageScaffold.vue'
import { ApiError } from '@/api/http'
import { changePassword, changePhone, sendSecurityCode } from '@/api/profile'
import { useAccountStore } from '@/stores/account'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

type CodeTarget = 'old' | 'new' | 'password'
const authStore = useAuthStore()
const accountStore = useAccountStore()
const uiStore = useUiStore()
const router = useRouter()
const phoneForm = reactive({ oldPhoneCode: '', newPhone: '', newPhoneCode: '' })
const passwordForm = reactive({ mode: 'password' as 'password' | 'sms', currentPassword: '', code: '', newPassword: '', confirmPassword: '' })
const cooldown = reactive<Record<CodeTarget, number>>({ old: 0, new: 0, password: 0 })
const sending = ref<CodeTarget | null>(null)
const submitting = ref<'phone' | 'password' | null>(null)
const errorMessage = ref('')
const timers = new Map<CodeTarget, number>()
const validNewPhone = computed(() => /^1[3-9]\d{9}$/.test(phoneForm.newPhone))
const currentPhone = computed(() => authStore.user?.phone || '')

function startCooldown(target: CodeTarget) {
  cooldown[target] = 60
  const old = timers.get(target)
  if (old) window.clearInterval(old)
  timers.set(target, window.setInterval(() => {
    cooldown[target] -= 1
    if (cooldown[target] <= 0) {
      window.clearInterval(timers.get(target))
      timers.delete(target)
    }
  }, 1_000))
}

async function sendCode(target: CodeTarget) {
  const phone = target === 'new' ? phoneForm.newPhone : currentPhone.value
  if (!/^1[3-9]\d{9}$/.test(phone)) {
    errorMessage.value = target === 'new' ? '请先填写正确的新手机号' : '当前账号未绑定有效手机号'
    return
  }
  const purpose = target === 'old' ? 'change_old_phone' : target === 'new' ? 'change_new_phone' : 'change_password'
  sending.value = target
  errorMessage.value = ''
  try {
    await sendSecurityCode(phone, purpose)
    startCooldown(target)
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '验证码发送失败，请稍后重试'
  } finally {
    sending.value = null
  }
}

function finishSensitiveChange() {
  authStore.clearSession()
  accountStore.reset()
  uiStore.openLogin()
  void router.replace('/profile')
}

async function submitPhone() {
  errorMessage.value = ''
  if (!/^\d{6}$/.test(phoneForm.oldPhoneCode) || !validNewPhone.value || !/^\d{6}$/.test(phoneForm.newPhoneCode)) {
    errorMessage.value = '请完整填写原手机号验证码、新手机号和新手机号验证码'
    return
  }
  submitting.value = 'phone'
  try {
    await changePhone({ ...phoneForm })
    finishSensitiveChange()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '手机号换绑失败，请稍后重试'
  } finally {
    submitting.value = null
  }
}

async function submitPassword() {
  errorMessage.value = ''
  if (passwordForm.newPassword.length < 8 || passwordForm.newPassword !== passwordForm.confirmPassword) {
    errorMessage.value = '新密码至少 8 位，且两次输入必须一致'
    return
  }
  if (passwordForm.mode === 'password' && !passwordForm.currentPassword
    || passwordForm.mode === 'sms' && !/^\d{6}$/.test(passwordForm.code)) {
    errorMessage.value = passwordForm.mode === 'password' ? '请输入当前密码' : '请输入 6 位短信验证码'
    return
  }
  submitting.value = 'password'
  try {
    await changePassword({
      currentPassword: passwordForm.mode === 'password' ? passwordForm.currentPassword : undefined,
      code: passwordForm.mode === 'sms' ? passwordForm.code : undefined,
      newPassword: passwordForm.newPassword,
    })
    finishSensitiveChange()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '密码修改失败，请稍后重试'
  } finally {
    submitting.value = null
  }
}

onBeforeUnmount(() => timers.forEach((timer) => window.clearInterval(timer)))
</script>

<template>
  <PageScaffold title="账号与安全">
    <p v-if="errorMessage" class="settings-message is-error" role="alert">{{ errorMessage }}</p>
    <div v-if="authStore.isAuthenticated" class="security-grid">
      <section class="settings-card" aria-labelledby="phone-change-title">
        <header><Smartphone :size="22" aria-hidden="true" /><div><h2 id="phone-change-title">换绑手机号</h2><p>原手机号与新手机号均需验证</p></div></header>
        <form class="settings-form" @submit.prevent="submitPhone">
          <label for="old-phone-code">原手机号 {{ currentPhone }}</label>
          <div class="verification-field">
            <input id="old-phone-code" v-model.trim="phoneForm.oldPhoneCode" inputmode="numeric" maxlength="6" autocomplete="one-time-code" />
            <button type="button" :disabled="sending === 'old' || cooldown.old > 0" @click="sendCode('old')"><MessageSquareText :size="16" aria-hidden="true" />{{ cooldown.old ? `${cooldown.old} 秒` : '获取验证码' }}</button>
          </div>
          <label for="new-phone">新手机号</label>
          <input id="new-phone" v-model.trim="phoneForm.newPhone" type="tel" inputmode="numeric" maxlength="11" autocomplete="tel" />
          <label for="new-phone-code">新手机号验证码</label>
          <div class="verification-field">
            <input id="new-phone-code" v-model.trim="phoneForm.newPhoneCode" inputmode="numeric" maxlength="6" autocomplete="one-time-code" />
            <button type="button" :disabled="sending === 'new' || cooldown.new > 0 || !validNewPhone" @click="sendCode('new')"><MessageSquareText :size="16" aria-hidden="true" />{{ cooldown.new ? `${cooldown.new} 秒` : '获取验证码' }}</button>
          </div>
          <button class="settings-submit" type="submit" :disabled="submitting === 'phone'"><LoaderCircle v-if="submitting === 'phone'" class="spin" :size="18" aria-hidden="true" /><ShieldCheck v-else :size="18" aria-hidden="true" />确认换绑</button>
        </form>
      </section>

      <section class="settings-card" aria-labelledby="password-change-title">
        <header><KeyRound :size="22" aria-hidden="true" /><div><h2 id="password-change-title">修改密码</h2><p>可通过当前密码或短信验证码验证身份</p></div></header>
        <div class="security-mode" role="tablist" aria-label="身份验证方式">
          <button type="button" role="tab" :aria-selected="passwordForm.mode === 'password'" :class="{ 'is-active': passwordForm.mode === 'password' }" @click="passwordForm.mode = 'password'">当前密码</button>
          <button type="button" role="tab" :aria-selected="passwordForm.mode === 'sms'" :class="{ 'is-active': passwordForm.mode === 'sms' }" @click="passwordForm.mode = 'sms'">短信验证</button>
        </div>
        <form class="settings-form" @submit.prevent="submitPassword">
          <template v-if="passwordForm.mode === 'password'">
            <label for="current-password">当前密码</label>
            <input id="current-password" v-model="passwordForm.currentPassword" type="password" autocomplete="current-password" />
          </template>
          <template v-else>
            <label for="password-code">手机号 {{ currentPhone }} 的验证码</label>
            <div class="verification-field">
              <input id="password-code" v-model.trim="passwordForm.code" inputmode="numeric" maxlength="6" autocomplete="one-time-code" />
              <button type="button" :disabled="sending === 'password' || cooldown.password > 0" @click="sendCode('password')"><MessageSquareText :size="16" aria-hidden="true" />{{ cooldown.password ? `${cooldown.password} 秒` : '获取验证码' }}</button>
            </div>
          </template>
          <label for="new-password">新密码</label>
          <input id="new-password" v-model="passwordForm.newPassword" type="password" minlength="8" maxlength="72" autocomplete="new-password" />
          <label for="confirm-password">确认新密码</label>
          <input id="confirm-password" v-model="passwordForm.confirmPassword" type="password" minlength="8" maxlength="72" autocomplete="new-password" />
          <button class="settings-submit" type="submit" :disabled="submitting === 'password'"><LoaderCircle v-if="submitting === 'password'" class="spin" :size="18" aria-hidden="true" /><ShieldCheck v-else :size="18" aria-hidden="true" />确认修改</button>
        </form>
      </section>
      <p class="security-warning"><ShieldCheck :size="18" aria-hidden="true" />手机号或密码修改成功后，全部设备都会退出登录。</p>
    </div>
    <section v-else class="settings-empty"><ShieldCheck :size="32" aria-hidden="true" /><p>登录后才能管理账号安全</p><button type="button" @click="uiStore.openLogin">立即登录</button></section>
  </PageScaffold>
</template>

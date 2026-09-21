import { request } from './http'
import type { UserProfile } from './auth'

export interface PhoneChangePayload {
  oldPhoneCode: string
  newPhone: string
  newPhoneCode: string
}

export interface PasswordChangePayload {
  currentPassword?: string
  code?: string
  newPassword: string
}

export function updateProfile(name: string) {
  return request<UserProfile>({ url: '/user/user/profile', method: 'PUT', data: { name } })
}

export function uploadAvatar(file: File) {
  const data = new FormData()
  data.append('file', file)
  return request<UserProfile>({
    url: '/user/user/avatar',
    method: 'POST',
    data,
    // 清除 http.ts 的 JSON 默认头，让浏览器自动补 multipart boundary。
    headers: { 'Content-Type': undefined },
  })
}

export function sendSecurityCode(phone: string, purpose: 'change_old_phone' | 'change_new_phone' | 'change_password') {
  return request<void>({ url: '/user/auth/sms/send', method: 'POST', data: { phone, purpose } })
}

export function changePhone(data: PhoneChangePayload) {
  return request<void>({ url: '/user/user/phone', method: 'PUT', data })
}

export function changePassword(data: PasswordChangePayload) {
  return request<void>({ url: '/user/user/password', method: 'PUT', data })
}
